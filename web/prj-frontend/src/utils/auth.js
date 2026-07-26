// [F-11-FIX] 鉴权改造：真实 JWT 由后端以 HttpOnly Cookie 下发，前端 JS 永不直接持有令牌，
// 仅在 localStorage 记录一个「已登录」标记用于路由守卫判断，从源头消除 XSS 窃取令牌的风险。
const LoginFlag = 'Admin-IsLogin'

export function getToken() {
  // 返回登录态标记（'1' 或 null）；路由守卫据此判断是否已登录
  return localStorage.getItem(LoginFlag)
}

export function setToken(token) {
  // 刻意不保存原始 JWT（参数被忽略）；令牌只存在于 HttpOnly Cookie 中，JS 无法读取
  return localStorage.setItem(LoginFlag, '1')
}

export function removeToken() {
  return localStorage.removeItem(LoginFlag)
}
