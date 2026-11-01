<template>
  <div>
    <div class="page-card">
      <div class="bar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="reload">
          <el-option v-for="(v, k) in ORDER_STATUS" :key="k" :label="v.text" :value="k" />
        </el-select>
        <el-select v-model="query.customerId" placeholder="全部客户" clearable filterable style="width: 190px" @change="reload">
          <el-option v-for="c in customers" :key="c.id" :label="c.customerName" :value="c.id" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="订单号 / 客户名" clearable style="width: 200px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer"></div>
        <span class="muted small">共 {{ total }} 单</span>
      </div>

      <el-table :data="list" v-loading="loading" size="small" empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" width="152">
          <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="76">
          <template #default="{ row }">
            <el-tag v-if="row.orderType === 'SECKILL'" type="danger" size="small">抢购</el-tag>
            <span v-else class="muted small">常规</span>
          </template>
        </el-table-column>
        <el-table-column label="吨数" width="96" align="right">
          <template #default="{ row }"><span class="num">{{ qtyShort(row.totalQty) }}</span></template>
        </el-table-column>
        <el-table-column label="应付金额" width="122" align="right">
          <template #default="{ row }"><span class="money">¥{{ money(row.payAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="配送" width="86">
          <template #default="{ row }">
            <span class="small">{{ row.deliveryType === 'PICKUP' ? '自提' : '配送' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small">{{ label(ORDER_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="136">
          <template #default="{ row }"><span class="muted small">{{ dateStr(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING_AUDIT'" link type="primary" size="small" @click="audit(row)">审核</el-button>
            <el-button v-if="row.status === 'AUDITED'" link type="primary" size="small" @click="openDispatch(row)">派车</el-button>
            <el-button v-if="['DISPATCHED', 'AUDITED'].includes(row.status)" link type="warning" size="small" @click="ship(row)">出厂</el-button>
            <el-button v-if="row.status === 'SHIPPED'" link type="success" size="small" @click="sign(row)">签收</el-button>
            <el-button v-if="['PENDING_AUDIT', 'AUDITED'].includes(row.status)" link type="danger" size="small" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 14px">
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </div>

    <el-drawer v-model="detailShow" title="订单详情" size="620px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号"><span class="mono">{{ detail.order.orderNo }}</span></el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="tagType(detail.order.status)" size="small">{{ label(ORDER_STATUS, detail.order.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客户" :span="2">{{ detail.order.customerName }}</el-descriptions-item>
          <el-descriptions-item label="收货人">{{ detail.order.receiver || '-' }}</el-descriptions-item>
          <el-descriptions-item label="期望到货">{{ detail.order.expectedDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ detail.order.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ dateStr(detail.order.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="配送方式">{{ detail.order.deliveryType === 'PICKUP' ? '客户自提' : '矿区配送' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.order.remark" label="备注" :span="2">{{ detail.order.remark }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title" style="margin: 20px 0 10px">物料明细</div>
        <el-table :data="detail.items || []" size="small" border>
          <el-table-column prop="skuName" label="物料" min-width="130" />
          <el-table-column label="吨数" width="90" align="right">
            <template #default="{ row }"><span class="num">{{ qty(row.qty) }}</span></template>
          </el-table-column>
          <el-table-column label="单价" width="90" align="right">
            <template #default="{ row }"><span class="num">{{ money(row.price) }}</span></template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }"><span class="money">{{ money(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column label="结算吨数" width="100" align="right">
            <template #default="{ row }">
              <span class="num" :style="{ color: Number(row.settleQty) > 0 ? '#0f8a5f' : '#8b95a1' }">
                {{ Number(row.settleQty) > 0 ? qty(row.settleQty) : '待过磅' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="结算金额" width="110" align="right">
            <template #default="{ row }"><span class="num">{{ Number(row.settleAmount) > 0 ? money(row.settleAmount) : '-' }}</span></template>
          </el-table-column>
        </el-table>

        <div class="sum-row">
          <span class="muted">商品金额 ¥{{ money(detail.order.totalAmount) }}</span>
          <span class="muted">运费 ¥{{ money(detail.order.freight) }}</span>
          <span class="muted">优惠 -¥{{ money(detail.order.discountAmount) }}</span>
          <b class="money" style="font-size: 16px; color: #c0392b">应付 ¥{{ money(detail.order.payAmount) }}</b>
        </div>

        <div class="acts" v-if="detail.order.status === 'PENDING_AUDIT' || detail.order.status === 'AUDITED'">
          <el-button v-if="detail.order.status === 'PENDING_AUDIT'" type="primary" @click="audit(detail.order, true)">审核通过</el-button>
          <el-button v-if="detail.order.status === 'AUDITED'" type="primary" @click="openDispatch(detail.order)">安排派车</el-button>
          <el-button type="danger" plain @click="cancel(detail.order, true)">取消订单</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="dispatchShow" title="安排派车" width="480px">
      <el-form :model="dispatchForm" label-width="88px" size="default">
        <el-form-item label="订单号"><span class="mono">{{ dispatchForm.orderNo }}</span></el-form-item>
        <el-form-item label="车辆">
          <el-select v-model="dispatchForm.vehicleId" placeholder="选择车辆" filterable style="width: 100%">
            <el-option v-for="v in vehicles" :key="v.id" :label="v.plateNo + ' · ' + v.loadCapacity + '吨'" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="司机">
          <el-select v-model="dispatchForm.driverId" placeholder="选择司机" filterable style="width: 100%">
            <el-option v-for="d in drivers" :key="d.id" :label="d.driverName + ' · ' + d.phone" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划吨数">
          <el-input-number v-model="dispatchForm.planQty" :min="1" :precision="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划发车">
          <el-date-picker
            v-model="dispatchForm.planTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dispatchForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchShow = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDispatch">确认派车</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'
import { ORDER_STATUS, label, money, qty, qtyShort, dateStr } from '@xinji/shared'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const customers = ref([])
const vehicles = ref([])
const drivers = ref([])
const detail = ref(null)
const detailShow = ref(false)
const dispatchShow = ref(false)
const saving = ref(false)

const query = reactive({ status: '', customerId: null, keyword: '', page: 1, size: 10 })

const dispatchForm = reactive({
  orderNo: '',
  vehicleId: null,
  driverId: null,
  planQty: 30,
  planTime: '',
  remark: ''
})

function tagType(st) {
  const t = (ORDER_STATUS[st] || {}).type
  return t === 'primary' ? '' : t
}

async function load() {
  loading.value = true
  try {
    const data = await api.admin.order.list({
      status: query.status || undefined,
      customerId: query.customerId || undefined,
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size
    })
    list.value = data.records || data.list || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  load()
}

function reset() {
  query.status = ''
  query.customerId = null
  query.keyword = ''
  reload()
}

async function openDetail(row) {
  detailShow.value = true
  detail.value = null
  try {
    detail.value = await api.admin.order.detail(row.orderNo)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function audit(row, fromDrawer) {
  try {
    await ElMessageBox.confirm(
      `审核通过后系统会冻结 ${qtyShort(row.totalQty)} 吨库存，确定继续？`,
      '审核订单 ' + row.orderNo,
      { type: 'warning', confirmButtonText: '审核通过' }
    )
    await api.admin.order.audit(row.orderNo)
    ElMessage.success('审核通过，库存已冻结')
    if (fromDrawer) detailShow.value = false
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

function openDispatch(row) {
  dispatchForm.orderNo = row.orderNo
  dispatchForm.planQty = Number(row.totalQty)
  dispatchForm.vehicleId = null
  dispatchForm.driverId = null
  dispatchForm.remark = ''
  const t = new Date(Date.now() + 3600000)
  const p = (n) => String(n).padStart(2, '0')
  dispatchForm.planTime = `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())} ${p(t.getHours())}:00:00`
  dispatchShow.value = true
}

async function submitDispatch() {
  if (!dispatchForm.vehicleId || !dispatchForm.driverId) {
    ElMessage.warning('请选择车辆和司机')
    return
  }
  saving.value = true
  try {
    await api.admin.dispatch.createOrder({ ...dispatchForm })
    ElMessage.success('派车成功，订单进入已派车状态')
    dispatchShow.value = false
    detailShow.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function ship(row) {
  try {
    await ElMessageBox.confirm(
      '出厂会核销冻结库存，物理总库存将减少。请确认已完成过磅出票。',
      '确认出厂 ' + row.orderNo,
      { type: 'warning' }
    )
    await api.admin.order.ship(row.orderNo)
    ElMessage.success('已出厂')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

async function sign(row) {
  try {
    await api.admin.order.sign(row.orderNo)
    ElMessage.success('已签收')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function cancel(row, fromDrawer) {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消订单 ' + row.orderNo, {
      inputValue: '客户变更需求',
      confirmButtonText: '确认取消'
    })
    await api.admin.order.cancel(row.orderNo, value || '后台取消')
    ElMessage.success('已取消，冻结库存已释放')
    if (fromDrawer) detailShow.value = false
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

onMounted(async () => {
  load()
  try {
    customers.value = (await api.admin.customer.options()) || []
    vehicles.value = (await api.admin.dispatch.vehicles()) || []
    drivers.value = (await api.admin.dispatch.drivers()) || []
  } catch (e) {
    /* 下拉数据失败不影响主表 */
  }
})
</script>

<style scoped>
.sum-row {
  display: flex; gap: 18px; align-items: center; justify-content: flex-end;
  padding: 14px 0; border-top: 1px dashed var(--el-border-color); margin-top: 14px;
}
.acts { display: flex; gap: 10px; margin-top: 16px; }
</style>
