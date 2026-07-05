import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'

// [P1-FIX] Cookie 安全加固：添加 SameSite 防止 CSRF，secure 在 HTTPS 下生效
const cookieOptions = {
  sameSite: 'Lax',
  secure: window.location.protocol === 'https:'
}

export function getToken() {
  return Cookies.get(TokenKey)
}

export function setToken(token) {
  return Cookies.set(TokenKey, token, cookieOptions)
}

export function removeToken() {
  return Cookies.remove(TokenKey, cookieOptions)
}
