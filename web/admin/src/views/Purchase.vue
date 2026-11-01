<template>
  <div>
    <div class="page-card">
      <div class="bar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option v-for="(v, k) in PURCHASE_STATUS" :key="k" :label="v.text" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button type="primary" @click="openCreate">新建采购单</el-button>
      </div>

      <el-table :data="list" v-loading="loading" size="small" empty-text="暂无采购单">
        <el-table-column prop="poNo" label="采购单号" width="150">
          <template #default="{ row }"><span class="mono">{{ row.poNo }}</span></template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="140" />
        <el-table-column prop="skuName" label="物料" min-width="130" />
        <el-table-column label="计划量" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ qty(row.planQty) }}</span></template>
        </el-table-column>
        <el-table-column label="已到货" width="100" align="right">
          <template #default="{ row }"><span class="num" style="color: #0f8a5f">{{ qty(row.arrivedQty) }}</span></template>
        </el-table-column>
        <el-table-column label="单价" width="92" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.price) }}</span></template>
        </el-table-column>
        <el-table-column label="到货金额" width="120" align="right">
          <template #default="{ row }"><span class="money">¥{{ money(row.arrivedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(PURCHASE_STATUS, row.status)" size="small">{{ label(PURCHASE_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预计到货" width="110">
          <template #default="{ row }"><span class="muted small">{{ dayStr(row.expectedDate) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'DONE'" link type="primary" size="small" @click="openArrive(row)">到货入库</el-button>
            <el-button link size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showCreate" title="新建采购单" width="520px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="供应商"><el-input v-model="form.supplierName" placeholder="如 龙潭采石场" /></el-form-item>
        <el-form-item label="物料">
          <el-select v-model="form.skuId" filterable style="width: 100%">
            <el-option v-for="s in skus" :key="s.id" :label="s.skuName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划量">
              <el-input-number v-model="form.planQty" :min="1" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购单价">
              <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="预计到货">
          <el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showArrive" title="到货入库" width="440px">
      <el-form label-width="92px">
        <el-form-item label="采购单"><span class="mono">{{ current?.poNo }}</span></el-form-item>
        <el-form-item label="物料">{{ current?.skuName }}</el-form-item>
        <el-form-item label="待收">
          {{ qty(Number(current?.planQty || 0) - Number(current?.arrivedQty || 0)) }} 吨
        </el-form-item>
        <el-form-item label="本次到货">
          <el-input-number v-model="arriveQty" :min="0.001" :precision="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="arriveRemark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showArrive = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doArrive">确认入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showInfo" title="采购单详情" width="480px">
      <el-descriptions :column="1" border size="small" v-if="info">
        <el-descriptions-item label="采购单号"><span class="mono">{{ info.poNo }}</span></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ info.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="物料">{{ info.skuName }}</el-descriptions-item>
        <el-descriptions-item label="计划 / 已到">{{ qty(info.planQty) }} / {{ qty(info.arrivedQty) }} 吨</el-descriptions-item>
        <el-descriptions-item label="状态">{{ label(PURCHASE_STATUS, info.status) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ dateStr(info.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'
import { PURCHASE_STATUS, label, statusType, money, qty, dateStr, dayStr } from '@xinji/shared'

const list = ref([])
const skus = ref([])
const loading = ref(false)
const saving = ref(false)
const showCreate = ref(false)
const showArrive = ref(false)
const showInfo = ref(false)
const current = ref(null)
const info = ref(null)
const arriveQty = ref(0)
const arriveRemark = ref('')

const query = reactive({ status: '' })
const form = reactive({ supplierName: '', skuId: null, planQty: 500, price: 0, expectedDate: '' })

async function load() {
  loading.value = true
  try {
    list.value = (await api.admin.purchase.list({ status: query.status || undefined })) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    supplierName: '',
    skuId: skus.value[0]?.id || null,
    planQty: 500,
    price: skus.value[0]?.price || 0,
    expectedDate: ''
  })
  showCreate.value = true
}

async function create() {
  if (!form.supplierName || !form.skuId) {
    ElMessage.warning('请填写供应商和物料')
    return
  }
  saving.value = true
  try {
    await api.admin.purchase.create({ ...form })
    ElMessage.success('采购单已创建')
    showCreate.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function openArrive(row) {
  current.value = row
  arriveQty.value = Number(row.planQty) - Number(row.arrivedQty)
  arriveRemark.value = '到货入库'
  showArrive.value = true
}

async function doArrive() {
  saving.value = true
  try {
    await api.admin.purchase.arrive(current.value.id, { arrivedQty: arriveQty.value, remark: arriveRemark.value })
    ElMessage.success('到货已入库，可售量已增加')
    showArrive.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function showDetail(row) {
  showInfo.value = true
  try {
    info.value = (await api.admin.purchase.detail(row.id)) || row
  } catch (e) {
    info.value = row
  }
}

onMounted(async () => {
  load()
  try {
    skus.value = (await api.admin.product.list()) || []
  } catch (e) { /* ignore */ }
})
</script>
