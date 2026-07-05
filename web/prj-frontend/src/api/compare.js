import axios from 'axios'

// 提交两个 Excel 文件进行比对，后端返回比对结果（JSON）
export function compareExcelApi(formData) {
  return axios.post('/api/compare', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 下载比对结果，返回 Blob
export function downloadCompareResultApi(params) {
  return axios.get('/api/compare/download', {
    params,
    responseType: 'blob'
  }).then(res => res.data)
}

export default {
  compareExcelApi,
  downloadCompareResultApi
}
