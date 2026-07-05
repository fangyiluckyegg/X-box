import request from '@/utils/request'
import axios from 'axios'

// 提交两个 Excel 文件进行比对，后端返回比对结果（JSON）
export function compareExcelApi(formData) {
  return request({
    url: '/api/excel/compare',
    method: 'post',
    data: formData
  })
}

// 下载比对结果，返回 Blob（使用原生 axios 绕过响应拦截器）
export function downloadCompareResultApi(params) {
  return axios.get('/api/excel/downloadResult', {
    params,
    responseType: 'blob'
  }).then(res => res.data)
}

export default {
  compareExcelApi,
  downloadCompareResultApi
}
