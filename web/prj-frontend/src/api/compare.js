import request from '@/utils/request'

// 提交两个 Excel 文件进行比对（仅触发，不等待结果；结果通过 progress + fetchResult 获取）
export function compareExcelApi(formData) {
  return request({
    url: '/api/excel/compare',
    method: 'post',
    data: formData,
    timeout: 300000
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

// 获取已完成的比对结果（progress stage=done 后调用）
export function fetchCompareResultApi() {
  return request({
    url: '/api/excel/fetchResult',
    method: 'get',
    timeout: 10000
  })
}

export default {
  compareExcelApi,
  getExcelCompareProgressApi,
  downloadCompareResultApi,
  fetchCompareResultApi
}