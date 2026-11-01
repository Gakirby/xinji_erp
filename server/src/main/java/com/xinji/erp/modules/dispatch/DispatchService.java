package com.xinji.erp.modules.dispatch;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.common.OrderStatus;
import com.xinji.erp.modules.dispatch.dto.DispatchRequest;
import com.xinji.erp.modules.dispatch.dto.DriverRequest;
import com.xinji.erp.modules.dispatch.dto.VehicleRequest;
import com.xinji.erp.modules.dispatch.entity.DispatchOrder;
import com.xinji.erp.modules.dispatch.entity.Driver;
import com.xinji.erp.modules.dispatch.entity.Vehicle;
import com.xinji.erp.modules.dispatch.mapper.DispatchOrderMapper;
import com.xinji.erp.modules.dispatch.mapper.DriverMapper;
import com.xinji.erp.modules.dispatch.mapper.VehicleMapper;
import com.xinji.erp.modules.order.OrderService;
import com.xinji.erp.modules.order.entity.Order;
import com.xinji.erp.modules.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度服务：车辆 / 司机档案 + 派车单。
 *
 * 派车不是孤立动作 —— 派车成功会把订单从「已审核」推进到「已派车」，
 * 后续出厂、过磅、签收都围绕同一个 orderNo 展开，避免调度和销售各记一套。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final VehicleMapper vehicleMapper;
    private final DriverMapper driverMapper;
    private final DispatchOrderMapper dispatchMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;

    // ==================== 车辆 ====================

    public List<Vehicle> vehicles(Integer status) {
        return vehicleMapper.selectList(Wrappers.<Vehicle>lambdaQuery()
                .eq(status != null, Vehicle::getStatus, status)
                .orderByAsc(Vehicle::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Vehicle saveVehicle(Long id, VehicleRequest req) {
        Vehicle exists = vehicleMapper.selectOne(Wrappers.<Vehicle>lambdaQuery()
                .eq(Vehicle::getPlateNo, req.getPlateNo()));
        if (exists != null && !exists.getId().equals(id)) {
            throw new BizException("车牌号 " + req.getPlateNo() + " 已存在");
        }
        Vehicle v = id == null ? new Vehicle() : requireVehicle(id);
        v.setPlateNo(req.getPlateNo());
        v.setVehicleType(req.getVehicleType());
        v.setLoadCapacity(req.getLoadCapacity());
        v.setOwnerName(req.getOwnerName());
        v.setOwnerPhone(req.getOwnerPhone());
        v.setInsuranceExpire(req.getInsuranceExpire());
        if (id == null) {
            v.setStatus(1);
            vehicleMapper.insert(v);
        } else {
            vehicleMapper.updateById(v);
        }
        return v;
    }

    public Vehicle toggleVehicle(Long id, Integer status) {
        Vehicle v = requireVehicle(id);
        v.setStatus(status == null ? (v.getStatus() != null && v.getStatus() == 1 ? 0 : 1) : status);
        vehicleMapper.updateById(v);
        return v;
    }

    /** 证照到期提醒：保险 30 天内到期或已过期的车辆 */
    public List<Map<String, Object>> insuranceWarnings() {
        LocalDate deadline = LocalDate.now().plusDays(30);
        List<Vehicle> list = vehicleMapper.selectList(Wrappers.<Vehicle>lambdaQuery()
                .isNotNull(Vehicle::getInsuranceExpire)
                .le(Vehicle::getInsuranceExpire, deadline)
                .orderByAsc(Vehicle::getInsuranceExpire));
        return list.stream().map(v -> {
            Map<String, Object> m = new HashMap<>();
            m.put("vehicleId", v.getId());
            m.put("plateNo", v.getPlateNo());
            m.put("insuranceExpire", v.getInsuranceExpire());
            m.put("daysLeft", v.getInsuranceExpire().toEpochDay() - LocalDate.now().toEpochDay());
            m.put("expired", v.getInsuranceExpire().isBefore(LocalDate.now()));
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    // ==================== 司机 ====================

    public List<Driver> drivers(Integer status) {
        return driverMapper.selectList(Wrappers.<Driver>lambdaQuery()
                .eq(status != null, Driver::getStatus, status)
                .orderByAsc(Driver::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Driver saveDriver(Long id, DriverRequest req) {
        Driver d = id == null ? new Driver() : requireDriver(id);
        d.setDriverName(req.getDriverName());
        d.setPhone(req.getPhone());
        d.setIdCard(req.getIdCard());
        d.setLicenseNo(req.getLicenseNo());
        if (id == null) {
            d.setStatus(1);
            driverMapper.insert(d);
        } else {
            driverMapper.updateById(d);
        }
        return d;
    }

    public Driver toggleDriver(Long id, Integer status) {
        Driver d = requireDriver(id);
        d.setStatus(status == null ? (d.getStatus() != null && d.getStatus() == 1 ? 0 : 1) : status);
        driverMapper.updateById(d);
        return d;
    }

    // ==================== 派车单 ====================

    public List<DispatchOrder> dispatches(String status, String keyword) {
        return dispatchMapper.selectList(Wrappers.<DispatchOrder>lambdaQuery()
                .eq(status != null && !status.isEmpty(), DispatchOrder::getStatus, status)
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(DispatchOrder::getOrderNo, keyword)
                        .or().like(DispatchOrder::getPlateNo, keyword)
                        .or().like(DispatchOrder::getCustomerName, keyword))
                .orderByDesc(DispatchOrder::getId));
    }

    /**
     * 派车：校验订单状态 → 落派车单 → 订单推进到「已派车」。
     * 同一订单不允许有第二张有效派车单。
     */
    @Transactional(rollbackFor = Exception.class)
    public DispatchOrder create(DispatchRequest req) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, req.getOrderNo()));
        if (order == null) {
            throw BizException.notFound("订单不存在: " + req.getOrderNo());
        }
        if (!OrderStatus.AUDITED.name().equals(order.getStatus())
                && !OrderStatus.DISPATCHED.name().equals(order.getStatus())) {
            throw new BizException("订单当前状态为「" + OrderStatus.labelOf(order.getStatus())
                    + "」，只有已审核的订单才能派车");
        }
        Long active = dispatchMapper.selectCount(Wrappers.<DispatchOrder>lambdaQuery()
                .eq(DispatchOrder::getOrderNo, req.getOrderNo())
                .notIn(DispatchOrder::getStatus,
                        DispatchOrder.CANCELLED, DispatchOrder.DONE));
        if (active > 0) {
            throw new BizException("订单 " + req.getOrderNo() + " 已有进行中的派车单，不能重复派车");
        }

        Vehicle v = requireVehicle(req.getVehicleId());
        if (v.getStatus() == null || v.getStatus() != 1) {
            throw new BizException("车辆 " + v.getPlateNo() + " 已停用，不能派车");
        }
        if (v.getInsuranceExpire() != null && v.getInsuranceExpire().isBefore(LocalDate.now())) {
            throw new BizException("车辆 " + v.getPlateNo() + " 保险已于 "
                    + v.getInsuranceExpire() + " 到期，请先续保再派车");
        }
        if (v.getLoadCapacity() != null && req.getPlanQty().compareTo(v.getLoadCapacity()) > 0) {
            throw new BizException("计划装载 " + req.getPlanQty() + " 吨超过车辆核定载重 "
                    + v.getLoadCapacity() + " 吨");
        }
        Driver d = requireDriver(req.getDriverId());
        if (d.getStatus() == null || d.getStatus() != 1) {
            throw new BizException("司机 " + d.getDriverName() + " 已停用，不能派车");
        }

        DispatchOrder o = new DispatchOrder();
        o.setDispatchNo(IdGen.dispatchNo());
        o.setOrderNo(order.getOrderNo());
        o.setCustomerName(order.getCustomerName());
        o.setVehicleId(v.getId());
        o.setPlateNo(v.getPlateNo());
        o.setDriverId(d.getId());
        o.setDriverName(d.getDriverName());
        o.setDriverPhone(d.getPhone());
        o.setPlanQty(req.getPlanQty());
        o.setPlanTime(req.getPlanTime());
        o.setStatus(DispatchOrder.PENDING);
        o.setRemark(req.getRemark());
        dispatchMapper.insert(o);

        if (OrderStatus.AUDITED.name().equals(order.getStatus())) {
            orderService.transfer(order.getOrderNo(), OrderStatus.DISPATCHED);
        }
        log.info("派车成功: {} 订单={} 车辆={} 司机={}", o.getDispatchNo(), order.getOrderNo(),
                v.getPlateNo(), d.getDriverName());
        return o;
    }

    /** 装车中 → 已出厂（订单出厂走订单接口，这里只同步派车单状态） */
    public DispatchOrder markLoading(Long id) {
        DispatchOrder o = requireDispatch(id);
        if (!DispatchOrder.PENDING.equals(o.getStatus())) {
            throw new BizException("派车单当前状态为「" + o.getStatus() + "」，只有待发车可以转装车中");
        }
        o.setStatus(DispatchOrder.LOADING);
        dispatchMapper.updateById(o);
        return o;
    }

    public DispatchOrder cancel(Long id, String reason) {
        DispatchOrder o = requireDispatch(id);
        if (DispatchOrder.SHIPPED.equals(o.getStatus()) || DispatchOrder.DONE.equals(o.getStatus())) {
            throw new BizException("派车单已出厂/已签收，不能取消");
        }
        o.setStatus(DispatchOrder.CANCELLED);
        o.setRemark(reason == null ? o.getRemark() : reason);
        dispatchMapper.updateById(o);
        return o;
    }

    /** 订单出厂后由订单模块回调，保证派车单与订单状态一致 */
    public void markShippedByOrderNo(String orderNo) {
        List<DispatchOrder> list = dispatchMapper.selectList(Wrappers.<DispatchOrder>lambdaQuery()
                .eq(DispatchOrder::getOrderNo, orderNo)
                .in(DispatchOrder::getStatus, DispatchOrder.PENDING, DispatchOrder.LOADING));
        for (DispatchOrder o : list) {
            o.setStatus(DispatchOrder.SHIPPED);
            dispatchMapper.updateById(o);
        }
    }

    /** 调度看板：派车单状态分布 + 今日计划发车数 */
    public Map<String, Object> board() {
        Map<String, Object> m = new HashMap<>();
        m.put("byStatus", dispatchMapper.countByStatus());
        m.put("pending", dispatchMapper.selectCount(Wrappers.<DispatchOrder>lambdaQuery()
                .in(DispatchOrder::getStatus, DispatchOrder.PENDING, DispatchOrder.LOADING)));
        m.put("insuranceWarnings", insuranceWarnings());
        return m;
    }

    private Vehicle requireVehicle(Long id) {
        Vehicle v = vehicleMapper.selectById(id);
        if (v == null) {
            throw BizException.notFound("车辆不存在: " + id);
        }
        return v;
    }

    private Driver requireDriver(Long id) {
        Driver d = driverMapper.selectById(id);
        if (d == null) {
            throw BizException.notFound("司机不存在: " + id);
        }
        return d;
    }

    private DispatchOrder requireDispatch(Long id) {
        DispatchOrder o = dispatchMapper.selectById(id);
        if (o == null) {
            throw BizException.notFound("派车单不存在: " + id);
        }
        return o;
    }

    /** 供订单模块判断是否有在途派车，避免订单被误取消 */
    public boolean hasActiveDispatch(String orderNo) {
        return dispatchMapper.selectCount(Wrappers.<DispatchOrder>lambdaQuery()
                .eq(DispatchOrder::getOrderNo, orderNo)
                .in(DispatchOrder::getStatus, DispatchOrder.PENDING,
                        DispatchOrder.LOADING, DispatchOrder.SHIPPED)) > 0;
    }

    /** 装载率：用于调度考核 */
    public BigDecimal loadRate(DispatchOrder o) {
        if (o.getPlanQty() == null || o.getPlanQty().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return o.getPlanQty();
    }
}
