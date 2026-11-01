-- =====================================================================
--  抢购原子脚本
--  把「资格判断 + 一人一单 + 库存扣减 + 写消息」压缩成 Redis 内一次执行，
--  避免高并发下的超卖与重复抢购。
--
--  KEYS[1] 活动库存 key     sk:stock:{seckillSkuId}
--  KEYS[2] 已抢用户集合 key  sk:users:{seckillSkuId}
--  KEYS[3] 消息流 key        sk:stream:token
--
--  ARGV[1] userId
--  ARGV[2] qty
--  ARGV[3] tokenNo
--  ARGV[4] seckillSkuId
--  ARGV[5] activityId
--  ARGV[6] price
--  ARGV[7] limitPerUser
--
--  返回：>=0 扣减后的剩余库存
--        -1  重复抢购（一人一单）
--        -2  未预热 / 活动不存在
--        -3  库存不足
--        -4  超过每人限购
-- =====================================================================

local stockKey  = KEYS[1]
local userKey   = KEYS[2]
local streamKey = KEYS[3]

local userId = ARGV[1]
local qty    = tonumber(ARGV[2])
local tokenNo = ARGV[3]
local seckillSkuId = ARGV[4]
local activityId = ARGV[5]
local price  = ARGV[6]
local limit  = tonumber(ARGV[7])

-- 一人一单：已经抢过的直接返回
if redis.call('SISMEMBER', userKey, userId) == 1 then
    return -1
end

-- 活动库存必须已预热
local stockStr = redis.call('GET', stockKey)
if not stockStr then
    return -2
end
local stock = tonumber(stockStr)
if stock <= 0 then
    return -3
end
if qty <= 0 then
    return -4
end
if qty > limit then
    return -4
end
if stock < qty then
    return -3
end

-- 扣库存 + 记录用户 + 投递发放消息，三步在脚本内原子完成
redis.call('DECRBY', stockKey, qty)
redis.call('SADD', userKey, userId)
redis.call('XADD', streamKey, '*',
        'tokenNo', tokenNo,
        'userId', userId,
        'seckillSkuId', seckillSkuId,
        'activityId', activityId,
        'qty', tostring(qty),
        'price', price)

return stock - qty
