package com.xinji.erp.modules.seckill;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinji.erp.common.PageResult;
import com.xinji.erp.common.Result;
import com.xinji.erp.modules.seckill.dto.ActivityRequest;
import com.xinji.erp.modules.seckill.dto.SeckillSkuRequest;
import com.xinji.erp.modules.seckill.entity.SeckillActivity;
import com.xinji.erp.modules.seckill.entity.SeckillSku;
import com.xinji.erp.modules.seckill.entity.SeckillToken;
import com.xinji.erp.modules.seckill.mapper.SeckillTokenMapper;
import com.xinji.erp.modules.seckill.vo.SeckillSkuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "13 后台 · 秒杀管理")
@RestController
@RequestMapping("/api/admin/seckill")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillService seckillService;
    private final SeckillAdminService seckillAdminService;
    private final SeckillTokenMapper tokenMapper;
    private final SeckillStreamConsumer consumer;

    @Operation(summary = "活动列表")
    @GetMapping("/activities")
    public Result<List<SeckillActivity>> activities() {
        return Result.ok(seckillService.activities());
    }

    @Operation(summary = "新建活动")
    @PostMapping("/activities")
    public Result<SeckillActivity> createActivity(@Validated @RequestBody ActivityRequest req) {
        return Result.ok(seckillAdminService.save(null, req));
    }

    @Operation(summary = "修改活动（已开始的活动不能改时间）")
    @PutMapping("/activities/{id}")
    public Result<SeckillActivity> updateActivity(@PathVariable Long id,
                                                  @Validated @RequestBody ActivityRequest req) {
        return Result.ok(seckillAdminService.save(id, req));
    }

    @Operation(summary = "发布活动")
    @PostMapping("/activities/{id}/publish")
    public Result<SeckillActivity> publish(@PathVariable Long id) {
        return Result.ok(seckillAdminService.publish(id));
    }

    @Operation(summary = "开始活动")
    @PostMapping("/activities/{id}/start")
    public Result<SeckillActivity> start(@PathVariable Long id) {
        return Result.ok(seckillAdminService.start(id));
    }

    @Operation(summary = "结束活动（未售库存回拨销售池）")
    @PostMapping("/activities/{id}/finish")
    public Result<SeckillActivity> finish(@PathVariable Long id) {
        return Result.ok(seckillAdminService.finish(id));
    }

    @Operation(summary = "活动 SKU 与库存预热状态")
    @GetMapping("/activities/{activityId}/skus")
    public Result<List<SeckillSkuVO>> skus(@PathVariable Long activityId) {
        return Result.ok(seckillService.activitySkus(activityId));
    }

    @Operation(summary = "向活动追加物料（划拨活动库存）")
    @PostMapping("/activities/{activityId}/skus")
    public Result<SeckillSku> addSku(@PathVariable Long activityId,
                                     @Validated @RequestBody SeckillSkuRequest req) {
        return Result.ok(seckillAdminService.addSku(activityId, req));
    }

    @Operation(summary = "修改活动 SKU（活动价 / 限购 / 上下架）")
    @PutMapping("/activities/skus/{seckillSkuId}")
    public Result<SeckillSku> updateSku(@PathVariable Long seckillSkuId,
                                        @Validated @RequestBody SeckillSkuRequest req) {
        return Result.ok(seckillAdminService.updateSku(seckillSkuId, req));
    }

    @Operation(summary = "移除活动 SKU（未售库存回拨销售池）")
    @PostMapping("/activities/skus/{seckillSkuId}/remove")
    public Result<SeckillSku> removeSku(@PathVariable Long seckillSkuId,
                                        @RequestParam(required = false) String reason) {
        return Result.ok(seckillAdminService.removeSku(seckillSkuId, reason));
    }

    @Operation(summary = "预热：活动库存写入 Redis，并恢复一人一单集合")
    @PostMapping("/activities/{activityId}/warmup")
    public Result<Void> warmup(@PathVariable Long activityId) {
        seckillService.warmup(activityId);
        return Result.ok();
    }

    @Operation(summary = "抢购令牌分页")
    @GetMapping("/tokens")
    public Result<PageResult<SeckillToken>> tokens(@RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        IPage<SeckillToken> p = tokenMapper.selectPage(new Page<>(page, size),
                Wrappers.<SeckillToken>lambdaQuery()
                        .eq(status != null && !status.isEmpty(), SeckillToken::getStatus, status)
                        .orderByDesc(SeckillToken::getId));
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @Operation(summary = "消费组与 pending-list 状态")
    @GetMapping("/monitor")
    public Result<Map<String, Object>> monitor() {
        Map<String, Object> m = new HashMap<>();
        m.put("consumerRunning", consumer.isRunning());
        m.put("pendingCount", consumer.pendingCount());
        long issued = tokenMapper.selectCount(Wrappers.<SeckillToken>lambdaQuery()
                .eq(SeckillToken::getStatus, "ISSUED"));
        long used = tokenMapper.selectCount(Wrappers.<SeckillToken>lambdaQuery()
                .eq(SeckillToken::getStatus, "USED"));
        m.put("issuedTokens", issued);
        m.put("usedTokens", used);
        return Result.ok(m);
    }
}
