import Vue from 'vue'
import Router from 'vue-router'
Vue.use(Router)
// 修复 vue-router 3.x 导航竞态未捕获错误（健壮版，兼容 push 是否返回 Promise）：
// 标签点击 / tab 关闭 / 重定向再入 等场景下多次 router.push 竞态会抛出
// "Navigation cancelled ... with a new navigation" 或 "NavigationDuplicated"。
// 这类导航失败是良性的（被更新的导航取代，用户最终到达目标路由），
// 统一在原型层 catch 吞掉，仅透传真实错误。login.vue 已有同类 .catch 兜底。
function _isBenignNavigationError(err) {
  if (!err) return false
  if (Router.isNavigationFailure && Router.isNavigationFailure(err, Router.NavigationFailureType.duplicated)) return true
  if (Router.isNavigationFailure && Router.isNavigationFailure(err, Router.NavigationFailureType.cancelled)) return true
  if (typeof err.message === 'string' &&
    (err.message.indexOf('Navigation cancelled') > -1 || err.message.indexOf('Avoided redundant navigation') > -1)) return true
  return false
}

const _vueRouterPush = Router.prototype.push
const _vueRouterReplace = Router.prototype.replace

Router.prototype.push = function vueRouterPush(location, onComplete, onAbort) {
  const result = _vueRouterPush.call(this, location, onComplete, onAbort)
  // 低版本 vue-router 的 push 可能不返回 Promise（返回 undefined 或当前 route），
  // 此时直接透传，避免对 undefined 调 .catch 抛 "Cannot read properties of undefined (reading 'catch')"。
  if (result && typeof result.catch === 'function') {
    return result.catch((err) => {
      if (_isBenignNavigationError(err)) return err
      throw err
    })
  }
  return result
}

Router.prototype.replace = function vueRouterReplace(location, onComplete, onAbort) {
  const result = _vueRouterReplace.call(this, location, onComplete, onAbort)
  if (result && typeof result.catch === 'function') {
    return result.catch((err) => {
      if (_isBenignNavigationError(err)) return err
      throw err
    })
  }
  return result
}
/* Layout */
import Layout from '@/layout'
// 公共路由
export const constantRoutes = [

        {
          path: '/',
          component: Layout,
          name: '比对工具',
          meta: { title: '比对工具' },
            children: [
            {
              path: '/compare',
              component: (resolve) => require(['@/views/compare/index.vue'], resolve),
              name: '单位名称比对',
              meta: { title: '单位名称比对' }
            }
          ]
        },
        {
          path: '/',
          component: Layout,
          name: 'XX工具',
          meta: { title: 'XX工具' },
            children: [
            {
              path: '/employee_kpi',
              component: (resolve) => require(['@/views/employee_kpi/index.vue'], resolve),
              name: '员工评价管理',
              meta: { title: '员工评价管理' }
            }
          ]
        },
  {
    path: '/login',
    component: (resolve) => require(['@/views/login'], resolve),
    hidden: true
  },
  {
    path: '/index',
    component: Layout,
    redirect: '/index',
    children: [
      {
        path: '/index',
        component: (resolve) => require(['@/views/index'], resolve),
        name: 'Index',
        meta: { title: '首页', isFixed: true }
      }
    ]
  }
]

export default new Router({
  routes: constantRoutes
})
