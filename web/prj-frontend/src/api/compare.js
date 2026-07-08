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

// 轮询获取比对进度
export function getExcelCompareProgressApi() {
  return request({
    url: '/api/excel/progress',
    method: 'get',
    timeout: 10000
  })
}

// 下载比对结果
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
  getExcelCompareProgressApi,
  downloadCompareResultApi
}