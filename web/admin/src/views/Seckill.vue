<template>
  <div>
    <div class="page-card">
      <div class="bar">
        <el-button type="primary" @click="openActivity">新建活动</el-button>
        <div class="spacer"></div>
        <span class="muted small">发布后可预热，把活动库存与已抢用户灌进 Redis</span>
      </div>

      <el-table :data="activities" v-loading="loading" size="small" empty-text="暂无活动" highlight-current-row @row-click="select">
        <el-table-column prop="activityNo" label="活动编号" width="140">
          <template #default="{ row }"><span class="mono">{{ row.activityNo }}</span></template>
        </el-table-column>
        <el-table-column prop="activityName" label="活动名称" min-width="200" />
        <el-table-column label="起止时间" width="260">
          <template #default="{ row }">
            <span class="small muted">{{ dateStr(row.startTime) }} ~ {{ dateStr(row.endTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="statusType(ACTIVITY_STATUS, row.status)" size="small">{{ label(ACTIVITY_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预热" width="96">
          <template #default="{ row }">
            <el-tag :type="row.warmupStatus === 'DONE' ? 'success' : 'info'" size="small">
              {{ row.warmupStatus === 'DONE' ? '已预热' : '未预热' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click.stop="publish(row)">发布</el-button>
            <el-button v-if="['PUBLISHED', 'DRAFT'].includes(row.status)" link type="warning" size="small" @click.stop="warmup(row)">预热</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="success" size="small" @click.stop="start(row)">开始</el-button>
            <el-button v-if="row.status === 'RUNNING'" link type="danger" size="small" @click.stop="finish(row)">结束并回拨</el-button>
            <el-button link size="small" @click.stop="select(row)">配置商品</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card" v-if="current">
      <div class="bar">
        <div class="section-title" style="margin: 0">{{ current.activityName }} · 活动商品</div>
        <div class="spacer"></div>
        <el-button @click="loadMonitor">刷新监控</el-button>
        <el-button type="primary" @click="openSku">添加商品</el-button>
      </div>

      <el-table :data="actSkus" size="small" empty-text="还没有配置活动商品">
        <el-table-column prop="seckillSkuNo" label="活动SKU" width="130">
          <template #default="{ row }"><span class="mono small">{{ row.seckillSkuNo }}</span></template>
        </el-table-column>
        <el-table-column prop="skuName" label="物料" min-width="140" />
        <el-table-column label="活动价" width="100" align="right">
          <template #default="{ row }"><span class="num" style="color: #c0392b; font-weight: 600">{{ money(row.seckillPrice) }}</span></template>
        </el-table-column>
        <el-table-column label="原价" width="90" align="right">
          <template #default="{ row }"><span class="num muted">{{ money(row.originalPrice) }}</span></template>
        </el-table-column>
        <el-table-column label="活动库存" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ qtyShort(row.activityStock) }}</span></template>
        </el-table-column>
        <el-table-column label="已抢" width="96" align="right">
          <template #default="{ row }"><span class="num" style="color: #bd7415">{{ qtyShort(row.soldQty) }}</span></template>
        </el-table-column>
        <el-table-column label="剩余" width="96" align="right">
          <template #default="{ row }"><span class="num" style="color: #0f8a5f">{{ qtyShort(row.remainQty) }}</span></template>
        </el-table-column>
        <el-table-column label="限购" width="90" align="right">
          <template #default="{ row }"><span class="num muted">{{ qtyShort(row.limitPerUser) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ON' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openSku(row)">改价</el-button>
            <el-button link type="danger" size="small" @click="removeSku(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card">
      <div class="bar">
        <div class="section-title" style="margin: 0">抢购令牌</div>
        <div class="spacer"></div>
        <span class="muted small">
          发放中 {{ monitor.PENDING || 0 }} · 待核销 {{ monitor.ISSUED || 0 }} · 已下单 {{ monitor.USED || 0 }}
        </span>
        <el-button size="small" @click="loadTokens">刷新</el-button>
      </div>
      <el-table :data="tokens" size="small" empty-text="暂无令牌" max-height="380">
        <el-table-column prop="tokenNo" label="令牌号" width="150">
          <template #default="{ row }"><span class="mono small">{{ row.tokenNo }}</span></template>
        </el-table-column>
        <el-table-column label="吨数" width="90" align="right">
          <template #default="{ row }"><span class="num">{{ qtyShort(row.qty) }}</span></template>
        </el-table-column>
        <el-table-column label="成交价" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.price) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(TOKEN_STATUS, row.status)" size="small">{{ label(TOKEN_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="关联订单" width="150">
          <template #default="{ row }"><span class="mono small">{{ row.orderNo || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="过期时间" width="150">
          <template #default="{ row }"><span class="muted small">{{ dateStr(row.expireTime) }}</span></template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" min-width="140" show-overflow-tooltip />
      </el-table>
    </div>

    <el-dialog v-model="showActivity" title="新建秒杀活动" width="460px">
      <el-form :model="actForm" label-width="92px">
        <el-form-item label="活动名称"><el-input v-model="actForm.activityName" /></el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="actForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="actForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showActivity = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createActivity">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showSku" :title="skuForm.id ? '修改活动商品' : '添加活动商品'" width="460px">
      <el-form :model="skuForm" label-width="96px">
        <el-form-item label="物料" v-if="!skuForm.id">
          <el-select v-model="skuForm.skuId" filterable style="width: 100%" @change="onSku">
            <el-option v-for="s in skus" :key="s.id" :label="s.skuName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动价">
          <el-input-number v-model="skuForm.seckillPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="活动库存">
          <el-input-number v-model="skuForm.activityStock" :min="0" :precision="3" style="width: 100%" :disabled="!!skuForm.id" />
          <div class="muted small" style="margin-top: 4px">活动库存需要先通过「库存管理 → 活动划拨」从销售池挪过来</div>
        </el-form-item>
        <el-form-item label="每人限购">
          <el-input-number v-model="skuForm.limitPerUser" :min="1" :precision="3" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSku = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSku">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'
import {
  ACTIVITY_STATUS, TOKEN_STATUS, label, statusType, money, qtyShort, dateStr
} from '@xinji/shared'

const activities = ref([])
const actSkus = ref([])
const tokens = ref([])
const skus = ref([])
const monitor = ref({})
const current = ref(null)
const loading = ref(false)
const saving = ref(false)
const showActivity = ref(false)
const showSku = ref(false)

const actForm = reactive({ activityName: '', startTime: '', endTime: '' })
const skuForm = reactive({ id: null, skuId: null, seckillPrice: 0, activityStock: 0, limitPerUser: 60 })

async function load() {
  loading.value = true
  try {
    activities.value = (await api.admin.seckill.activities()) || []
    if (!current.value && activities.value.length) select(activities.value[0])
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function select(row) {
  current.value = row
  try {
    actSkus.value = (await api.admin.seckill.activitySkus(row.id)) || []
  } catch (e) {
    actSkus.value = []
  }
}

function openActivity() {
  const p = (n) => String(n).padStart(2, '0')
  const now = new Date()
  const end = new Date(Date.now() + 3 * 86400000)
  const fmt = (d) => `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`
  Object.assign(actForm, { activityName: '', startTime: fmt(now), endTime: fmt(end) })
  showActivity.value = true
}

async function createActivity() {
  if (!actForm.activityName) {
    ElMessage.warning('请填写活动名称')
    return
  }
  saving.value = true
  try {
    await api.admin.seckill.createActivity({ ...actForm })
    ElMessage.success('活动已创建，下一步配置商品并发布')
    showActivity.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function publish(row) {
  try {
    await api.admin.seckill.publish(row.id)
    ElMessage.success('已发布')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function warmup(row) {
  try {
    await api.admin.seckill.warmup(row.id)
    ElMessage.success('预热完成，库存已写入 Redis')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function start(row) {
  try {
    await api.admin.seckill.start(row.id)
    ElMessage.success('活动已开始')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function finish(row) {
  try {
    await ElMessageBox.confirm('结束后会把未售出的活动库存回拨到销售可售池，确定？', '结束活动', { type: 'warning' })
    await api.admin.seckill.finish(row.id)
    ElMessage.success('活动已结束，库存已回拨')
    load()
    select(row)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

function openSku(row) {
  if (!current.value) {
    ElMessage.warning('请先选择一个活动')
    return
  }
  if (row) {
    Object.assign(skuForm, {
      id: row.id, skuId: row.skuId, seckillPrice: Number(row.seckillPrice),
      activityStock: Number(row.activityStock), limitPerUser: Number(row.limitPerUser)
    })
  } else {
    Object.assign(skuForm, {
      id: null, skuId: skus.value[0]?.id || null,
      seckillPrice: Number(skus.value[0]?.price || 0) * 0.8,
      activityStock: 100, limitPerUser: 60
    })
  }
  showSku.value = true
}

function onSku(id) {
  const s = skus.value.find((x) => x.id === id)
  if (s) skuForm.seckillPrice = Number((Number(s.price) * 0.8).toFixed(2))
}

async function saveSku() {
  saving.value = true
  try {
    if (skuForm.id) await api.admin.seckill.updateSku(skuForm.id, { ...skuForm })
    else await api.admin.seckill.addSku(current.value.id, { ...skuForm })
    ElMessage.success('已保存')
    showSku.value = false
    select(current.value)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function removeSku(row) {
  try {
    await ElMessageBox.confirm('移除后活动池会回拨到销售池，确定？', '移除活动商品', { type: 'warning' })
    await api.admin.seckill.removeSku(row.id)
    ElMessage.success('已移除')
    select(current.value)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

async function loadTokens() {
  try {
    tokens.value = (await api.admin.seckill.tokens()) || []
    monitor.value = (await api.admin.seckill.monitor()) || {}
  } catch (e) { /* ignore */ }
}

async function loadMonitor() {
  try {
    monitor.value = (await api.admin.seckill.monitor()) || {}
    ElMessage.success('监控已刷新')
  } catch (e) { /* ignore */ }
}

onMounted(async () => {
  load()
  loadTokens()
  try {
    skus.value = (await api.admin.product.list()) || []
  } catch (e) { /* ignore */ }
})
</script>
