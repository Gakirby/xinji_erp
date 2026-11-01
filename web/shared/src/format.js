export const ORDER_STATUS = {
  PENDING_AUDIT: { text: '待审核', type: 'warning' },
  AUDITED: { text: '已审核', type: 'primary' },
  DISPATCHED: { text: '已派车', type: 'primary' },
  SHIPPED: { text: '已出厂', type: 'info' },
  SIGNED: { text: '已签收', type: 'success' },
  SETTLED: { text: '已对账', type: 'success' },
  INVOICED: { text: '已开票', type: 'success' },
  PAID: { text: '已收款', type: 'success' },
  CANCELLED: { text: '已取消', type: 'info' }
}

export const DISPATCH_STATUS = {
  PENDING: { text: '待发车', type: 'info' },
  LOADING: { text: '装车中', type: 'warning' },
  SHIPPED: { text: '已出厂', type: 'primary' },
  DONE: { text: '已签收', type: 'success' },
  CANCELLED: { text: '已取消', type: 'info' }
}

export const PRODUCTION_STATUS = {
  PENDING: { text: '待开始', type: 'info' },
  RUNNING: { text: '生产中', type: 'warning' },
  DONE: { text: '已完工', type: 'success' },
  CANCELLED: { text: '已取消', type: 'info' }
}

export const PURCHASE_STATUS = {
  PENDING: { text: '在途', type: 'info' },
  PARTIAL: { text: '部分到货', type: 'warning' },
  DONE: { text: '已完成', type: 'success' }
}

export const SETTLEMENT_STATUS = {
  DRAFT: { text: '草稿', type: 'info' },
  CONFIRMED: { text: '已确认', type: 'success' },
  INVOICED: { text: '已开票', type: 'success' }
}

export const RECEIVABLE_STATUS = {
  UNPAID: { text: '未收款', type: 'danger' },
  PARTIAL: { text: '部分收款', type: 'warning' },
  PAID: { text: '已收清', type: 'success' }
}

export const INVOICE_STATUS = {
  PENDING: { text: '待开具', type: 'warning' },
  ISSUED: { text: '已开具', type: 'success' },
  VOID: { text: '已作废', type: 'info' }
}

export const ACTIVITY_STATUS = {
  DRAFT: { text: '草稿', type: 'info' },
  PUBLISHED: { text: '已发布', type: 'primary' },
  RUNNING: { text: '进行中', type: 'danger' },
  ENDED: { text: '已结束', type: 'info' }
}

export const TOKEN_STATUS = {
  PENDING: { text: '发放中', type: 'info' },
  ISSUED: { text: '待核销', type: 'warning' },
  USED: { text: '已下单', type: 'success' },
  EXPIRED: { text: '已过期', type: 'info' },
  FAILED: { text: '失败', type: 'danger' }
}

export const BIZ_TYPE = {
  INBOUND: '采购入库',
  OUTBOUND: '销售出库',
  FREEZE: '订单冻结',
  UNFREEZE: '解冻释放',
  ALLOC: '活动划拨',
  RETURN: '活动回拨',
  CHECK: '盘点调整'
}

export const PAY_METHOD = {
  TRANSFER: '银行转账',
  CASH: '现金',
  CHECK: '承兑汇票',
  DRAFT: '商业汇票'
}

export const INVOICE_TYPE = {
  VAT_SPECIAL: '增值税专票',
  VAT_NORMAL: '增值税普票'
}

export function label(map, key) {
  const hit = map[key]
  return hit ? hit.text : (key || '-')
}

export function statusType(map, key) {
  const hit = map[key]
  return hit ? hit.type : 'info'
}

export function money(v) {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

export function qty(v) {
  if (v === null || v === undefined || v === '') return '0.000'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toFixed(3)
}

export function qtyShort(v) {
  const n = Number(v || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

export function dateStr(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 16)
}

export function dayStr(v) {
  if (!v) return '-'
  return String(v).slice(0, 10)
}

export function percent(a, b) {
  const x = Number(a || 0), y = Number(b || 0)
  if (!y) return '0%'
  return ((x / y) * 100).toFixed(1) + '%'
}

export function todayStr() {
  const d = new Date()
  const p = function (n) { return n < 10 ? '0' + n : '' + n }
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate())
}

export function monthStartStr() {
  const d = new Date()
  const p = function (n) { return n < 10 ? '0' + n : '' + n }
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-01'
}
