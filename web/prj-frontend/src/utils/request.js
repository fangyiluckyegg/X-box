import axios from 'axios'
import { Notification, MessageBox, Message } from 'element-ui'
import store from '@/store'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'

// 创建请求实例
const service = axios.create({
  // 定义公共的请求前缀
  baseURL: process.env.VUE_APP_BASE_API,
  // 超时
  timeout: 2000
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
    console.log(error)
    Promise.reject(error)
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
    let { message } = error;
    if (message == "Network Error") {
      message = "后端接口连接异常";
    }
    else if (message.includes("timeout")) {
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
