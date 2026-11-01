package com.xinji.erp.modules.product;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.common.cache.MultiLevelCache;
import com.xinji.erp.modules.product.dto.SkuRequest;
import com.xinji.erp.modules.product.dto.WarehouseRequest;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.product.entity.Warehouse;
import com.xinji.erp.modules.product.mapper.SkuMapper;
import com.xinji.erp.modules.product.mapper.WarehouseMapper;
import com.xinji.erp.modules.product.vo.SkuVO;
import com.xinji.erp.modules.stock.StockService;
import com.xinji.erp.modules.stock.dto.StockOpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String CACHE_KEY_LIST = "xj:sku:list";
    private static final String CACHE_KEY_DETAIL = "xj:sku:detail:";

    private final SkuMapper skuMapper;
    private final WarehouseMapper warehouseMapper;
    private final StockService stockService;
    private final MultiLevelCache cache;
    private final ObjectMapper objectMapper;

    @Value("${xinji.cache.redis-ttl-seconds:600}")
    private long ttl;

    /** 客户端商品列表：走四级缓存，命中下层会回填请求内缓存 */
    public List<SkuVO> listForClient() {
        String cached = cache.get(CACHE_KEY_LIST);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<SkuVO>>() {
                });
            } catch (Exception e) {
                log.warn("商品缓存反序列化失败，回源数据库: {}", e.getMessage());
            }
        }
        List<SkuVO> list = loadAll();
        if (list.isEmpty()) {
            cache.putNull(CACHE_KEY_LIST);
        } else {
            try {
                cache.put(CACHE_KEY_LIST, objectMapper.writeValueAsString(list), ttl);
            } catch (Exception e) {
                log.warn("商品缓存写入失败: {}", e.getMessage());
            }
        }
        return list;
    }

    /** 员工侧：库存要准，直接读库，不走缓存 */
    public List<SkuVO> listForAdmin() {
        return loadAll();
    }

    public SkuVO detail(Long id, boolean fromCache) {
        if (fromCache) {
            String key = CACHE_KEY_DETAIL + id;
            String cached = cache.get(key);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, SkuVO.class);
                } catch (Exception ignored) {
                }
            }
            SkuVO vo = loadOne(id);
            try {
                cache.put(key, objectMapper.writeValueAsString(vo), ttl);
            } catch (Exception ignored) {
            }
            return vo;
        }
        return loadOne(id);
    }

    private SkuVO loadOne(Long id) {
        Sku sku = skuMapper.selectById(id);
        if (sku == null) {
            throw BizException.notFound("物料不存在");
        }
        Map<Long, String> whMap = warehouseNoMap();
        return toVO(sku, whMap);
    }

    private List<SkuVO> loadAll() {
        List<Sku> skus = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                .eq(Sku::getStatus, 1)
                .orderByAsc(Sku::getId));
        Map<Long, String> whMap = warehouseNoMap();
        List<SkuVO> result = new ArrayList<>(skus.size());
        for (Sku s : skus) {
            result.add(toVO(s, whMap));
        }
        return result;
    }

    private Map<Long, String> warehouseNoMap() {
        List<Warehouse> list = warehouseMapper.selectList(null);
        Map<Long, String> map = new HashMap<>(list.size());
        for (Warehouse w : list) {
            map.put(w.getId(), w.getWarehouseNo());
        }
        return map;
    }

    private SkuVO toVO(Sku s, Map<Long, String> whMap) {
        SkuVO v = new SkuVO();
        v.setId(s.getId());
        v.setSkuNo(s.getSkuNo());
        v.setSkuName(s.getSkuName());
        v.setCategory(s.getCategory());
        v.setSpec(s.getSpec());
        v.setUnit(s.getUnit());
        v.setPrice(s.getPrice());
        v.setSaleableQty(nz(s.getStockQty()));
        v.setActivityAllocQty(nz(s.getActivityAllocQty()));
        v.setFrozenQty(nz(s.getFrozenQty()));
        v.setTotalQty(nz(s.getStockQty()).add(nz(s.getActivityAllocQty())).add(nz(s.getFrozenQty())));
        v.setSafetyQty(nz(s.getSafetyQty()));
        v.setWarehouseNo(whMap.get(s.getWarehouseId()));
        v.setImageTag(s.getImageTag());
        v.setStockStatus(stockStatus(v.getSaleableQty(), v.getSafetyQty()));
        return v;
    }

    private String stockStatus(BigDecimal saleable, BigDecimal safety) {
        if (safety.compareTo(BigDecimal.ZERO) <= 0) {
            return "正常";
        }
        BigDecimal ratio = saleable.divide(safety, 2, BigDecimal.ROUND_HALF_UP);
        if (ratio.compareTo(new BigDecimal("0.5")) < 0) {
            return "紧张";
        }
        if (ratio.compareTo(BigDecimal.ONE) < 0) {
            return "偏低";
        }
        if (ratio.compareTo(new BigDecimal("2")) > 0) {
            return "充足";
        }
        return "正常";
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 库存预警：只列可售量低于安全库存的物料，越紧张越靠前 */
    public List<Map<String, Object>> warningList() {
        List<SkuVO> all = loadAll();
        return all.stream()
                .filter(v -> v.getSafetyQty().compareTo(BigDecimal.ZERO) > 0
                        && v.getSaleableQty().compareTo(v.getSafetyQty()) < 0)
                .sorted((a, b) -> a.getSaleableQty().compareTo(b.getSaleableQty()))
                .limit(10)
                .map(v -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("skuId", v.getId());
                    m.put("skuName", v.getSkuName());
                    m.put("warehouseNo", v.getWarehouseNo());
                    m.put("saleableQty", v.getSaleableQty());
                    m.put("safetyQty", v.getSafetyQty());
                    m.put("status", v.getStockStatus());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ==================== 物料档案维护 ====================

    /**
     * 新建 / 修改物料。
     * 建档时的期初库存走 StockService.inbound，保证「有库存就有台账」，
     * 不允许直接 update stock_qty 塞一个数字进去。
     */
    @Transactional(rollbackFor = Exception.class)
    public Sku saveSku(Long id, SkuRequest req) {
        String skuNo = req.getSkuNo() == null || req.getSkuNo().trim().isEmpty()
                ? IdGen.next("WL") : req.getSkuNo().trim();
        Sku dup = skuMapper.selectOne(Wrappers.<Sku>lambdaQuery().eq(Sku::getSkuNo, skuNo));
        if (dup != null && !dup.getId().equals(id)) {
            throw new BizException("物料编码 " + skuNo + " 已存在");
        }
        if (req.getWarehouseId() != null && warehouseMapper.selectById(req.getWarehouseId()) == null) {
            throw BizException.notFound("料仓不存在: " + req.getWarehouseId());
        }
        Sku sku = id == null ? new Sku() : requireSku(id);
        sku.setSkuNo(skuNo);
        sku.setSkuName(req.getSkuName());
        sku.setCategory(req.getCategory());
        sku.setSpec(req.getSpec());
        sku.setUnit(req.getUnit() == null || req.getUnit().isEmpty() ? "吨" : req.getUnit());
        sku.setPrice(req.getPrice());
        sku.setWarehouseId(req.getWarehouseId());
        sku.setSafetyQty(req.getSafetyQty() == null ? BigDecimal.ZERO : req.getSafetyQty());
        sku.setImageTag(req.getImageTag());

        if (id == null) {
            sku.setStockQty(BigDecimal.ZERO);
            sku.setActivityAllocQty(BigDecimal.ZERO);
            sku.setFrozenQty(BigDecimal.ZERO);
            sku.setStatus(1);
            skuMapper.insert(sku);
            if (req.getInitQty() != null && req.getInitQty().compareTo(BigDecimal.ZERO) > 0) {
                StockOpRequest op = new StockOpRequest();
                op.setSkuId(sku.getId());
                op.setQty(req.getInitQty());
                op.setBizNo("INIT-" + sku.getSkuNo());
                op.setRemark("物料建档期初库存");
                stockService.inbound(op);
                // 期初入库后库存已变，重新读一次再返回，避免前端拿到 0
                sku = skuMapper.selectById(sku.getId());
            }
        } else {
            skuMapper.updateById(sku);
        }
        stockService.evictSkuAfterCommit(sku.getId());
        return sku;
    }

    /** 上架 / 下架。下架后客户端列表不再出现，历史订单不受影响 */
    public Sku toggleSku(Long id, Integer status) {
        Sku sku = requireSku(id);
        sku.setStatus(status == null ? (sku.getStatus() != null && sku.getStatus() == 1 ? 0 : 1) : status);
        skuMapper.updateById(sku);
        stockService.evictSkuAfterCommit(sku.getId());
        return sku;
    }

    private Sku requireSku(Long id) {
        Sku sku = skuMapper.selectById(id);
        if (sku == null) {
            throw BizException.notFound("物料不存在: " + id);
        }
        return sku;
    }

    // ==================== 料仓 ====================

    public List<Warehouse> warehouses() {
        return warehouseMapper.selectList(Wrappers.<Warehouse>lambdaQuery()
                .orderByAsc(Warehouse::getWarehouseNo));
    }

    public Warehouse saveWarehouse(WarehouseRequest req) {
        Warehouse dup = warehouseMapper.selectOne(Wrappers.<Warehouse>lambdaQuery()
                .eq(Warehouse::getWarehouseNo, req.getWarehouseNo()));
        if (dup != null) {
            throw new BizException("料仓编号 " + req.getWarehouseNo() + " 已存在");
        }
        Warehouse w = new Warehouse();
        w.setWarehouseNo(req.getWarehouseNo());
        w.setWarehouseName(req.getWarehouseName());
        w.setCapacity(req.getCapacity());
        warehouseMapper.insert(w);
        return w;
    }
}
