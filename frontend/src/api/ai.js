import http from './http'

export const knowledgePage = (params) => http.get('/ai/knowledge/page', { params })
export const vectorizeKnowledge = () => http.post('/ai/knowledge/vectorize')
export const rebuildKnowledge = () => http.post('/ai/knowledge/rebuild')

export const warningRules = () => http.get('/ai/warning/rules')
export const toggleWarningRule = (code, enabled) =>
  http.put(`/ai/warning/rules/${code}/toggle`, null, { params: { enabled } })
