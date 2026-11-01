package com.xinji.erp.modules.weigh;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.modules.order.OrderService;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.stock.StockService;
import com.xinji.erp.modules.weigh.dto.WeighTicketRequest;
import com.xinji.erp.modules.weigh.entity.WeighTicket;
import com.xinji.erp.modules.weigh.mapper.WeighTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 过磅：矿石行业最容易扯皮的环节，所以每一步都算清楚并留痕。
 *   净重   = 毛重 - 皮重
 *   扣水量 = 净重 × 含水率
 *   结算量 = 净重 - 扣水量 - 扣杂量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeighService {

    private final WeighTicketMapper ticketMapper;
    private final OrderService orderService;
    private final StockService stockService;

    @Transactional(rollbackFor = Exception.class)
    public WeighTicket create(WeighTicketRequest req) {
        if (req.getGrossWeight().compareTo(req.getTareWeight()) <= 0) {
            throw new BizException("毛重必须大于皮重，请检查地磅读数");
        }
        Sku sku = stockService.requireSku(req.getSkuId());

        BigDecimal net = req.getGrossWeight().subtract(req.getTareWeight())
                .setScale(3, BigDecimal.ROUND_HALF_UP);
        BigDecimal rate = req.getWaterRate() == null ? BigDecimal.ZERO : req.getWaterRate();
        BigDecimal waterDeduct = net.multiply(rate).setScale(3, BigDecimal.ROUND_HALF_UP);
        BigDecimal impurity = req.getImpurityDeductWeight() == null
                ? BigDecimal.ZERO : req.getImpurityDeductWeight();
        BigDecimal settle = net.subtract(waterDeduct).subtract(impurity)
                .setScale(3, BigDecimal.ROUND_HALF_UP);
        if (settle.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("扣水扣杂已超过净重，请核对数据");
        }

        WeighTicket t = new WeighTicket();
        t.setTicketNo(IdGen.ticketNo());
        t.setOrderNo(req.getOrderNo());
        t.setSkuId(req.getSkuId());
        t.setPlateNo(req.getPlateNo());
        t.setDriverName(req.getDriverName());
        t.setDriverPhone(req.getDriverPhone());
        t.setGrossWeight(req.getGrossWeight());
        t.setTareWeight(req.getTareWeight());
        t.setNetWeight(net);
        t.setWaterRate(rate);
        t.setWaterDeductWeight(waterDeduct);
        t.setImpurityDeductWeight(impurity);
        t.setSettleWeight(settle);
        t.setWeighTime(LocalDateTime.now());
        t.setWeighman(req.getWeighman());
        t.setStatus("ISSUED");
        ticketMapper.insert(t);

        // 结算量回填到订单明细：财务后续按这个数收钱，不再二次计算
        BigDecimal amount = settle.multiply(sku.getPrice()).setScale(2, BigDecimal.ROUND_HALF_UP);
        orderService.applySettleQty(req.getOrderNo(), req.getSkuId(), settle, amount);

        log.info("过磅出票: {} 订单={} 净重={} 结算={}", t.getTicketNo(), req.getOrderNo(), net, settle);
        return t;
    }

    public List<WeighTicket> listByOrder(String orderNo) {
        return ticketMapper.selectList(Wrappers.<WeighTicket>lambdaQuery()
                .eq(orderNo != null && !orderNo.isEmpty(), WeighTicket::getOrderNo, orderNo)
                .orderByDesc(WeighTicket::getId));
    }

    public List<WeighTicket> recent() {
        return ticketMapper.selectList(Wrappers.<WeighTicket>lambdaQuery()
                .orderByDesc(WeighTicket::getId)
                .last("limit 50"));
    }
}
