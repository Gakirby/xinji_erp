<template>
  <div>
    <div class="page-card">
      <div class="bar">
        <el-input v-model="keyword" placeholder="物料编码 / 名称" clearable style="width: 200px" @keyup.enter="load" />
        <el-select v-model="category" placeholder="全部品种" clearable style="width: 130px" @change="load">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button @click="openWarehouse">料仓管理</el-button>
        <el-button type="primary" @click="openCreate">新增物料</el-button>
      </div>

      <el-table :data="filtered" v-loading="loading" size="small" empty-text="暂无物料">
        <el-table-column prop="skuNo" label="编码" width="104">
          <template #default="{ row }"><span class="mono">{{ row.skuNo }}</span></template>
        </el-table-column>
        <el-table-column prop="skuName" label="物料名称" min-width="150" />
        <el-table-column prop="category" label="品种" width="88" />
        <el-table-column prop="spec" label="规格" min-width="170" show-overflow-tooltip />
        <el-table-column label="挂牌价" width="92" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.price) }}</span></template>
        </el-table-column>
        <el-table-column label="可售" width="92" align="right">
          <template #default="{ row }">
            <span class="num" :style="{ color: Number(row.saleableQty) < Number(row.safetyQty) ? '#c0392b' : '#141a20' }">
              {{ qtyShort(row.saleableQty) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="活动划拨" width="92" align="right">
          <template #default="{ row }"><span class="num" style="color: #bd7415">{{ qtyShort(row.activityAllocQty) }}</span></template>
        </el-table-column>
        <el-table-column label="冻结" width="86" align="right">
          <template #default="{ row }"><span class="num" style="color: #2c7499">{{ qtyShort(row.frozenQty) }}</span></template>
        </el-table-column>
        <el-table-column label="总量" width="92" align="right">
          <template #default="{ row }"><span class="num" style="font-weight: 600">{{ qtyShort(row.totalQty) }}</span></template>
        </el-table-column>
        <el-table-column prop="warehouseNo" label="料仓" width="76" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '在售' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="toggle(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showForm" :title="form.id ? '编辑物料' : '新增物料'" width="560px">
      <el-form :model="form" label-width="88px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="物料编码">
              <el-input v-model="form.skuNo" placeholder="留空自动生成" :disabled="!!form.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称">
              <el-input v-model="form.skuName" placeholder="如 石灰石 5-10mm" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="品种">
              <el-select v-model="form.category" style="width: 100%" allow-create filterable>
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="挂牌价">
              <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="规格">
          <el-input v-model="form.spec" placeholder="如 压碎值≤10% · 含泥量≤1%" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="料仓">
              <el-select v-model="form.warehouseId" style="width: 100%">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseNo + ' ' + w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安全库存">
              <el-input-number v-model="form.safetyQty" :min="0" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="期初入库" v-if="!form.id">
          <el-input-number v-model="form.initQty" :min="0" :precision="3" style="width: 100%" />
          <div class="muted small" style="margin-top: 4px">建档时一并入库的吨数，会写一条 INBOUND 流水</div>
        </el-form-item>
        <el-form-item label="色标">
          <el-select v-model="form.imageTag" style="width: 100%">
            <el-option label="石灰石（灰）" value="stone" />
            <el-option label="机制砂（砂黄）" value="sand" />
            <el-option label="铁精粉（深灰）" value="iron" />
            <el-option label="石粉（浅灰）" value="powder" />
            <el-option label="尾矿（褐）" value="tailing" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showWh" title="料仓" width="520px">
      <el-table :data="warehouses" size="small" border>
        <el-table-column prop="warehouseNo" label="编号" width="80" />
        <el-table-column prop="warehouseName" label="名称" min-width="160" />
        <el-table-column label="设计容量" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ qtyShort(row.capacity) }} 吨</span></template>
        </el-table-column>
      </el-table>
      <div class="wh-form">
        <el-input v-model="wh.warehouseNo" placeholder="编号" style="width: 100px" />
        <el-input v-model="wh.warehouseName" placeholder="名称" style="flex: 1" />
        <el-input-number v-model="wh.capacity" :min="0" :precision="0" style="width: 130px" />
        <el-button type="primary" @click="addWh">新增</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'
import { money, qtyShort } from '@xinji/shared'

const list = ref([])
const warehouses = ref([])
const loading = ref(false)
const saving = ref(false)
const showForm = ref(false)
const showWh = ref(false)
const keyword = ref('')
const category = ref('')

const categories = ['石灰石', '机制砂', '铁精粉', '石粉', '尾矿']

const form = reactive({
  id: null, skuNo: '', skuName: '', category: '石灰石', spec: '',
  unit: '吨', price: 0, warehouseId: null, safetyQty: 0, imageTag: 'stone', initQty: 0
})

const wh = reactive({ warehouseNo: '', warehouseName: '', capacity: 0 })

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return list.value.filter((x) => {
    const okK = !k || (x.skuName || '').toLowerCase().includes(k) || (x.skuNo || '').toLowerCase().includes(k)
    const okC = !category.value || x.category === category.value
    return okK && okC
  })
})

async function load() {
  loading.value = true
  try {
    list.value = (await api.admin.product.list()) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadWarehouses() {
  try {
    warehouses.value = (await api.admin.product.warehouses()) || []
  } catch (e) { /* ignore */ }
}

function openCreate() {
  Object.assign(form, {
    id: null, skuNo: '', skuName: '', category: '石灰石', spec: '',
    unit: '吨', price: 0, warehouseId: warehouses.value[0]?.id || null,
    safetyQty: 0, imageTag: 'stone', initQty: 0
  })
  showForm.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, skuNo: row.skuNo, skuName: row.skuName, category: row.category,
    spec: row.spec, unit: row.unit, price: Number(row.price),
    warehouseId: null, safetyQty: Number(row.safetyQty), imageTag: row.imageTag, initQty: 0
  })
  const w = warehouses.value.find((x) => x.warehouseNo === row.warehouseNo)
  if (w) form.warehouseId = w.id
  showForm.value = true
}

async function save() {
  if (!form.skuName) {
    ElMessage.warning('请填写物料名称')
    return
  }
  saving.value = true
  try {
    if (form.id) await api.admin.product.update(form.id, form)
    else await api.admin.product.create(form)
    ElMessage.success('已保存')
    showForm.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  try {
    await api.admin.product.status(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success('已更新')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openWarehouse() {
  showWh.value = true
}

async function addWh() {
  if (!wh.warehouseNo || !wh.warehouseName) {
    ElMessage.warning('请填写料仓编号和名称')
    return
  }
  try {
    await api.admin.product.createWarehouse({ ...wh })
    ElMessage.success('料仓已新增')
    wh.warehouseNo = ''
    wh.warehouseName = ''
    wh.capacity = 0
    loadWarehouses()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await loadWarehouses()
  load()
})
</script>

<style scoped>
.wh-form { display: flex; gap: 8px; margin-top: 14px; }
</style>
