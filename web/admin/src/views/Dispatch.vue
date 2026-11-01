<template>
  <div>
    <div class="page-card">
      <el-tabs v-model="tab" @tab-change="onTab">
        <el-tab-pane label="派车单" name="orders">
          <div class="bar">
            <el-select v-model="orderQuery.status" placeholder="全部状态" clearable style="width: 140px" @change="loadOrders">
              <el-option v-for="(v, k) in DISPATCH_STATUS" :key="k" :label="v.text" :value="k" />
            </el-select>
            <el-button type="primary" @click="loadOrders">查询</el-button>
            <div class="spacer"></div>
            <el-button type="primary" @click="openCreate">新建派车单</el-button>
          </div>
          <el-table :data="orders" v-loading="loading" size="small" empty-text="暂无派车单">
            <el-table-column prop="dispatchNo" label="派车单号" width="146">
              <template #default="{ row }"><span class="mono">{{ row.dispatchNo }}</span></template>
            </el-table-column>
            <el-table-column prop="orderNo" label="订单号" width="146">
              <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
            </el-table-column>
            <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
            <el-table-column prop="plateNo" label="车牌" width="104" />
            <el-table-column prop="driverName" label="司机" width="88" />
            <el-table-column label="计划吨数" width="100" align="right">
              <template #default="{ row }"><span class="num">{{ qtyShort(row.planQty) }}</span></template>
            </el-table-column>
            <el-table-column label="计划发车" width="140">
              <template #default="{ row }"><span class="muted small">{{ dateStr(row.planTime) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <el-tag :type="statusType(DISPATCH_STATUS, row.status)" size="small">{{ label(DISPATCH_STATUS, row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="loading1(row)">开始装车</el-button>
                <el-button v-if="!['DONE', 'CANCELLED'].includes(row.status)" link type="danger" size="small" @click="cancel(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="车辆档案" name="vehicles">
          <div class="bar">
            <el-alert
              v-if="insuranceWarnings.length"
              :title="insuranceWarnings.length + ' 台车保险即将到期或已过期'"
              type="warning"
              :closable="false"
              show-icon
              style="flex: 1"
            />
            <div class="spacer"></div>
            <el-button type="primary" @click="openVehicle">新增车辆</el-button>
          </div>
          <el-table :data="vehicles" size="small">
            <el-table-column prop="plateNo" label="车牌号" width="110" />
            <el-table-column prop="vehicleType" label="车型" width="110" />
            <el-table-column label="核定载重" width="110" align="right">
              <template #default="{ row }"><span class="num">{{ qtyShort(row.loadCapacity) }} 吨</span></template>
            </el-table-column>
            <el-table-column prop="ownerName" label="车主" width="100" />
            <el-table-column prop="ownerPhone" label="电话" width="130" />
            <el-table-column label="保险到期" width="130">
              <template #default="{ row }">
                <span :style="{ color: isExpiring(row.insuranceExpire) ? '#c0392b' : '#141a20' }">
                  {{ dayStr(row.insuranceExpire) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '可用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="toggleVehicle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="司机档案" name="drivers">
          <div class="bar">
            <div class="spacer"></div>
            <el-button type="primary" @click="openDriver">新增司机</el-button>
          </div>
          <el-table :data="drivers" size="small">
            <el-table-column prop="driverName" label="姓名" width="110" />
            <el-table-column prop="phone" label="联系电话" width="140" />
            <el-table-column prop="idCard" label="身份证（脱敏）" min-width="160">
              <template #default="{ row }"><span class="mono small">{{ row.idCard }}</span></template>
            </el-table-column>
            <el-table-column prop="licenseNo" label="驾驶证号" min-width="180">
              <template #default="{ row }"><span class="mono small">{{ row.licenseNo }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '可用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="toggleDriver(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="调度看板" name="board">
          <div class="board">
            <div class="col" v-for="col in boardCols" :key="col.k">
              <div class="col-h">
                <span>{{ col.n }}</span>
                <b>{{ (board[col.k] || []).length }}</b>
              </div>
              <div class="col-b">
                <div class="card-item" v-for="d in board[col.k] || []" :key="d.id">
                  <b>{{ d.plateNo }} · {{ d.driverName }}</b>
                  <small>{{ d.orderNo }}</small>
                  <div class="ci-ft">
                    <span>{{ qtyShort(d.planQty) }} 吨</span>
                    <span class="muted">{{ dateStr(d.planTime) }}</span>
                  </div>
                </div>
                <div v-if="!(board[col.k] || []).length" class="muted small" style="padding: 14px; text-align: center">空</div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="showCreate" title="新建派车单" width="480px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="订单号">
          <el-select v-model="form.orderNo" filterable allow-create placeholder="选择或输入订单号" style="width: 100%">
            <el-option v-for="o in auditedOrders" :key="o.orderNo" :label="o.orderNo + ' · ' + o.customerName" :value="o.orderNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="车辆">
          <el-select v-model="form.vehicleId" style="width: 100%" @change="onVehicle">
            <el-option v-for="v in vehicles.filter(x => x.status === 1)" :key="v.id" :label="v.plateNo + ' · ' + qtyShort(v.loadCapacity) + '吨'" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="司机">
          <el-select v-model="form.driverId" style="width: 100%" @change="onDriver">
            <el-option v-for="d in drivers.filter(x => x.status === 1)" :key="d.id" :label="d.driverName + ' · ' + d.phone" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划吨数">
          <el-input-number v-model="form.planQty" :min="1" :precision="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划发车">
          <el-date-picker v-model="form.planTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">确认派车</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showVehicle" title="新增车辆" width="440px">
      <el-form :model="vForm" label-width="92px">
        <el-form-item label="车牌号"><el-input v-model="vForm.plateNo" /></el-form-item>
        <el-form-item label="车型">
          <el-select v-model="vForm.vehicleType" style="width: 100%" allow-create filterable>
            <el-option label="自卸车" value="自卸车" />
            <el-option label="半挂牵引" value="半挂牵引" />
            <el-option label="罐车" value="罐车" />
          </el-select>
        </el-form-item>
        <el-form-item label="核定载重">
          <el-input-number v-model="vForm.loadCapacity" :min="0" :precision="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="车主"><el-input v-model="vForm.ownerName" /></el-form-item>
        <el-form-item label="车主电话"><el-input v-model="vForm.ownerPhone" /></el-form-item>
        <el-form-item label="保险到期">
          <el-date-picker v-model="vForm.insuranceExpire" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showVehicle = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveVehicle">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDriver" title="新增司机" width="420px">
      <el-form :model="dForm" label-width="92px">
        <el-form-item label="姓名"><el-input v-model="dForm.driverName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="dForm.phone" /></el-form-item>
        <el-form-item label="身份证号"><el-input v-model="dForm.idCard" /></el-form-item>
        <el-form-item label="驾驶证号"><el-input v-model="dForm.licenseNo" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDriver = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDriver">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'
import { DISPATCH_STATUS, label, statusType, qtyShort, dateStr, dayStr } from '@xinji/shared'

const tab = ref('orders')
const orders = ref([])
const vehicles = ref([])
const drivers = ref([])
const board = ref({})
const auditedOrders = ref([])
const insuranceWarnings = ref([])
const loading = ref(false)
const saving = ref(false)
const showCreate = ref(false)
const showVehicle = ref(false)
const showDriver = ref(false)

const orderQuery = reactive({ status: '' })
const form = reactive({ orderNo: '', vehicleId: null, driverId: null, planQty: 30, planTime: '', remark: '' })
const vForm = reactive({ plateNo: '', vehicleType: '自卸车', loadCapacity: 35, ownerName: '', ownerPhone: '', insuranceExpire: '' })
const dForm = reactive({ driverName: '', phone: '', idCard: '', licenseNo: '' })

const boardCols = [
  { k: 'PENDING', n: '待发车' },
  { k: 'LOADING', n: '装车中' },
  { k: 'SHIPPED', n: '已出厂' },
  { k: 'DONE', n: '已签收' }
]

function isExpiring(d) {
  if (!d) return false
  const diff = new Date(String(d).replace(/-/g, '/')).getTime() - Date.now()
  return diff < 30 * 86400000
}

async function loadOrders() {
  loading.value = true
  try {
    orders.value = (await api.admin.dispatch.orders({ status: orderQuery.status || undefined })) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadBase() {
  try {
    vehicles.value = (await api.admin.dispatch.vehicles()) || []
    drivers.value = (await api.admin.dispatch.drivers()) || []
    insuranceWarnings.value = (await api.admin.dispatch.insuranceWarnings()) || []
  } catch (e) { /* ignore */ }
}

async function loadBoard() {
  try {
    board.value = (await api.admin.dispatch.board()) || {}
  } catch (e) {
    board.value = {}
  }
}

function onTab(name) {
  if (name === 'board') loadBoard()
  if (name === 'vehicles' || name === 'drivers') loadBase()
}

async function openCreate() {
  Object.assign(form, { orderNo: '', vehicleId: null, driverId: null, planQty: 30, planTime: '', remark: '' })
  showCreate.value = true
  try {
    const data = await api.admin.order.list({ status: 'AUDITED', page: 1, size: 50 })
    auditedOrders.value = data.records || data.list || []
  } catch (e) {
    auditedOrders.value = []
  }
}

function onVehicle(id) {
  const v = vehicles.value.find((x) => x.id === id)
  if (v) form.planQty = Number(v.loadCapacity)
}

function onDriver(id) {
  const d = drivers.value.find((x) => x.id === id)
  if (d) form.remark = d.driverName + ' ' + d.phone
}

async function create() {
  if (!form.orderNo || !form.vehicleId || !form.driverId) {
    ElMessage.warning('订单、车辆、司机都要选')
    return
  }
  saving.value = true
  try {
    await api.admin.dispatch.createOrder({ ...form })
    ElMessage.success('派车成功，订单状态已更新')
    showCreate.value = false
    loadOrders()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function loading1(row) {
  try {
    await api.admin.dispatch.loading(row.id)
    ElMessage.success('已开始装车')
    loadOrders()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm('确定取消这张派车单？', '取消派车', { type: 'warning' })
    await api.admin.dispatch.cancelOrder(row.id)
    ElMessage.success('已取消')
    loadOrders()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

function openVehicle() {
  Object.assign(vForm, { plateNo: '', vehicleType: '自卸车', loadCapacity: 35, ownerName: '', ownerPhone: '', insuranceExpire: '' })
  showVehicle.value = true
}

async function saveVehicle() {
  if (!vForm.plateNo) {
    ElMessage.warning('请填写车牌号')
    return
  }
  saving.value = true
  try {
    await api.admin.dispatch.createVehicle({ ...vForm })
    ElMessage.success('车辆已新增')
    showVehicle.value = false
    loadBase()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function toggleVehicle(row) {
  try {
    await api.admin.dispatch.vehicleStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success('已更新')
    loadBase()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openDriver() {
  Object.assign(dForm, { driverName: '', phone: '', idCard: '', licenseNo: '' })
  showDriver.value = true
}

async function saveDriver() {
  if (!dForm.driverName || !dForm.phone) {
    ElMessage.warning('请填写姓名和电话')
    return
  }
  saving.value = true
  try {
    await api.admin.dispatch.createDriver({ ...dForm })
    ElMessage.success('司机已新增')
    showDriver.value = false
    loadBase()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function toggleDriver(row) {
  try {
    await api.admin.dispatch.driverStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success('已更新')
    loadBase()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => {
  loadOrders()
  loadBase()
})
</script>

<style scoped>
.board { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.col { background: #fafbfc; border: 1px solid var(--el-border-color-lighter); border-radius: 9px; }
.col-h {
  padding: 10px 12px; font-size: 12.5px; font-weight: 500; border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex; justify-content: space-between;
}
.col-h b { color: var(--el-text-color-secondary); font-weight: 600; }
.col-b { padding: 10px; min-height: 200px; display: flex; flex-direction: column; gap: 8px; }
.card-item { background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 7px; padding: 9px 11px; }
.card-item b { display: block; font-size: 12.5px; font-weight: 500; }
.card-item small { display: block; font-size: 11px; color: var(--el-text-color-secondary); font-family: ui-monospace, monospace; }
.ci-ft { display: flex; justify-content: space-between; font-size: 11.5px; margin-top: 5px; }
</style>
