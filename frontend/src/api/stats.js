import http from './http'

export const dashboardOverview = () => http.get('/stats/dashboard/overview')
export const dashboardTrend = (days = 7) => http.get('/stats/dashboard/trend', { params: { days } })
export const dashboardTypeDistribution = (days = 7) =>
  http.get('/stats/dashboard/type-distribution', { params: { days } })
export const dashboardCsLoad = () => http.get('/stats/dashboard/cs-load')
