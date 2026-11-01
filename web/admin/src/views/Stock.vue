<template>
  <div>
    <div class="page-card">
      <div class="bar">
        <el-button type="primary" @click="open('alloc')">活动划拨</el-button>
        <el-button @click="open('return')">活动回拨</el-button>
        <el-button @click="open('inbound')">采购入库</el-button>
        <div class="spacer"></div>
        <span class="muted small">划拨不减总量，只把货从销售池挪到活动池</span>
      </div>

      <el-tabs v-model="tab" @tab-change="onTab">
        <el-tab-pane label="库存台账" name="ledger">
          <el-table :data="skus" v-loading="loading" size="small">
            <el-table-column prop="skuName" label="物料" min-width="150" />
            <el-table-column prop="category" label="品种" width="90" />
            <el-table-column label="销售可售" width="110" align="right">
              <template #default="{ row }">
                <span class="num" :style="{ color: Number(row.saleableQty) < Number(row.safetyQty) ? '#c0392b' : '#0f8a5f', fontWeight: 600 }">
                  {{ qty(row.saleableQty) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="活动划拨" width="110" align="right">
              <template #default="{ row }"><span class="num" style="color: #bd7415">{{ qty(row.activityAllocQty) }}</span></template>
            </el-table-column>
            <el-table-column label="订单冻结" width="110" align="right">
              <template #default="{ row }"><span class="num" style="color: #2c7499">{{ qty(row.frozenQty) }}</span></template>
            </el-table-column>
            <el-table-column label="物理总量" width="110" align="right">
              <template #default="{ row }"><span class="num" style="font-weight: 600">{{ qty(row.totalQty) }}</span></template>
            </el-table-column>
            <el-table-column label="安全线" width="100" align="right">
              <template #default="{ row }"><span class="num muted">{{ qty(row.safetyQty) }}</span></template>
            </el-table-column>
            <el-table-column prop="warehouseNo" label="料仓" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.stockStatus === '正常' ? 'success' : 'warning'" size="small">{{ row.stockStatus }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="出入库流水" name="flow">
          <div class="bar">
            <el-select v-model="flowQuery.skuId" placeholder="全部物料" clearable style="width: 180px" @change="loadFlows">
              <el-option v-for="s in skus" :key="s.id" :label="s.skuName" :value="s.id" />
            </el-select>
            <el-select v-model="flowQuery.bizType" placeholder="全部类型" clearable style="width: 150px" @change="loadFlows">
              <el-option v-for="(v, k) in BIZ_TYPE" :key="k" :label="v" :value="k" />
            </el-select>
          </div>
          <el-table :data="flows" v-loading="flowLoading" size="small">
            <el-table-column prop="flowNo" label="流水号" width="140">
              <template #default="{ row }"><span class="mono">{{ row.flowNo }}</span></template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.qty > 0 ? 'success' : 'warning'">{{ BIZ_TYPE[row.bizType] || row.bizType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="变动量" width="110" align="right">
              <template #default="{ row }">
                <span class="num" :style="{ color: Number(row.qty) > 0 ? '#0f8a5f' : '#c0392b', fontWeight: 600 }">
                  {{ Number(row.qty) > 0 ? '+' : '' }}{{ qty(row.qty) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="变动前" width="100" align="right">
              <template #default="{ row }"><span class="num muted">{{ qty(row.beforeQty) }}</span></template>
            </el-table-column>
            <el-table-column label="变动后" width="100" align="right">
              <template #default="{ row }"><span class="num">{{ qty(row.afterQty) }}</span></template>
            </el-table-column>
            <el-table-column prop="bizNo" label="关联单号" width="140">
              <template #default="{ row }"><span class="mono small">{{ row.bizNo || '-' }}</span></template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="150" show-overflow-tooltip />
            <el-table-column prop="operator" label="操作人" width="90" />
            <el-table-column label="时间" width="140">
              <template #default="{ row }"><span class="muted small">{{ dateStr(row.createdAt) }}</span></template>
            </el-table-column>
          </el-table>
          <div style="display: flex; justify-content: flex-end; margin-top: 12px">
            <el-pagination
              v-model:current-page="flowQuery.page"
              :page-size="flowQuery.size"
              :total="flowTotal"
              layout="total, prev, pager, next"
              @current-change="loadFlows"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="业务汇总" name="summary">
          <el-table :data="summary" size="small">
            <el-table-column label="业务类型" width="160">
              <template #default="{ row }">{{ BIZ_TYPE[row.bizType] || row.bizType }}</template>
            </el-table-column>
            <el-table-column label="累计变动量（吨）" align="right">
              <template #default="{ row }">
                <span class="num" :style="{ color: Number(row.qty) > 0 ? '#0f8a5f' : '#c0392b', fontWeight: 600 }">
                  {{ qty(row.qty) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
          <p class="muted small" style="margin-top: 12px">
            入库为正、出库为负；划拨与回拨在可售量与活动量之间平移，不影响物理总量。
          </p>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="showOp" :title="opTitle" width="460px">
      <el-form :model="opForm" label-width="96px">
        <el-form-item label="物料" v-if="opType !== 'alloc' && opType !== 'return'">
          <el-select v-model="opForm.skuId" filterable style="width: 100%">
            <el-option v-for="s in skus" :key="s.id" :label="s.skuName + '（可售 ' + qtyShort(s.saleableQty) + '）'" :value="s.id" />
          </el-select>
        </el-form-item>

        <template v-if="opType === 'alloc' || opType === 'return'">
          <el-form-item label="活动">
            <el-select v-model="opForm.activityId" style="width: 100%" @change="loadActSkus">
              <el-option v-for="a in activities" :key="a.id" :label="a.activityName" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="活动 SKU">
            <el-select v-model="opForm.seckillSkuId" style="width: 100%">
              <el-option
                v-for="s in actSkus"
                :key="s.id"
                :label="s.skuName + '（活动池 ' + qtyShort(s.activityStock) + '）'"
                :value="s.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="物料">
            <span>{{ currentActSkuName || '-' }}</span>
          </el-form-item>
        </template>

        <el-form-item :label="opType === 'inbound' ? '入库吨数' : '划拨吨数'">
          <el-input-number v-model="opForm.qty" :min="0.001" :precision="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="关联单号">
          <el-input v-model="opForm.bizNo" placeholder="采购单号 / 活动编号，可留空" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="opForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showOp = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitOp">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'
import { BIZ_TYPE, qty, qtyShort, dateStr } from '@xinji/shared'

const tab = ref('ledger')
const skus = ref([])
const flows = ref([])
const summary = ref([])
const activities = ref([])
const actSkus = ref([])
const loading = ref(false)
const flowLoading = ref(false)
const flowTotal = ref(0)
const showOp = ref(false)
const saving = ref(false)
const opType = ref('inbound')

const flowQuery = reactive({ skuId: null, bizType: '', page: 1, size: 20 })
const opForm = reactive({ skuId: null, qty: 100, activityId: null, seckillSkuId: null, bizNo: '', remark: '' })

const opTitle = computed(() => ({ alloc: '活动划拨', return: '活动回拨', inbound: '采购入库' }[opType.value]))

const currentActSkuName = computed(() => {
  const s = actSkus.value.find((x) => x.id === opForm.seckillSkuId)
  return s ? s.skuName : ''
})

async function loadSkus() {
  loading.value = true
  try {
    skus.value = (await api.admin.product.list()) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadFlows() {
  flowLoading.value = true
  try {
    const data = await api.admin.stock.flows({
      skuId: flowQuery.skuId || undefined,
      bizType: flowQuery.bizType || undefined,
      page: flowQuery.page,
      size: flowQuery.size
    })
    flows.value = data.records || data.list || []
    flowTotal.value = data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    flowLoading.value = false
  }
}

async function loadSummary() {
  try {
    summary.value = (await api.admin.stock.summary()) || []
  } catch (e) { /* ignore */ }
}

function onTab(name) {
  if (name === 'flow' && !flows.value.length) loadFlows()
  if (name === 'summary') loadSummary()
}

function open(type) {
  opType.value = type
  Object.assign(opForm, {
    skuId: skus.value[0]?.id || null,
    qty: type === 'inbound' ? 100 : 50,
    activityId: null,
    seckillSkuId: null,
    bizNo: '',
    remark: ''
  })
  actSkus.value = []
  if (type === 'alloc' || type === 'return') loadActivities()
  showOp.value = true
}

async function loadActivities() {
  try {
    activities.value = (await api.admin.seckill.activities()) || []
    if (activities.value.length && !opForm.activityId) {
      opForm.activityId = activities.value[0].id
      loadActSkus()
    }
  } catch (e) { /* ignore */ }
}

async function loadActSkus() {
  actSkus.value = []
  opForm.seckillSkuId = null
  if (!opForm.activityId) return
  try {
    actSkus.value = (await api.admin.seckill.activitySkus(opForm.activityId)) || []
    if (actSkus.value.length) {
      opForm.seckillSkuId = actSkus.value[0].id
      opForm.skuId = actSkus.value[0].skuId
    }
  } catch (e) { /* ignore */ }
}

async function submitOp() {
  saving.value = true
  try {
    if (opType.value === 'inbound') {
      if (!opForm.skuId) {
        ElMessage.warning('请选择物料')
        saving.value = false
        return
      }
      await api.admin.stock.inbound({
        skuId: opForm.skuId,
        qty: opForm.qty,
        bizNo: opForm.bizNo || 'MANUAL',
        remark: opForm.remark || '手工入库'
      })
    } else {
      if (!opForm.seckillSkuId) {
        ElMessage.warning('请选择活动 SKU')
        saving.value = false
        return
      }
      const payload = {
        skuId: opForm.skuId,
        qty: opForm.qty,
        seckillSkuId: opForm.seckillSkuId,
        bizNo: opForm.bizNo,
        remark: opForm.remark
      }
      if (opType.value === 'alloc') await api.admin.stock.alloc(payload)
      else await api.admin.stock.back(payload)
    }
    ElMessage.success('操作成功，库存池已更新')
    showOp.value = false
    loadSkus()
    loadSummary()
    flows.value = []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadSkus()
  loadSummary()
})
</script>
