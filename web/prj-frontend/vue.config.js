'use strict'
const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

const name = 'X工具箱后台'

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
    proxy: {
      [process.env.VUE_APP_BASE_API]: {
        // Docker Compose多容器互通，使用后端服务名backend，不能localhost
        target: `http://prj-backend-c:8080`,
        //target: `http://backend:8080`,
        //http://prj-backend-c:8080
        //http://bibutong-backend:8080
        changeOrigin: true,
        pathRewrite: {
          ['^' + process.env.VUE_APP_BASE_API]: ''
        }
      }
    },
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
