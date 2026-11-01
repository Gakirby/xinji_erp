import { createHttp, createApi } from '@xinji/shared'

export const TOKEN_KEY = 'xj_client_token'
export const USER_KEY = 'xj_client_user'

export const http = createHttp({
  tokenKey: TOKEN_KEY,
  onUnauthorized: function () {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    if (location.pathname.indexOf('/login') === -1) location.href = '/login'
  }
})

export const api = createApi(http)
