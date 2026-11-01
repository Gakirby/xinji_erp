<template>
  <div class="page-card">
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="对账批次" name="settle">
        <div class="bar">
          <el-button type="primary" @click="openSettle">生成对账批次</el-button>
          <div class="spacer"></div>
          <span class="muted small">按客户 + 账期聚合已签收订单，确认后自动生成应收单</span>
        </div>
        <el-table :data="settlements" v-loading="loading" size="small" empty-text="暂无对账批次">
          <el-table-column prop="settlementNo" label="批次号" width="146">
            <template #default="{ row }"><span class="mono">{{ row.settlementNo }}</span></template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="170" show-overflow-tooltip />
          <el-table-column label="账期" width="200">
            <template #default="{ row }">
              <span class="small">{{ dayStr(row.periodStart) }} ~ {{ dayStr(row.periodEnd) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="orderCount" label="订单数" width="86" align="right" />
          <el-table-column label="吨数" width="104" align="right">
            <template #default="{ row }"><span class="num">{{ qty(row.totalQty) }}</span></template>
          </el-table-column>
          <el-table-column label="金额" width="128" align="right">
            <template #default="{ row }"><span class="money">¥{{ money(row.totalAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="statusType(SETTLEMENT_STATUS, row.status)" size="small">{{ label(SETTLEMENT_STATUS, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="showItems(row)">明细</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="confirm(row)">确认</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="应收账款" name="receivable">
        <div class="bar">
          <span class="muted small">未收合计 <b style="color: #c0392b; font-size: 15px">¥{{ money(outstanding) }}</b></span>
          <div class="spacer"></div>
          <el-button @click="loadAll">刷新</el-button>
        </div>
        <el-table :data="receivables" size="small" empty-text="暂无应收">
          <el-table-column prop="receivableNo" label="应收单号" width="140">
            <template #default="{ row }"><span class="mono">{{ row.receivableNo }}</span></template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="170" show-overflow-tooltip />
          <el-table-column label="应收金额" width="130" align="right">
            <template #default="{ row }"><span class="num">¥{{ money(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column label="已收" width="130" align="right">
            <template #default="{ row }"><span class="num" style="color: #0f8a5f">¥{{ money(row.receivedAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="未收" width="130" align="right">
            <template #default="{ row }">
              <span class="num" style="color: #c0392b; font-weight: 600">¥{{ money(Number(row.amount) - Number(row.receivedAmount)) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="到期日" width="110">
            <template #default="{ row }"><span class="small">{{ dayStr(row.dueDate) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="statusType(RECEIVABLE_STATUS, row.status)" size="small">{{ label(RECEIVABLE_STATUS, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status !== 'PAID'" link type="primary" size="small" @click="openReceive(row)">收款</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="收款流水" name="payment">
        <div class="kpi-grid" style="margin-bottom: 14px">
          <div class="kpi"><div class="k">本月回款</div><div class="v">¥{{ money(paymentStats.monthAmount || 0) }}</div></div>
          <div class="kpi"><div class="k">回款笔数</div><div class="v">{{ paymentStats.count || 0 }}<span class="u">笔</span></div></div>
        </div>
        <el-table :data="payments" size="small" empty-text="暂无收款记录">
          <el-table-column prop="paymentNo" label="流水号" width="140">
            <template #default="{ row }"><span class="mono">{{ row.paymentNo }}</span></template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="170" show-overflow-tooltip />
          <el-table-column prop="receivableNo" label="应收单" width="140">
            <template #default="{ row }"><span class="mono small">{{ row.receivableNo }}</span></template>
          </el-table-column>
          <el-table-column label="金额" width="128" align="right">
            <template #default="{ row }"><span class="money">¥{{ money(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column label="方式" width="100">
            <template #default="{ row }">{{ PAY_METHOD[row.payMethod] || row.payMethod }}</template>
          </el-table-column>
          <el-table-column prop="operator" label="经办人" width="90" />
          <el-table-column label="收款时间" width="140">
            <template #default="{ row }"><span class="muted small">{{ dateStr(row.payTime) }}</span></template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="发票" name="invoice">
        <div class="bar">
          <el-button type="primary" @click="openInvoice">开具发票</el-button>
          <div class="spacer"></div>
          <span class="muted small">价税合计按 13% 税率拆分，作废需谨慎</span>
        </div>
        <el-table :data="invoices" size="small" empty-text="暂无发票">
          <el-table-column prop="invoiceNo" label="发票号" width="140">
            <template #default="{ row }"><span class="mono">{{ row.invoiceNo }}</span></template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="170" show-overflow-tooltip />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ INVOICE_TYPE[row.invoiceType] || row.invoiceType }}</template>
          </el-table-column>
          <el-table-column label="价税合计" width="128" align="right">
            <template #default="{ row }"><span class="money">¥{{ money(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column label="税额" width="118" align="right">
            <template #default="{ row }"><span class="num muted">¥{{ money(row.taxAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="statusType(INVOICE_STATUS, row.status)" size="small">{{ label(INVOICE_STATUS, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PENDING'" link type="success" size="small" @click="issue(row)">开具</el-button>
              <el-button v-if="row.status !== 'VOID'" link type="danger" size="small" @click="voidInv(row)">作废</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showSettle" title="生成对账批次" width="460px">
      <el-form :model="settleForm" label-width="92px">
        <el-form-item label="客户">
          <el-select v-model="settleForm.customerId" filterable style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.customerName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="账期">
          <el-date-picker
            v-model="settleRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSettle = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createSettle">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showReceive" title="收款登记" width="440px">
      <el-form label-width="92px">
        <el-form-item label="应收单"><span class="mono">{{ current?.receivableNo }}</span></el-form-item>
        <el-form-item label="客户">{{ current?.customerName }}</el-form-item>
        <el-form-item label="未收">
          <b style="color: #c0392b">¥{{ money(Number(current?.amount || 0) - Number(current?.receivedAmount || 0)) }}</b>
        </el-form-item>
        <el-form-item label="本次收款">
          <el-input-number v-model="receiveForm.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款方式">
          <el-select v-model="receiveForm.payMethod" style="width: 100%">
            <el-option v-for="(v, k) in PAY_METHOD" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="receiveForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReceive = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doReceive">确认收款</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showInv" title="开具发票" width="460px">
      <el-form :model="invForm" label-width="96px">
        <el-form-item label="应收单">
          <el-select v-model="invForm.receivableId" filterable style="width: 100%" @change="onReceivable">
            <el-option
              v-for="r in receivables.filter(x => x.status !== 'PAID')"
              :key="r.id"
              :label="r.receivableNo + ' · ' + r.customerName"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="invForm.invoiceType" style="width: 100%">
            <el-option v-for="(v, k) in INVOICE_TYPE" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="invForm.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率">
          <el-input-number v-model="invForm.taxRate" :min="0" :max="1" :step="0.01" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="invForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInv = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createInv">确认开具</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showItemList" title="对账明细" width="620px">
      <el-table :data="items" size="small" border>
        <el-table-column prop="orderNo" label="订单号" width="150">
          <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column label="结算吨数" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ qty(row.settleQty) }}</span></template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }"><span class="money">¥{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="结算日期" min-width="110">
          <template #default="{ row }">{{ dayStr(row.settleDate) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'
import {
  SETTLEMENT_STATUS, RECEIVABLE_STATUS, INVOICE_STATUS, PAY_METHOD, INVOICE_TYPE,
  label, statusType, money, qty, dateStr, dayStr
} from '@xinji/shared'

const tab = ref('settle')
const settlements = ref([])
const receivables = ref([])
const payments = ref([])
const invoices = ref([])
const customers = ref([])
const items = ref([])
const outstanding = ref(0)
const paymentStats = ref({})
const loading = ref(false)
const saving = ref(false)
const showSettle = ref(false)
const showReceive = ref(false)
const showInv = ref(false)
const showItemList = ref(false)
const current = ref(null)
const settleRange = ref([])

const settleForm = reactive({ customerId: null })
const receiveForm = reactive({ amount: 0, payMethod: 'TRANSFER', remark: '' })
const invForm = reactive({ receivableId: null, invoiceType: 'VAT_SPECIAL', amount: 0, taxRate: 0.13, remark: '' })

async function loadAll() {
  loading.value = true
  try {
    const [s, r, p, i, o, ps] = await Promise.all([
      api.admin.finance.settlements().catch(() => []),
      api.admin.finance.receivables().catch(() => []),
      api.admin.finance.payments().catch(() => []),
      api.admin.finance.invoices().catch(() => []),
      api.admin.finance.outstanding().catch(() => 0),
      api.admin.finance.paymentStats().catch(() => ({}))
    ])
    settlements.value = s || []
    receivables.value = r || []
    payments.value = p || []
    invoices.value = i || []
    outstanding.value = o || 0
    paymentStats.value = ps || {}
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function onTab() {
  loadAll()
}

function openSettle() {
  settleForm.customerId = customers.value[0]?.id || null
  const d = new Date()
  const first = new Date(d.getFullYear(), d.getMonth(), 1)
  const p = (x) => String(x).padStart(2, '0')
  settleRange.value = [
    `${first.getFullYear()}-${p(first.getMonth() + 1)}-01`,
    `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
  ]
  showSettle.value = true
}

async function createSettle() {
  if (!settleForm.customerId || !settleRange.value || settleRange.value.length !== 2) {
    ElMessage.warning('请选择客户和账期')
    return
  }
  saving.value = true
  try {
    await api.admin.finance.createSettlement({
      customerId: settleForm.customerId,
      periodStart: settleRange.value[0],
      periodEnd: settleRange.value[1]
    })
    ElMessage.success('对账批次已生成')
    showSettle.value = false
    loadAll()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function showItems(row) {
  showItemList.value = true
  items.value = []
  try {
    items.value = (await api.admin.finance.settlementItems(row.id)) || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function confirm(row) {
  try {
    await ElMessageBox.confirm('确认后会自动生成应收单，确定继续？', '确认对账 ' + row.settlementNo, { type: 'warning' })
    await api.admin.finance.confirmSettlement(row.id)
    ElMessage.success('已确认，应收单已生成')
    loadAll()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

function openReceive(row) {
  current.value = row
  receiveForm.amount = Number(row.amount) - Number(row.receivedAmount)
  receiveForm.payMethod = 'TRANSFER'
  receiveForm.remark = ''
  showReceive.value = true
}

async function doReceive() {
  saving.value = true
  try {
    await api.admin.finance.receiveDetail(current.value.id, { ...receiveForm })
    ElMessage.success('收款已登记')
    showReceive.value = false
    loadAll()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function openInvoice() {
  const r = receivables.value.find((x) => x.status !== 'PAID')
  invForm.receivableId = r ? r.id : null
  invForm.amount = r ? Number(r.amount) : 0
  invForm.invoiceType = 'VAT_SPECIAL'
  invForm.taxRate = 0.13
  invForm.remark = ''
  showInv.value = true
}

function onReceivable(id) {
  const r = receivables.value.find((x) => x.id === id)
  if (r) invForm.amount = Number(r.amount)
}

async function createInv() {
  if (!invForm.receivableId) {
    ElMessage.warning('请选择应收单')
    return
  }
  saving.value = true
  try {
    await api.admin.finance.createInvoice({ ...invForm })
    ElMessage.success('发票已开具')
    showInv.value = false
    loadAll()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function issue(row) {
  try {
    await api.admin.finance.issueInvoice(row.id)
    ElMessage.success('已开具')
    loadAll()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function voidInv(row) {
  try {
    await ElMessageBox.confirm('作废后不可恢复，确定？', '作废发票 ' + row.invoiceNo, { type: 'warning' })
    await api.admin.finance.voidInvoice(row.id)
    ElMessage.success('已作废')
    loadAll()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

onMounted(async () => {
  loadAll()
  try {
    customers.value = (await api.admin.customer.options()) || []
  } catch (e) { /* ignore */ }
})
</script>
