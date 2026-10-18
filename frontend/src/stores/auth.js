import { reactive } from 'vue'
import { api } from '../api/mock'

// 会话状态。权限、范围都来自后端（此处为模拟后端），前端只用于展示，不作为授权依据。
export const auth = reactive({
  isAuthenticated: false, permissionsLoaded: false, user: null, roles: [], grants: [], permissions: [], scopes: {},
  mustChangePassword: false
})

// 原型刷新页面后用它恢复会话；只记账号编号，不记密码。正式系统使用后端会话。
const STORAGE_KEY = 'rbac-demo-session'
const storage = {
  get() { try { return sessionStorage.getItem(STORAGE_KEY) } catch { return null } },
  set(value) { try { value ? sessionStorage.setItem(STORAGE_KEY, value) : sessionStorage.removeItem(STORAGE_KEY) } catch { /* 无法存储时只在内存中保持 */ } }
}

function apply(me) {
  Object.assign(auth, {
    isAuthenticated: true, permissionsLoaded: true, user: me.user, roles: me.roles, grants: me.grants,
    permissions: me.permissions, scopes: me.scopes, mustChangePassword: me.user.mustChangePassword
  })
  storage.set(me.user.id)
}

function clear() {
  Object.assign(auth, {
    isAuthenticated: false, permissionsLoaded: false, user: null, roles: [], grants: [], permissions: [], scopes: {},
    mustChangePassword: false
  })
  storage.set(null)
}

export async function login(account, password) {
  apply(await api.login(account, password))
}

export async function switchIdentity(username) {
  apply(await api.switchDemoIdentity(username))
}

export async function changePassword(oldPassword, newPassword) {
  apply(await api.changePassword(oldPassword, newPassword))
}

let restored = false
export async function restoreSession() {
  if (restored) return
  restored = true
  const id = storage.get()
  if (!id || auth.isAuthenticated) return
  try { apply(await api.resumeDemoSession(id)) } catch { clear() }
}

// 每次切换页面重新拉取权限：授权变更后下一次请求即生效，账号停用后会话失效（GRANT-05、USER-04）。
export async function refreshSession() {
  if (!auth.isAuthenticated) return
  try { apply(await api.me()) } catch { clear() }
}

export function logout() {
  api.logout()
  clear()
}
