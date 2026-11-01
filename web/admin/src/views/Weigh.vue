<template>
  <div>
    <div class="grid">
      <div class="page-card">
        <div class="section-title">过磅出票</div>
        <el-form :model="form" label-width="88px" size="default">
          <el-form-item label="订单号">
            <el-input v-model="form.orderNo" placeholder="XJ2026..." @blur="loadOrder" />
          </el-form-item>
          <el-form-item label="车牌号">
            <el-input v-model="form.plateNo" placeholder="吉B·8K275" />
          </el-form-item>
          <el-row :gutter="10">
            <el-col :span="12">
              <el-form-item label="司机"><el-input v-model="form.driverName" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="电话"><el-input v-model="form.driverPhone" /></el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="10">
            <el-col :span="12">
              <el-form-item label="毛重"><el-input-number v-model="form.grossWeight" :min="0" :precision="3" :controls="false" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="皮重"><el-input-number v-model="form.tareWeight" :min="0" :precision="3" :controls="false" style="width: 100%" /></el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="10">
            <el-col :span="12">
              <el-form-item label="含水率">
                <el-input-number v-model="form.waterRate" :min="0" :max="1" :step="0.001" :precision="4" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="扣杂量">
                <el-input-number v-model="form.impurityDeductWeight" :min="0" :precision="3" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="过磅员"><el-input v-model="form.weighman" /></el-form-item>

          <div class="calc">
            <div class="c-row"><span>净重</span><b>{{ qty(net) }} 吨</b></div>
            <div class="c-row"><span>扣水（净重 × 含水率）</span><b>{{ qty(waterDeduct) }} 吨</b></div>
            <div class="c-row"><span>扣杂</span><b>{{ qty(form.impurityDeductWeight) }} 吨</b></div>
            <div class="c-row tot"><span>结算量</span><b>{{ qty(settle) }} 吨</b></div>
          </div>

          <el-button type="primary" style="width: 100%; margin-top: 14px" :loading="saving" @click="submit">
            出票并回填订单
          </el-button>
          <p class="muted small" style="margin-top: 8px; text-align: center">
            结算量会写回订单明细，后续对账按结算量计金额
          </p>
        </el-form>
      </div>

      <div class="page-card">
        <div class="bar">
          <div class="section-title" style="margin: 0">已出磅单</div>
          <div class="spacer"></div>
          <el-input v-model="filterNo" placeholder="按订单号筛选" clearable size="small" style="width: 170px" @keyup.enter="loadTickets" />
          <el-button size="small" @click="loadTickets">查询</el-button>
        </div>
        <el-table :data="tickets" v-loading="loading" size="small" empty-text="暂无磅单" height="560">
          <el-table-column prop="ticketNo" label="磅单号" width="132">
            <template #default="{ row }"><span class="mono small">{{ row.ticketNo }}</span></template>
          </el-table-column>
          <el-table-column prop="orderNo" label="订单号" width="140">
            <template #default="{ row }"><span class="mono small">{{ row.orderNo }}</span></template>
          </el-table-column>
          <el-table-column prop="plateNo" label="车牌" width="100" />
          <el-table-column label="毛重" width="86" align="right">
            <template #default="{ row }"><span class="num">{{ qtyShort(row.grossWeight) }}</span></template>
          </el-table-column>
          <el-table-column label="皮重" width="86" align="right">
            <template #default="{ row }"><span class="num">{{ qtyShort(row.tareWeight) }}</span></template>
          </el-table-column>
          <el-table-column label="净重" width="86" align="right">
            <template #default="{ row }"><span class="num">{{ qtyShort(row.netWeight) }}</span></template>
          </el-table-column>
          <el-table-column label="扣水" width="80" align="right">
            <template #default="{ row }"><span class="num muted">{{ qtyShort(row.waterDeductWeight) }}</span></template>
          </el-table-column>
          <el-table-column label="结算量" width="92" align="right">
            <template #default="{ row }"><span class="num" style="color: #0f8a5f; font-weight: 600">{{ qtyShort(row.settleWeight) }}</span></template>
          </el-table-column>
          <el-table-column label="时间" width="130">
            <template #default="{ row }"><span class="muted small">{{ dateStr(row.weighTime) }}</span></template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'
import { qty, qtyShort, dateStr } from '@xinji/shared'

const form = reactive({
  orderNo: '', skuId: null, plateNo: '', driverName: '', driverPhone: '',
  grossWeight: 0, tareWeight: 0, waterRate: 0, impurityDeductWeight: 0, weighman: '王立'
})

const tickets = ref([])
const loading = ref(false)
const saving = ref(false)
const filterNo = ref('')

const net = computed(() => Math.max(0, Number(form.grossWeight || 0) - Number(form.tareWeight || 0)))
const waterDeduct = computed(() => net.value * Number(form.waterRate || 0))
const settle = computed(() => Math.max(0, net.value - waterDeduct.value - Number(form.impurityDeductWeight || 0)))

async function loadOrder() {
  if (!form.orderNo) return
  try {
    const d = await api.admin.order.detail(form.orderNo)
    if (d && d.items && d.items.length) {
      form.skuId = d.items[0].skuId
      ElMessage.success('已关联订单：' + d.order.customerName)
    }
  } catch (e) {
    /* 订单不存在不阻断手工录单 */
  }
}

async function submit() {
  if (!form.orderNo) {
    ElMessage.warning('请填写订单号')
    return
  }
  if (net.value <= 0) {
    ElMessage.warning('毛重必须大于皮重')
    return
  }
  saving.value = true
  try {
    await api.admin.weigh.create({ ...form, skuId: form.skuId })
    ElMessage.success('磅单已生成，结算量已回填订单')
    form.orderNo = ''
    form.grossWeight = 0
    form.tareWeight = 0
    form.waterRate = 0
    form.impurityDeductWeight = 0
    loadTickets()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function loadTickets() {
  loading.value = true
  try {
    tickets.value = (await api.admin.weigh.tickets({ orderNo: filterNo.value || undefined })) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadTickets)
</script>

<style scoped>
.grid { display: grid; grid-template-columns: 420px 1fr; gap: 14px; align-items: start; }
.calc { background: #fafbfc; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 12px 14px; }
.c-row { display: flex; justify-content: space-between; font-size: 12.5px; padding: 4px 0; }
.c-row span { color: var(--el-text-color-secondary); }
.c-row.tot { border-top: 1px dashed var(--el-border-color); margin-top: 6px; padding-top: 8px; font-size: 14px; }
.c-row.tot b { color: #0f8a5f; font-size: 18px; font-variant-numeric: tabular-nums; }
@media (max-width: 1200px) {
  .grid { grid-template-columns: 1fr; }
}
</style>
