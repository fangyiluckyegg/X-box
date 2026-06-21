import Vue from 'vue'


import Element from 'element-ui'
import './assets/styles/element-variables.scss'

import '@/assets/styles/index.scss' // global css
import App from './App'
import store from './store'
import router from './router'
import directive from './directive' // directive
import plugins from './plugins' // plugins

import './permission' // permission control


import { resetForm } from "@/utils/index";
// 分页组件
import Pagination from "@/components/Pagination";

// 全局方法挂载
Vue.prototype.resetForm = resetForm

// 全局组件挂载
Vue.component('Pagination', Pagination)

Vue.use(directive)
Vue.use(plugins)

Vue.use(Element, {
  size: 'medium' // set element-ui default size
})


new Vue({
  el: '#app',
  router,
  store,
  render: h => h(App)
})
