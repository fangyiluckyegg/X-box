'use strict'
const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

const name = 'X-box工具箱'

//const port = 80 // 端口
// 开发端口改为8081，避开系统80端口冲突
const port = 8081


// vue.config.js 配置说明
module.exports = {
  publicPath: "/",
  // 在npm run build 或 yarn build 时 ，生成文件的目录名称（要和baseUrl的生产环境路径一致）（默认dist）
  outputDir: 'dist',
  assetsDir: 'static',
  productionSourceMap: false,
  devServer: {
    host: '0.0.0.0',
    port: port,
    public: '127.0.0.1:' + port,
    sockHost: '127.0.0.1',
    sockPort: port,
    open: false, // 容器内不要自动打开浏览器，宿主机手动访问
    // 容器热更新核心配置：轮询监听文件变化
    watchOptions: {
      poll: 1000
    },
    // 后端路由前缀代理：与网关 prj.conf 的 allowlist 对齐（/api/、登录/验证码、业务根路径等）。
    // 前端 VUE_APP_BASE_API 已改为同源相对路径('/')，开发态浏览器请求会先落到本 dev server，
    // 再由下方代理转发到后端容器(prj-backend-c:8080)；生产 / 经网关访问则由网关统一转发，不走此代理。
    // 注意：切勿把代理 key 设为 VUE_APP_BASE_API（现为 '/'），否则会匹配全部请求导致 HMR/静态资源被误代理。
    proxy: (() => {
      const backendTarget = process.env.VUE_APP_PROXY_TARGET || 'http://prj-backend-c:8080'
      const contexts = [
        '/api',
        '/login', '/logout', '/captchaImage',
        '/employee_kpi', '/compare', '/positionLearning', '/druid',
        '/v3', '/swagger-ui', '/doc.html', '/webjars', '/profile'
      ]
      return contexts.reduce((acc, ctx) => {
        acc[ctx] = {
          target: backendTarget,
          changeOrigin: true,
          // 关闭 WebSocket 代理：本项目不使用 ws（前端 HMR 走 sockjs，不经 proxy ws 通道），
          // 避免每个代理在 dev server 的 HTTP Server 上挂一个 upgrade 监听器，
          // 13 个代理累计超过 Node EventEmitter 默认 10 个监听器上限而刷 MaxListenersExceededWarning。
          ws: false
        }
        return acc
      }, {})
    })(),
    disableHostCheck: true,
    allowedHosts: ['all']
  },
  configureWebpack: {
    name: name,
    resolve: {
      alias: {
        '@': resolve('src')
      }
    }
  },
  chainWebpack(config) {
    config.plugins.delete('preload')
    config.plugins.delete('prefetch')
  }
}
