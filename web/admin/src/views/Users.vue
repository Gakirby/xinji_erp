<template>
  <div>
    <div class="page-card">
      <div class="bar">
        <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 140px" @change="load">
          <el-option v-for="(v, k) in roleMap" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button type="primary" @click="openCreate">新增账号</el-button>
      </div>

      <el-table :data="list" v-loading="loading" size="small" empty-text="暂无账号">
        <el-table-column prop="username" label="登录名" width="130">
          <template #default="{ row }"><span class="mono">{{ row.username }}</span></template>
        </el-table-column>
        <el-table-column prop="employeeName" label="姓名" width="110" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.userType === 'EMPLOYEE' ? 'primary' : 'info'" size="small">
              {{ row.userType === 'EMPLOYEE' ? '员工' : '客户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">{{ roleMap[row.role] || row.role }}</template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机号" width="130" />
        <el-table-column label="绑定客户" min-width="160">
          <template #default="{ row }">
            <span v-if="row.customerId" class="muted small">{{ customerName(row.customerId) }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="140">
          <template #default="{ row }"><span class="muted small">{{ dateStr(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="resetPwd(row)">重置密码</el-button>
            <el-button
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              size="small"
              :disabled="row.username === 'admin'"
              @click="toggle(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showForm" :title="form.id ? '编辑账号' : '新增账号'" width="500px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="登录名">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号，唯一" />
        </el-form-item>
        <el-form-item label="密码" v-if="!form.id">
          <el-input v-model="form.password" type="password" show-password placeholder="BCrypt 加密存储" />
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.employeeName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.mobile" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.userType">
            <el-radio label="EMPLOYEE">员工</el-radio>
            <el-radio label="CUSTOMER">客户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色" v-if="form.userType === 'EMPLOYEE'">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="(v, k) in staffRoleMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定客户" v-else>
          <el-select v-model="form.customerId" filterable style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.customerName" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'
import { dateStr } from '@xinji/shared'

const list = ref([])
const customers = ref([])
const loading = ref(false)
const saving = ref(false)
const showForm = ref(false)

const query = reactive({ role: '' })

const roleMap = {
  ADMIN: '管理员',
  SALES: '销售',
  FINANCE: '财务',
  WEIGH: '磅房',
  DISPATCH: '调度',
  CUSTOMER: '客户'
}
const staffRoleMap = { ADMIN: '管理员', SALES: '销售', FINANCE: '财务', WEIGH: '磅房', DISPATCH: '调度' }

const form = reactive({
  id: null, username: '', password: '', employeeName: '', mobile: '',
  userType: 'EMPLOYEE', role: 'SALES', customerId: null
})

function customerName(id) {
  const c = customers.value.find((x) => x.id === id)
  return c ? c.customerName : '客户 #' + id
}

async function load() {
  loading.value = true
  try {
    list.value = (await api.admin.user.list({ role: query.role || undefined })) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null, username: '', password: '', employeeName: '', mobile: '',
    userType: 'EMPLOYEE', role: 'SALES', customerId: null
  })
  showForm.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, username: row.username, password: '', employeeName: row.employeeName,
    mobile: row.mobile, userType: row.userType, role: row.role, customerId: row.customerId
  })
  showForm.value = true
}

async function save() {
  if (!form.id && (!form.username || !form.password)) {
    ElMessage.warning('请填写登录名和密码')
    return
  }
  saving.value = true
  try {
    if (form.id) await api.admin.user.update(form.id, form)
    else await api.admin.user.create(form)
    ElMessage.success('已保存')
    showForm.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function resetPwd(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码（至少 6 位）', '重置密码 · ' + row.username, {
      inputValue: '123456',
      confirmButtonText: '确认重置'
    })
    if (!value) return
    await api.admin.user.resetPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

async function toggle(row) {
  try {
    await api.admin.user.status(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success('已更新')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  load()
  try {
    customers.value = (await api.admin.customer.options()) || []
  } catch (e) { /* ignore */ }
})
</script>
