<template>
  <div>
    <div class="page-card">
      <div class="bar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option v-for="(v, k) in PRODUCTION_STATUS" :key="k" :label="v.text" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button type="primary" @click="openCreate">新建工单</el-button>
      </div>

      <el-table :data="list" v-loading="loading" size="small" empty-text="暂无生产工单">
        <el-table-column prop="productionNo" label="工单号" width="146">
          <template #default="{ row }"><span class="mono">{{ row.productionNo }}</span></template>
        </el-table-column>
        <el-table-column prop="lineName" label="产线" width="120" />
        <el-table-column prop="shift" label="班次" width="80" />
        <el-table-column prop="skuName" label="产出物料" min-width="130" />
        <el-table-column label="计划产量" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ qtyShort(row.planQty) }}</span></template>
        </el-table-column>
        <el-table-column label="实际产量" width="100" align="right">
          <template #default="{ row }"><span class="num" style="color: #0f8a5f; font-weight: 600">{{ qtyShort(row.outputQty) }}</span></template>
        </el-table-column>
        <el-table-column label="投料量" width="96" align="right">
          <template #default="{ row }"><span class="num muted">{{ qtyShort(row.feedQty) }}</span></template>
        </el-table-column>
        <el-table-column label="成品率" width="90" align="right">
          <template #default="{ row }"><span class="num">{{ rate(row) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="statusType(PRODUCTION_STATUS, row.status)" size="small">{{ label(PRODUCTION_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="班组长" width="90" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="start(row)">开工</el-button>
            <el-button v-if="row.status === 'RUNNING'" link type="success" size="small" @click="openFinish(row)">完工报产</el-button>
            <el-button v-if="!['DONE', 'CANCELLED'].includes(row.status)" link type="danger" size="small" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card">
      <div class="section-title">产线看板</div>
      <div class="board">
        <div class="col" v-for="col in cols" :key="col.k">
          <div class="col-h"><span>{{ col.n }}</span><b>{{ (board[col.k] || []).length }}</b></div>
          <div class="col-b">
            <div class="item" v-for="o in board[col.k] || []" :key="o.id">
              <b>{{ o.skuName }}</b>
              <small>{{ o.lineName }} · {{ o.shift }}</small>
              <div class="ft">
                <span>计划 {{ qtyShort(o.planQty) }}</span>
                <span style="color: #0f8a5f">产出 {{ qtyShort(o.outputQty) }}</span>
              </div>
            </div>
            <div v-if="!(board[col.k] || []).length" class="muted small" style="padding: 14px; text-align: center">空</div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showCreate" title="新建生产工单" width="480px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="产线">
          <el-select v-model="form.lineName" style="width: 100%" allow-create filterable>
            <el-option label="一号破碎线" value="一号破碎线" />
            <el-option label="二号制砂线" value="二号制砂线" />
          </el-select>
        </el-form-item>
        <el-form-item label="班次">
          <el-select v-model="form.shift" style="width: 100%">
            <el-option label="白班" value="白班" />
            <el-option label="中班" value="中班" />
            <el-option label="夜班" value="夜班" />
          </el-select>
        </el-form-item>
        <el-form-item label="产出物料">
          <el-select v-model="form.skuId" filterable style="width: 100%">
            <el-option v-for="s in skus" :key="s.id" :label="s.skuName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划产量">
              <el-input-number v-model="form.planQty" :min="1" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划投料">
              <el-input-number v-model="form.feedQty" :min="0" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="班组长"><el-input v-model="form.operator" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showFinish" title="完工报产" width="440px">
      <el-form label-width="96px">
        <el-form-item label="工单"><span class="mono">{{ current?.productionNo }}</span></el-form-item>
        <el-form-item label="产出物料">{{ current?.skuName }}</el-form-item>
        <el-form-item label="实际产量">
          <el-input-number v-model="finishForm.outputQty" :min="0.001" :precision="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="实际投料">
          <el-input-number v-model="finishForm.feedQty" :min="0" :precision="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="finishForm.remark" /></el-form-item>
        <el-alert
          title="完工后实际产量会直接进入销售可售池，并写一条 INBOUND 流水"
          type="info"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="showFinish = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="finish">确认完工</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'
import { PRODUCTION_STATUS, label, statusType, qtyShort } from '@xinji/shared'

const list = ref([])
const board = ref({})
const skus = ref([])
const loading = ref(false)
const saving = ref(false)
const showCreate = ref(false)
const showFinish = ref(false)
const current = ref(null)

const query = reactive({ status: '' })
const form = reactive({ lineName: '一号破碎线', shift: '白班', skuId: null, planQty: 500, feedQty: 550, operator: '', remark: '' })
const finishForm = reactive({ outputQty: 0, feedQty: 0, remark: '' })

const cols = [
  { k: 'PENDING', n: '待开始' },
  { k: 'RUNNING', n: '生产中' },
  { k: 'DONE', n: '已完工' }
]

function rate(row) {
  const plan = Number(row.planQty || 0)
  const out = Number(row.outputQty || 0)
  if (!plan) return '-'
  return ((out / plan) * 100).toFixed(1) + '%'
}

async function load() {
  loading.value = true
  try {
    list.value = (await api.admin.production.orders({ status: query.status || undefined })) || []
    board.value = (await api.admin.production.board()) || {}
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { lineName: '一号破碎线', shift: '白班', skuId: skus.value[0]?.id || null, planQty: 500, feedQty: 550, operator: '', remark: '' })
  showCreate.value = true
}

async function create() {
  if (!form.skuId) {
    ElMessage.warning('请选择产出物料')
    return
  }
  saving.value = true
  try {
    await api.admin.production.create({ ...form })
    ElMessage.success('工单已创建')
    showCreate.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function start(row) {
  try {
    await api.admin.production.start(row.id)
    ElMessage.success('已开工')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openFinish(row) {
  current.value = row
  finishForm.outputQty = Number(row.planQty)
  finishForm.feedQty = Number(row.feedQty || row.planQty)
  finishForm.remark = ''
  showFinish.value = true
}

async function finish() {
  saving.value = true
  try {
    await api.admin.production.finish(current.value.id, { ...finishForm })
    ElMessage.success('已完工，成品已入库')
    showFinish.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm('确定取消这张工单？', '取消工单', { type: 'warning' })
    await api.admin.production.cancel(row.id)
    ElMessage.success('已取消')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

onMounted(async () => {
  load()
  try {
    skus.value = (await api.admin.product.list()) || []
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
.board { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.col { background: #fafbfc; border: 1px solid var(--el-border-color-lighter); border-radius: 9px; }
.col-h {
  padding: 10px 12px; font-size: 12.5px; font-weight: 500;
  border-bottom: 1px solid var(--el-border-color-lighter); display: flex; justify-content: space-between;
}
.col-b { padding: 10px; min-height: 140px; display: flex; flex-direction: column; gap: 8px; }
.item { background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 7px; padding: 9px 11px; }
.item b { display: block; font-size: 12.5px; font-weight: 500; }
.item small { display: block; font-size: 11px; color: var(--el-text-color-secondary); }
.ft { display: flex; justify-content: space-between; font-size: 11.5px; margin-top: 5px; }
</style>
