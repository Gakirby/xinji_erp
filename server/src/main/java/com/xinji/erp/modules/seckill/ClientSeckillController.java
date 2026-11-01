package com.xinji.erp.modules.seckill;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.order.dto.CreateOrderRequest;
import com.xinji.erp.modules.order.vo.OrderVO;
import com.xinji.erp.modules.seckill.dto.GrabRequest;
import com.xinji.erp.modules.seckill.dto.GrabResult;
import com.xinji.erp.modules.seckill.entity.SeckillActivity;
import com.xinji.erp.modules.seckill.entity.SeckillToken;
import com.xinji.erp.modules.seckill.vo.SeckillSkuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "04 客户端 · 限时抢购")
@RestController
@RequestMapping("/api/client/seckill")
@RequiredArgsConstructor
public class ClientSeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "活动列表")
    @GetMapping("/activities")
    public Result<List<SeckillActivity>> activities() {
        return Result.ok(seckillService.activities());
    }

    @Operation(summary = "活动 SKU（活动库存池与销售可售池分别展示）")
    @GetMapping("/activities/{activityId}/skus")
    public Result<List<SeckillSkuVO>> skus(@PathVariable Long activityId) {
        return Result.ok(seckillService.activitySkus(activityId));
    }

    @Operation(summary = "抢购（Lua 原子扣减，立即返回令牌号）")
    @PostMapping("/grab")
    public Result<GrabResult> grab(@Validated @RequestBody GrabRequest req) {
        GrabResult r = seckillService.grab(req);
        // 抢购是高频接口，没抢到属于正常业务结果，不抛异常；用 msg 带上原因方便前端直接提示
        Result<GrabResult> res = Result.ok(r);
        res.setMsg(r.isSuccess() ? "抢购成功，请尽快提交订单" : r.getReason());
        return res;
    }

    @Operation(summary = "查询令牌发放状态（前端轮询）")
    @GetMapping("/tokens/{tokenNo}")
    public Result<SeckillToken> token(@PathVariable String tokenNo) {
        return Result.ok(seckillService.queryToken(tokenNo));
    }

    @Operation(summary = "我的抢购记录")
    @GetMapping("/tokens")
    public Result<List<SeckillToken>> myTokens() {
        return Result.ok(seckillService.myTokens());
    }

    @Operation(summary = "令牌核销下单")
    @PostMapping("/tokens/{tokenNo}/order")
    public Result<OrderVO> useToken(@PathVariable String tokenNo,
                                    @RequestBody CreateOrderRequest req) {
        return Result.ok(seckillService.useToken(tokenNo, req));
    }
}
