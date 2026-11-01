/**
 * 全量接口封装，路径与后端 Controller 严格一一对应。
 * 后端统一返回 { code, msg, data }，request.js 里已经把 data 剥出来了，
 * 所以这里每个方法直接拿到业务数据。
 */
export function createApi(http) {
  const client = {
    product: {
      list: (params) => http.get('/client/products', { params }),
      detail: (id) => http.get('/client/products/' + id)
    },
    order: {
      create: (data) => http.post('/client/orders', data),
      list: (params) => http.get('/client/orders', { params }),
      detail: (orderNo) => http.get('/client/orders/' + orderNo),
      cancel: (orderNo, reason) =>
        http.post('/client/orders/' + orderNo + '/cancel', null, { params: { reason: reason || '客户主动取消' } })
    },
    seckill: {
      activities: () => http.get('/client/seckill/activities'),
      skus: (activityId) => http.get('/client/seckill/activities/' + activityId + '/skus'),
      grab: (data) => http.post('/client/seckill/grab', data),
      token: (tokenNo) => http.get('/client/seckill/tokens/' + tokenNo),
      myTokens: () => http.get('/client/seckill/tokens'),
      useToken: (tokenNo, data) => http.post('/client/seckill/tokens/' + tokenNo + '/order', data)
    }
  }

  const admin = {
    dashboard: {
      summary: () => http.get('/admin/dashboard/summary'),
      byStatus: () => http.get('/admin/dashboard/orders/by-status'),
      recent: (params) => http.get('/admin/dashboard/orders/recent', { params })
    },

    order: {
      list: (params) => http.get('/admin/orders', { params }),
      detail: (orderNo) => http.get('/admin/orders/' + orderNo),
      audit: (orderNo) => http.post('/admin/orders/' + orderNo + '/audit'),
      dispatch: (orderNo) => http.post('/admin/orders/' + orderNo + '/dispatch'),
      ship: (orderNo) => http.post('/admin/orders/' + orderNo + '/ship'),
      sign: (orderNo) => http.post('/admin/orders/' + orderNo + '/sign'),
      cancel: (orderNo, reason) =>
        http.post('/admin/orders/' + orderNo + '/cancel', null, { params: { reason: reason || '后台取消' } })
    },

    product: {
      list: (params) => http.get('/admin/products', { params }),
      warnings: () => http.get('/admin/products/warnings'),
      create: (data) => http.post('/admin/products', data),
      update: (id, data) => http.put('/admin/products/' + id, data),
      status: (id, status) =>
        http.post('/admin/products/' + id + '/status', null, { params: { status } }),
      warehouses: () => http.get('/admin/products/warehouses'),
      createWarehouse: (data) => http.post('/admin/products/warehouses', data)
    },

    stock: {
      summary: () => http.get('/admin/stock/summary'),
      flows: (params) => http.get('/admin/stock/flows', { params }),
      alloc: (data) => http.post('/admin/stock/alloc', data),
      back: (data) => http.post('/admin/stock/return', data),
      inbound: (data) => http.post('/admin/stock/inbound', data)
    },

    purchase: {
      list: (params) => http.get('/admin/purchase/orders', { params }),
      create: (data) => http.post('/admin/purchase/orders', data),
      arrive: (id, data) => http.post('/admin/purchase/orders/' + id + '/arrive', data),
      detail: (id) => http.get('/admin/purchase/orders/' + id),
      cancel: (id) => http.post('/admin/purchase/orders/' + id + '/cancel')
    },

    dispatch: {
      vehicles: (params) => http.get('/admin/dispatch/vehicles', { params }),
      createVehicle: (data) => http.post('/admin/dispatch/vehicles', data),
      updateVehicle: (id, data) => http.put('/admin/dispatch/vehicles/' + id, data),
      vehicleStatus: (id, status) =>
        http.post('/admin/dispatch/vehicles/' + id + '/status', null, { params: { status } }),
      insuranceWarnings: () => http.get('/admin/dispatch/vehicles/insurance-warnings'),
      drivers: (params) => http.get('/admin/dispatch/drivers', { params }),
      createDriver: (data) => http.post('/admin/dispatch/drivers', data),
      updateDriver: (id, data) => http.put('/admin/dispatch/drivers/' + id, data),
      driverStatus: (id, status) =>
        http.post('/admin/dispatch/drivers/' + id + '/status', null, { params: { status } }),
      orders: (params) => http.get('/admin/dispatch/orders', { params }),
      createOrder: (data) => http.post('/admin/dispatch/orders', data),
      loading: (id) => http.post('/admin/dispatch/orders/' + id + '/loading'),
      cancelOrder: (id) => http.post('/admin/dispatch/orders/' + id + '/cancel'),
      board: () => http.get('/admin/dispatch/board')
    },

    weigh: {
      create: (data) => http.post('/admin/weigh/tickets', data),
      tickets: (params) => http.get('/admin/weigh/tickets', { params })
    },

    production: {
      orders: (params) => http.get('/admin/production/orders', { params }),
      create: (data) => http.post('/admin/production/orders', data),
      start: (id) => http.post('/admin/production/orders/' + id + '/start'),
      finish: (id, data) => http.post('/admin/production/orders/' + id + '/finish', data),
      cancel: (id) => http.post('/admin/production/orders/' + id + '/cancel'),
      board: () => http.get('/admin/production/board')
    },

    customer: {
      list: (params) => http.get('/admin/customers', { params }),
      options: () => http.get('/admin/customers/options'),
      detail: (id) => http.get('/admin/customers/' + id),
      create: (data) => http.post('/admin/customers', data),
      update: (id, data) => http.put('/admin/customers/' + id, data),
      credit: (id, creditLimit) =>
        http.post('/admin/customers/' + id + '/credit', null, { params: { creditLimit } }),
      status: (id, status) =>
        http.post('/admin/customers/' + id + '/status', null, { params: { status } }),
      creditWarnings: () => http.get('/admin/customers/credit-warnings')
    },

    finance: {
      createSettlement: (data) => http.post('/admin/finance/settlements', data),
      settlements: (params) => http.get('/admin/finance/settlements', { params }),
      settlementItems: (id) => http.get('/admin/finance/settlements/' + id + '/items'),
      confirmSettlement: (id) => http.post('/admin/finance/settlements/' + id + '/confirm'),
      receivables: (params) => http.get('/admin/finance/receivables', { params }),
      receive: (id, amount) =>
        http.post('/admin/finance/receivables/' + id + '/receive', null, { params: { amount } }),
      receiveDetail: (id, data) =>
        http.post('/admin/finance/receivables/' + id + '/receive-detail', null, { params: data }),
      outstanding: () => http.get('/admin/finance/receivables/outstanding'),
      invoices: (params) => http.get('/admin/finance/invoices', { params }),
      createInvoice: (data) => http.post('/admin/finance/invoices', data),
      issueInvoice: (id) => http.post('/admin/finance/invoices/' + id + '/issue'),
      voidInvoice: (id) => http.post('/admin/finance/invoices/' + id + '/void'),
      invoiceStats: () => http.get('/admin/finance/invoices/stats'),
      payments: (params) => http.get('/admin/finance/payments', { params }),
      paymentStats: () => http.get('/admin/finance/payments/stats')
    },

    seckill: {
      activities: (params) => http.get('/admin/seckill/activities', { params }),
      createActivity: (data) => http.post('/admin/seckill/activities', data),
      updateActivity: (id, data) => http.put('/admin/seckill/activities/' + id, data),
      publish: (id) => http.post('/admin/seckill/activities/' + id + '/publish'),
      start: (id) => http.post('/admin/seckill/activities/' + id + '/start'),
      finish: (id) => http.post('/admin/seckill/activities/' + id + '/finish'),
      activitySkus: (activityId) => http.get('/admin/seckill/activities/' + activityId + '/skus'),
      addSku: (activityId, data) => http.post('/admin/seckill/activities/' + activityId + '/skus', data),
      updateSku: (seckillSkuId, data) => http.put('/admin/seckill/activities/skus/' + seckillSkuId, data),
      removeSku: (seckillSkuId) => http.post('/admin/seckill/activities/skus/' + seckillSkuId + '/remove'),
      warmup: (activityId) => http.post('/admin/seckill/activities/' + activityId + '/warmup'),
      tokens: (params) => http.get('/admin/seckill/tokens', { params }),
      monitor: () => http.get('/admin/seckill/monitor')
    },

    report: {
      overview: (params) => http.get('/admin/reports/overview', { params }),
      salesDaily: (params) => http.get('/admin/reports/sales/daily', { params }),
      salesCustomers: (params) => http.get('/admin/reports/sales/customers', { params }),
      salesSkus: (params) => http.get('/admin/reports/sales/skus', { params }),
      stockFlows: (params) => http.get('/admin/reports/stock/flows', { params }),
      receivableAging: () => http.get('/admin/reports/receivable/aging'),
      orderStatus: () => http.get('/admin/reports/orders/status'),
      seckillEffect: () => http.get('/admin/reports/seckill/effect')
    },

    user: {
      list: (params) => http.get('/admin/users', { params }),
      create: (data) => http.post('/admin/users', data),
      update: (id, data) => http.put('/admin/users/' + id, data),
      resetPassword: (id, newPassword) =>
        http.post('/admin/users/' + id + '/reset-password', null, { params: { newPassword } }),
      status: (id, status) =>
        http.post('/admin/users/' + id + '/status', null, { params: { status } }),
      overview: () => http.get('/admin/users/overview')
    }
  }

  return {
    auth: {
      login: (data) => http.post('/auth/login', data),
      me: () => http.get('/auth/me')
    },
    client,
    admin
  }
}
