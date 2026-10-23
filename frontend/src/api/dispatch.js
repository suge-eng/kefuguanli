import http from './http'

export const csPage = (params) => http.get('/dispatch/cs/page', { params })
export const csCreate = (data) => http.post('/dispatch/cs', data)
export const csUpdate = (data) => http.put('/dispatch/cs', data)
export const csOnline = (id) => http.put(`/dispatch/cs/${id}/online`)
export const csOffline = (id) => http.put(`/dispatch/cs/${id}/offline`)
export const csLoad = (id) => http.get(`/dispatch/cs/${id}/load`)

export const strategyList = () => http.get('/dispatch/strategy')
export const enableStrategy = (code) => http.put(`/dispatch/strategy/${code}/enable`)
