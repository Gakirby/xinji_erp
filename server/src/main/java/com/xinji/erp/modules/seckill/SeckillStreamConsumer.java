package com.xinji.erp.modules.seckill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息消费者：接口抢到资格后立刻返回令牌号，真正的落库交给这里异步完成。
 *
 * 消费语义是「手动 ACK」：
 *   - 处理成功 → ACK，消息出队；
 *   - 处理失败 → 不 ACK，消息留在 pending-list，由补偿任务重新认领。
 *
 * 可靠性设计（三条腿，缺一条都会出现「抢到了但一直不发券」）：
 *   1. 监听容器常驻消费；
 *   2. 消费者组存活检查 —— 组存在 Redis 里，被清库/迁移/淘汰后会消失，
 *      容器还在跑却再也读不到消息，所以每 30 秒探一次，发现 NOGROUP 立即重建；
 *   3. 兜底增量拉取 —— 容器本身异常退出时（日志里只有一句 Unexpected error），
 *      定时用 XREADGROUP 的 ">" 语义把未投递消息拉出来处理，与容器不重复消费。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStreamConsumer implements ApplicationRunner {

    private final StringRedisTemplate redis;
    private final TokenIssuer tokenIssuer;

    @Value("${xinji.seckill.stream-key:sk:stream:token}")
    private String streamKey;

    @Value("${xinji.seckill.group:sk-token-group}")
    private String group;

    @Value("${xinji.seckill.consumer:consumer-1}")
    private String consumerName;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private volatile boolean running = false;

    @Override
    public void run(ApplicationArguments args) {
        try {
            createGroupIfAbsent(ReadOffset.latest());
            startContainer();
            running = true;
            log.info("秒杀消费者组已启动: stream={} group={} consumer={}", streamKey, group, consumerName);
        } catch (Exception e) {
            log.error("秒杀 Stream 消费者启动失败（请确认 Redis 已启动）: {}", e.getMessage());
        }
    }

    private void createGroupIfAbsent(ReadOffset offset) {
        try {
            redis.opsForStream().createGroup(streamKey, offset, group);
            log.info("已创建消费者组: {} offset={}", group, offset);
        } catch (Exception e) {
            log.info("消费者组已存在，跳过创建: {}", group);
        }
    }

    private void startContainer() {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .build();
        container = StreamMessageListenerContainer.create(redis.getConnectionFactory(), options);

        StreamMessageListenerContainer.StreamReadRequest<String> request =
                StreamMessageListenerContainer.StreamReadRequest
                        .builder(StreamOffset.create(streamKey, ReadOffset.lastConsumed()))
                        .consumer(Consumer.from(group, consumerName))
                        .autoAck(false)
                        .build();

        StreamListener<String, MapRecord<String, String, String>> listener =
                record -> handleBody(new HashMap<Object, Object>(record.getValue()), record.getId());
        container.register(request, listener);
        container.start();
    }

    /** 处理一条消息：成功才 ACK，失败留在 pending-list */
    private boolean handleBody(Map<Object, Object> body, RecordId id) {
        boolean ok = tokenIssuer.issue(body);
        if (ok) {
            Long acked = redis.opsForStream().acknowledge(streamKey, group, id);
            log.debug("消息已 ACK: {} -> {}", id, acked);
        } else {
            log.warn("消息处理失败，留在 pending-list 等待补偿: {}", id);
        }
        return ok;
    }

    /**
     * 消费者组存活检查。
     * 组没了就重建，重建后从 0 开始读 —— 重放依赖 TokenIssuer 的唯一索引幂等，不会重复发券。
     */
    @Scheduled(fixedDelay = 10_000L, initialDelay = 10_000L)
    public void healthCheck() {
        if (!running) {
            run(null);
            return;
        }
        try {
            redis.opsForStream().pending(streamKey, group, Range.unbounded(), 1);
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (isGroupMissing(msg)) {
                log.warn("消费者组丢失，重建并从 0 重放: {}", group);
                createGroupIfAbsent(ReadOffset.from("0"));
            }
        }
    }

    /**
     * 兜底拉取：容器正常时这里读不到东西（">" 只返回未投递消息，不会和容器抢同一条）；
     * 容器挂了、或消费者组刚被重建时，这就是令牌发放的通道。
     */
    @Scheduled(fixedDelay = 3_000L, initialDelay = 5_000L)
    public void drainFallback() {
        if (!running) {
            return;
        }
        boolean groupRecreated = false;
        try {
            for (int round = 0; round < 5; round++) {
                List<MapRecord<String, Object, Object>> records;
                try {
                    records = redis.opsForStream().read(
                            Consumer.from(group, consumerName),
                            StreamReadOptions.empty().count(50),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
                } catch (Exception e) {
                    // 组没了：建组后立刻重试，别等下一个调度周期
                    if (!groupRecreated && isGroupMissing(String.valueOf(e.getMessage()))) {
                        createGroupIfAbsent(ReadOffset.from("0"));
                        groupRecreated = true;
                        continue;
                    }
                    throw e;
                }
                if (records == null || records.isEmpty()) {
                    return;
                }
                for (MapRecord<String, Object, Object> rec : records) {
                    handleBody(new HashMap<Object, Object>(rec.getValue()), rec.getId());
                }
            }
        } catch (Exception e) {
            log.debug("兜底拉取跳过: {}", e.getMessage());
        }
    }

    private boolean isGroupMissing(String msg) {
        return msg.contains("NOGROUP") || msg.contains("no such key") || msg.contains("NO_GROUP");
    }

    /**
     * pending-list 补偿：把超过 20 秒仍未 ACK 的消息重新认领回来处理，
     * 覆盖服务重启、消费者宕机、瞬时异常等情况。
     */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 45_000L)
    public void compensatePending() {
        if (!running) {
            return;
        }
        try {
            PendingMessages pending = redis.opsForStream().pending(
                    streamKey, group, Range.unbounded(), 20);
            if (pending == null || pending.isEmpty()) {
                return;
            }
            int retried = 0;
            for (PendingMessage pm : pending) {
                if (pm.getElapsedTimeSinceLastDelivery().getSeconds() < 20) {
                    continue;
                }
                RecordId id = pm.getId();
                List<MapRecord<String, Object, Object>> claimed = redis.opsForStream()
                        .claim(streamKey, group, consumerName, Duration.ZERO, id);
                if (claimed != null) {
                    for (MapRecord<String, Object, Object> rec : claimed) {
                        handleBody(new HashMap<Object, Object>(rec.getValue()), rec.getId());
                        retried++;
                    }
                }
            }
            if (retried > 0) {
                log.info("pending-list 补偿处理 {} 条", retried);
            }
        } catch (Exception e) {
            log.warn("pending-list 补偿失败: {}", e.getMessage());
        }
    }

    /** 供后台监控页展示 pending 数量 */
    public long pendingCount() {
        try {
            PendingMessages pending = redis.opsForStream().pending(
                    streamKey, group, Range.unbounded(), 100);
            return pending == null ? 0 : pending.size();
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean isRunning() {
        return running;
    }
}
