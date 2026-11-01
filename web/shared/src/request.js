import axios from 'axios'

/**
 * 三端共用的 HTTP 封装。
 *
 * 三个应用跑在同一个域名下（/、/admin/、/m/），localStorage 是共享的，
 * 所以 token 必须用不同的 key 存，否则商城登录会把后台登录态顶掉。
 */
export function createHttp(options) {
  const opts = options || {}
  const tokenKey = opts.tokenKey || 'xj_token'

  const http = axios.create({
    baseURL: opts.baseURL || '/api',
    timeout: opts.timeout || 20000
  })

  http.interceptors.request.use(function (cfg) {
    const token = localStorage.getItem(tokenKey)
    if (token) cfg.headers.Authorization = 'Bearer ' + token
    return cfg
  })

  http.interceptors.response.use(
    function (res) {
      const body = res.data
      if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'code')) {
        if (body.code === 0) return body.data
        if (body.code === 401) {
          localStorage.removeItem(tokenKey)
          // skipAuthRedirect：启动时探测登录态用，401 只当「没登录」，不强制跳登录页
          if (opts.onUnauthorized && !(res.config && res.config.skipAuthRedirect)) opts.onUnauthorized()
          return Promise.reject(new Error(body.msg || '登录已过期，请重新登录'))
        }
        return Promise.reject(new Error(body.msg || '请求失败'))
      }
      return body
    },
    function (err) {
      const status = err.response && err.response.status
      const body = err.response && err.response.data
      let msg = (body && (body.msg || body.message)) || err.message || '网络异常'
      if (status === 401) {
        localStorage.removeItem(tokenKey)
        if (opts.onUnauthorized && !(err.config && err.config.skipAuthRedirect)) opts.onUnauthorized()
        msg = '登录已过期，请重新登录'
      }
      if (status === 404) msg = '接口不存在：' + (err.config && err.config.url)
      if (status >= 500) msg = '服务端异常：' + msg
      return Promise.reject(new Error(msg))
    }
  )

  return http
}

/**
 * 启动时回填用户态。
 *
 * token 在、用户资料丢了（换了浏览器分区、手动清了半截缓存、从别的端跳进来），
 * 就会出现「接口能调通、界面却显示未登录」。这里用 /auth/me 把资料补回来。
 * 探测失败不抛错：token 过期就当没登录，页面照常渲染未登录状态。
 */
export async function rehydrateUser(http, tokenKey, userKey) {
  const token = localStorage.getItem(tokenKey)
  if (!token) return null
  try {
    const data = await http.get('/auth/me', { skipAuthRedirect: true, timeout: 3000 })
    if (data && data.userType) {
      localStorage.setItem(userKey, JSON.stringify(data))
      if (data.token) localStorage.setItem(tokenKey, data.token)
    }
    return data
  } catch (e) {
    return null
  }
}

export function tokenStore(key) {
  return {
    get: function () { return localStorage.getItem(key) || '' },
    set: function (t) { localStorage.setItem(key, t) },
    clear: function () { localStorage.removeItem(key) }
  }
}
