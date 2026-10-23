import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一请求封装
 * 设计思路：后端所有接口都返回 Result{code,message,data}，
 * 这里直接把 data 解包给页面用；非 200 和网络错误统一弹提示，
 * 页面只关心成功路径，不重复写错误处理。
 */
const http = axios.create({
  baseURL: '/api',
  // AI 生成草稿单次要 20~30 秒，统一给 90 秒上限
  timeout: 90000
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code !== 'undefined' && body.code !== 200) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body ? body.data : null
  },
  (err) => {
    const status = err.response?.status
    let message = err.response?.data?.message
    if (status === 429) message = '请求过于频繁，请稍后重试'
    else if (status === 503) message = '下游服务暂不可用，请稍后重试'
    else if (!message) message = err.message || '网络异常，请检查网关是否在线'
    ElMessage.error(message)
    return Promise.reject(err)
  }
)

export default http
