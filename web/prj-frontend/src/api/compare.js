import request from '@/utils/request'

// 提交两个 Excel 文件进行比对，后端返回比对结果（JSON）
export function compareExcelApi(formData) {
  return request({
    url: '/api/excel/compare',
    method: 'post',
    data: formData,
    timeout: 60000
  })
}

// [P0-FIX] 下载比对结果，改用项目封装的 request 实例以自动携带 Authorization 头
// responseType: 'blob' 使响应以 Blob 形式返回；request 拦截器对 Blob 响应
// 取 res.data.code 为 undefined，fallback 到 200，正常返回 res.data（即 Blob）
export function downloadCompareResultApi() {
  return request({
    url: '/api/excel/downloadResult',
    method: 'get',
    responseType: 'blob',
    timeout: 30000
  })
}

export default {
  compareExcelApi,
  downloadCompareResultApi
}
