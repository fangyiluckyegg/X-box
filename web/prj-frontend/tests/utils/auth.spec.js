// ============================================================
// 前端令牌 Cookie 工具单元测试样例（可选 starter）
//
// 对应审查报告 F-11：JWT 存于非 HttpOnly Cookie（auth.js）。
// 本样例仅验证令牌的写入/读取/清除基本契约，便于后续扩展为安全断言
// （例如验证 sameSite=Lax、secure 在 HTTPS 下为 true）。
//
// 运行：npx vitest run tests/utils/auth.spec.js
// ============================================================
import { describe, it, expect, beforeEach } from 'vitest'
import { getToken, setToken, removeToken } from '../../src/utils/auth'

describe('auth token cookie utils (F-11)', () => {
  beforeEach(() => {
    removeToken()
  })

  it('setToken 写入后 getToken 可读取', () => {
    setToken('eyJhbGciOiJIUzI1NiJ9.sample')
    expect(getToken()).toBe('eyJhbGciOiJIUzI1NiJ9.sample')
  })

  it('removeToken 清除令牌后 getToken 为 undefined', () => {
    setToken('abc')
    removeToken()
    expect(getToken()).toBeUndefined()
  })

  it('未设置令牌时 getToken 返回 undefined', () => {
    expect(getToken()).toBeUndefined()
  })
})
