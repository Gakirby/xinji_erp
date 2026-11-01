import { reactive } from 'vue'
import { rehydrateUser } from '@xinji/shared'
import { api, http, TOKEN_KEY, USER_KEY } from './api.js'

export const auth = reactive({
  user: readUser(),
  cart: readCart()
})

/**
 * 启动时把用户态补回来：token 在但资料丢了，就不会出现「能下单却显示未登录」。
 */
export async function rehydrate() {
  if (auth.user) return auth.user
  const data = await rehydrateUser(http, TOKEN_KEY, USER_KEY)
  if (data) auth.user = data
  return auth.user
}

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

function readCart() {
  try {
    return JSON.parse(localStorage.getItem('xj_client_cart') || '[]')
  } catch (e) {
    return []
  }
}

export function isLogin() {
  return !!localStorage.getItem(TOKEN_KEY)
}

export async function login(username, password) {
  const data = await api.auth.login({ username, password })
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data))
  auth.user = data
  return data
}

export function logout() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  auth.user = null
  location.href = '/'
}

export function saveCart() {
  localStorage.setItem('xj_client_cart', JSON.stringify(auth.cart))
}

export function addToCart(sku, qty) {
  const hit = auth.cart.find((x) => x.skuId === sku.id)
  if (hit) hit.qty = Number(hit.qty) + Number(qty)
  else
    auth.cart.push({
      skuId: sku.id,
      skuName: sku.skuName,
      spec: sku.spec,
      price: sku.price,
      imageTag: sku.imageTag,
      qty: Number(qty)
    })
  saveCart()
}

export function removeFromCart(skuId) {
  auth.cart = auth.cart.filter((x) => x.skuId !== skuId)
  saveCart()
}

export function clearCart() {
  auth.cart = []
  saveCart()
}

export function cartTotal() {
  return auth.cart.reduce((s, x) => s + Number(x.price) * Number(x.qty), 0)
}

export function cartQty() {
  return auth.cart.reduce((s, x) => s + Number(x.qty), 0)
}
