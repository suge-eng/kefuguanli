import http from './http'

export const pageWorkOrders = (params) => http.get('/work-order/page', { params })
export const getWorkOrderDetail = (id) => http.get(`/work-order/${id}`)
export const createWorkOrder = (data) => http.post('/work-order/create', data)
export const replyWorkOrder = (id, data) => http.post(`/work-order/${id}/reply`, data)
export const closeWorkOrder = (id, reason) =>
  http.put(`/work-order/${id}/close`, null, { params: { operatorId: 'web-console', reason } })
export const changeWorkOrderStatus = (id, targetStatus, remark) =>
  http.put(`/work-order/${id}/status`, null, { params: { targetStatus, remark } })
