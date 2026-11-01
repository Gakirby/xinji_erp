package com.xinji.erp.modules.weigh;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.weigh.dto.WeighTicketRequest;
import com.xinji.erp.modules.weigh.entity.WeighTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "14 后台 · 调度过磅")
@RestController
@RequestMapping("/api/admin/weigh")
@RequiredArgsConstructor
public class AdminWeighController {

    private final WeighService weighService;

    @Operation(summary = "过磅出票（自动算净重、扣水、扣杂、结算吨数）")
    @PostMapping("/tickets")
    public Result<WeighTicket> create(@Validated @RequestBody WeighTicketRequest req) {
        return Result.ok(weighService.create(req));
    }

    @Operation(summary = "磅单列表")
    @GetMapping("/tickets")
    public Result<List<WeighTicket>> list(@RequestParam(required = false) String orderNo) {
        if (orderNo == null || orderNo.isEmpty()) {
            return Result.ok(weighService.recent());
        }
        return Result.ok(weighService.listByOrder(orderNo));
    }
}
