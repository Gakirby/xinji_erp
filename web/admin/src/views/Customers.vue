<template>
  <div>
    <div class="page-card">
      <div class="bar">
        <el-input v-model="keyword" placeholder="客户名称 / 编号" clearable style="width: 200px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button type="primary" @click="openCreate">新增客户</el-button>
      </div>

      <el-alert
        v-if="warnings.length"
        :title="warnings.length + ' 家客户授信使用率超过 90%'"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />

      <el-table :data="filtered" v-loading="loading" size="small" empty-text="暂无客户">
        <el-table-column prop="customerNo" label="客户编号" width="110">
          <template #default="{ row }"><span class="mono">{{ row.customerNo }}</span></template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contactName" label="联系人" width="90" />
        <el-table-column prop="contactPhone" label="电话" width="130" />
        <el-table-column label="授信额度" width="120" align="right">
          <template #default="{ row }"><span class="num">¥{{ money(row.creditLimit) }}</span></template>
        </el-table-column>
        <el-table-column label="已用额度" width="120" align="right">
          <template #default="{ row }"><span class="num" style="color: #bd7415">¥{{ money(row.creditUsed) }}</span></template>
        </el-table-column>
        <el-table-column label="使用率" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="usage(row)"
              :stroke-width="8"
              :color="usage(row) > 90 ? '#c0392b' : (usage(row) > 70 ? '#c98a0a' : '#0f8a5f')"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openCredit(row)">调整授信</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="toggle(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showForm" :title="form.id ? '编辑客户' : '新增客户'" width="500px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="收货地址"><el-input v-model="form.address" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="授信额度">
          <el-input-number v-model="form.creditLimit" :min="0" :precision="2" :step="10000" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCredit" title="调整授信额度" width="420px">
      <el-form label-width="96px">
        <el-form-item label="客户">{{ current?.customerName }}</el-form-item>
        <el-form-item label="当前额度">¥{{ money(current?.creditLimit) }}</el-form-item>
        <el-form-item label="已用">¥{{ money(current?.creditUsed) }}</el-form-item>
        <el-form-item label="新额度">
          <el-input-number v-model="newLimit" :min="0" :precision="2" :step="10000" style="width: 100%" />
        </el-form-item>
      </el-form>
      <el-alert
        title="新额度不能低于已用额度，否则会被后端拦下"
        type="info"
        :closable="false"
        show-icon
      />
      <template #footer>
        <el-button @click="showCredit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCredit">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'
import { money } from '@xinji/shared'

const list = ref([])
const warnings = ref([])
const loading = ref(false)
const saving = ref(false)
const showForm = ref(false)
const showCredit = ref(false)
const keyword = ref('')
const current = ref(null)
const newLimit = ref(0)

const form = reactive({ id: null, customerName: '', contactName: '', contactPhone: '', address: '', creditLimit: 0 })

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter((x) =>
    (x.customerName || '').toLowerCase().includes(k) || (x.customerNo || '').toLowerCase().includes(k)
  )
})

function usage(row) {
  const l = Number(row.creditLimit || 0)
  if (!l) return 0
  return Math.min(100, Number(((Number(row.creditUsed || 0) / l) * 100).toFixed(1)))
}

async function load() {
  loading.value = true
  try {
    list.value = (await api.admin.customer.list()) || []
    warnings.value = (await api.admin.customer.creditWarnings()) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, customerName: '', contactName: '', contactPhone: '', address: '', creditLimit: 0 })
  showForm.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    customerName: row.customerName,
    contactName: row.contactName,
    contactPhone: row.contactPhone,
    address: row.address,
    creditLimit: Number(row.creditLimit)
  })
  showForm.value = true
}

async function save() {
  if (!form.customerName) {
    ElMessage.warning('请填写客户名称')
    return
  }
  saving.value = true
  try {
    if (form.id) await api.admin.customer.update(form.id, form)
    else await api.admin.customer.create(form)
    ElMessage.success('已保存')
    showForm.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function openCredit(row) {
  current.value = row
  newLimit.value = Number(row.creditLimit)
  showCredit.value = true
}

async function saveCredit() {
  saving.value = true
  try {
    await api.admin.customer.credit(current.value.id, newLimit.value)
    ElMessage.success('授信额度已调整')
    showCredit.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  try {
    await api.admin.customer.status(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success('已更新')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>
