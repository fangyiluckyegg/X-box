import axios from 'axios'
import { Notification, MessageBox, Message } from 'element-ui'
import store from '@/store'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'

// 创建请求实例
const service = axios.create({
  // 定义公共的请求前缀
  baseURL: process.env.VUE_APP_BASE_API,
  // 超时（10s 适合大多数 CRUD 接口；耗时接口如 Excel 比对/下载在调用处单独覆盖）
  timeout: 10000
})

// 请求request拦截器，查看请求是否带token
service.interceptors.request.use(config => {
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  if (getToken() && !isToken) {
    config.headers['Authorization'] = 'Bearer ' + getToken()
  }

  return config
}, error => {
    // [P1-FIX] axios 1.x 拦截器 error handler 必须返回 Promise.reject，否则错误会被静默吞没
    if (process.env.NODE_ENV === 'development') console.log(error)
    return Promise.reject(error)
})

// 响应response拦截器
service.interceptors.response.use(res => {
    const code = res.data.code || 200;
    // 获取错误信息
    const msg = res.data.msg || errorCode[code] || errorCode['default']
    if (code === 401) {
      MessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
          confirmButtonText: '重新登录',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(() => {
        store.dispatch('LogOut').then(() => {
          location.href = '/index';
        })
      }).catch(() => {});
      return Promise.reject('会话已过期，请重新登录。')
    } else if (code === 500) {
      Message({
        message: msg,
        type: 'error'
      })
      return Promise.reject(new Error(msg))
    } else if (code !== 200) {
      Notification.error({
        title: msg
      })
      return Promise.reject('error')
    } else {
      return res.data
    }
  },
  error => {
    // [P1-FIX] axios 1.x 适配：error.message 可能为 undefined（如请求取消），增加空值保护
    let { message } = error;
    if (message === "Network Error") {
      message = "后端接口连接异常";
    }
    else if (message && message.includes("timeout")) {
      message = "系统接口请求超时";
    }
    Message({
      message: message,
      type: 'error',
      duration: 2000
    })
    return Promise.reject(error)
  }
)

export default service
