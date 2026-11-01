package com.xinji.erp.modules.dispatch;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.dispatch.dto.DispatchRequest;
import com.xinji.erp.modules.dispatch.dto.DriverRequest;
import com.xinji.erp.modules.dispatch.dto.VehicleRequest;
import com.xinji.erp.modules.dispatch.entity.DispatchOrder;
import com.xinji.erp.modules.dispatch.entity.Driver;
import com.xinji.erp.modules.dispatch.entity.Vehicle;
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

import java.util.List;
import java.util.Map;

@Tag(name = "14 后台 · 调度与车辆")
@RestController
@RequestMapping("/api/admin/dispatch")
@RequiredArgsConstructor
public class AdminDispatchController {

    private final DispatchService dispatchService;

    // ---------------- 车辆 ----------------

    @Operation(summary = "车辆列表")
    @GetMapping("/vehicles")
    public Result<List<Vehicle>> vehicles(@RequestParam(required = false) Integer status) {
        return Result.ok(dispatchService.vehicles(status));
    }

    @Operation(summary = "新增车辆")
    @PostMapping("/vehicles")
    public Result<Vehicle> createVehicle(@Validated @RequestBody VehicleRequest req) {
        return Result.ok(dispatchService.saveVehicle(null, req));
    }

    @Operation(summary = "修改车辆")
    @PutMapping("/vehicles/{id}")
    public Result<Vehicle> updateVehicle(@PathVariable Long id, @Validated @RequestBody VehicleRequest req) {
        return Result.ok(dispatchService.saveVehicle(id, req));
    }

    @Operation(summary = "启用 / 停用车辆")
    @PostMapping("/vehicles/{id}/status")
    public Result<Vehicle> toggleVehicle(@PathVariable Long id,
                                         @RequestParam(required = false) Integer status) {
        return Result.ok(dispatchService.toggleVehicle(id, status));
    }

    @Operation(summary = "保险到期提醒（30 天内）")
    @GetMapping("/vehicles/insurance-warnings")
    public Result<List<Map<String, Object>>> insuranceWarnings() {
        return Result.ok(dispatchService.insuranceWarnings());
    }

    // ---------------- 司机 ----------------

    @Operation(summary = "司机列表")
    @GetMapping("/drivers")
    public Result<List<Driver>> drivers(@RequestParam(required = false) Integer status) {
        return Result.ok(dispatchService.drivers(status));
    }

    @Operation(summary = "新增司机")
    @PostMapping("/drivers")
    public Result<Driver> createDriver(@Validated @RequestBody DriverRequest req) {
        return Result.ok(dispatchService.saveDriver(null, req));
    }

    @Operation(summary = "修改司机")
    @PutMapping("/drivers/{id}")
    public Result<Driver> updateDriver(@PathVariable Long id, @Validated @RequestBody DriverRequest req) {
        return Result.ok(dispatchService.saveDriver(id, req));
    }

    @Operation(summary = "启用 / 停用司机")
    @PostMapping("/drivers/{id}/status")
    public Result<Driver> toggleDriver(@PathVariable Long id,
                                       @RequestParam(required = false) Integer status) {
        return Result.ok(dispatchService.toggleDriver(id, status));
    }

    // ---------------- 派车单 ----------------

    @Operation(summary = "派车单列表")
    @GetMapping("/orders")
    public Result<List<DispatchOrder>> dispatches(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword) {
        return Result.ok(dispatchService.dispatches(status, keyword));
    }

    @Operation(summary = "派车（订单推进到已派车）")
    @PostMapping("/orders")
    public Result<DispatchOrder> create(@Validated @RequestBody DispatchRequest req) {
        return Result.ok(dispatchService.create(req));
    }

    @Operation(summary = "装车中")
    @PostMapping("/orders/{id}/loading")
    public Result<DispatchOrder> loading(@PathVariable Long id) {
        return Result.ok(dispatchService.markLoading(id));
    }

    @Operation(summary = "取消派车")
    @PostMapping("/orders/{id}/cancel")
    public Result<DispatchOrder> cancel(@PathVariable Long id,
                                        @RequestParam(required = false) String reason) {
        return Result.ok(dispatchService.cancel(id, reason));
    }

    @Operation(summary = "调度看板")
    @GetMapping("/board")
    public Result<Map<String, Object>> board() {
        return Result.ok(dispatchService.board());
    }
}
