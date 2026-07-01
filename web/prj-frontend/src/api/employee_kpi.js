import request from '@/utils/request'

// 查询员工评价管理列表
export function listEmployee_kpi(query) {
  return request({
    url: '/employee_kpi/list',
    method: 'get',
    params: query
  })
}

// 查询员工评价管理详细
export function getEmployee_kpi(id) {
  return request({
    url: '/employee_kpi/' + id,
    method: 'get'
  })
}

// 新增员工评价管理
export function addEmployee_kpi(data) {
  return request({
    url: '/employee_kpi',
    method: 'post',
    data: data
  })
}

// 修改员工评价管理
export function updateEmployee_kpi(data) {
  return request({
    url: '/employee_kpi',
    method: 'put',
    data: data
  })
}

// 删除员工评价管理
export function delEmployee_kpi(id) {
  return request({
    url: '/employee_kpi/' + id,
    method: 'delete'
  })
}
