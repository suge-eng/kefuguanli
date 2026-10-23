import http from './http'

export const myPage = (params) => http.get('/workbench/my-page', { params })
export const workbenchDetail = (id) => http.get(`/workbench/detail/${id}`)
export const claimOrder = (id, handlerId) =>
  http.put(`/workbench/claim/${id}`, null, { params: { handlerId } })
export const replyOrder = (id, data) => http.post(`/workbench/reply/${id}`, data)
export const resolveOrder = (id, handlerId) =>
  http.put(`/workbench/resolve/${id}`, null, { params: { handlerId } })
export const transferOrder = (id, data) => http.put(`/workbench/transfer/${id}`, data)
export const closeOrder = (id, operatorId = 'web-console', reason = '人工关闭') =>
  http.put(`/workbench/close/${id}`, null, { params: { operatorId, reason } })
export const batchClaim = (data) => http.post('/workbench/batch-claim', data)
export const replyDraft = (id) => http.post(`/workbench/reply-draft/${id}`)

export const uploadAttachment = (workOrderId, operatorId, file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/workbench/attachment/upload', form, {
    params: { workOrderId, operatorId },
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
