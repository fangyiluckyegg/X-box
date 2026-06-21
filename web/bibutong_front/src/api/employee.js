import request from '@/utils/request'

// 上传文件操作
export function uploadFile(data) {
  return request({
    url: '/employee/',
    method: 'post'
  })
}
