import request from '@/utils/request'

// TODO: 后端 EmployeeController 尚未实现，以下接口调用会 404，待后端补齐后再启用
// 查询员工信息管理列表
export function listEmployee(query) {
  return request({
    url: '/employee/list',
    method: 'get',
    params: query
  })
}

// 查询员工信息管理详细
export function getEmployee(id) {
  return request({
    url: '/employee/' + id,
    method: 'get'
  })
}

// 新增员工信息管理
export function addEmployee(data) {
  return request({
    url: '/employee',
    method: 'post',
    data: data
  })
}

// 修改员工信息管理
export function updateEmployee(data) {
  return request({
    url: '/employee',
    method: 'put',
    data: data
  })
}

// 删除员工信息管理
export function delEmployee(id) {
  return request({
    url: '/employee/' + id,
    method: 'delete'
  })
}
