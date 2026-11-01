import { createHttp, createApi, rehydrateUser } from '@xinji/shared'

export const TOKEN_KEY = 'xj_admin_token'
export const USER_KEY = 'xj_admin_user'

export const http = createHttp({
  tokenKey: TOKEN_KEY,
  onUnauthorized: function () {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    if (location.pathname.indexOf('/login') === -1) location.href = '/admin/login'
  }
})

export const api = createApi(http)

export function currentUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

/**
 * 启动时回填用户态：token 在但资料丢了，不至于「接口能调、界面显示未登录」。
 */
export async function rehydrate() {
  const hit = currentUser()
  if (hit) return hit
  return await rehydrateUser(http, TOKEN_KEY, USER_KEY)
}

export function saveLogin(data) {
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data))
}

export function logout() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  location.href = '/admin/login'
}

export function isLogin() {
  return !!localStorage.getItem(TOKEN_KEY)
}
