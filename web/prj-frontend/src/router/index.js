import Vue from 'vue'
import Router from 'vue-router'
Vue.use(Router)
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
