import { defineStore } from 'pinia'
import { ref } from 'vue'
import { csPage } from '../api/dispatch'

/**
 * 当前操作身份
 * 设计思路：后端暂无鉴权，前端用"切换客服身份"模拟登录态；
 * 身份存 localStorage，刷新页面不丢失，工作台相关接口都带这个 handlerId
 */
export const useIdentityStore = defineStore('identity', () => {
  const csList = ref([])
  const handlerId = ref(Number(localStorage.getItem('handlerId')) || null)
  const handlerName = ref(localStorage.getItem('handlerName') || '')

  const loadCsList = async () => {
    if (csList.value.length) return csList.value
    const data = await csPage({ pageNum: 1, pageSize: 100 })
    csList.value = data?.records || []
    // 首次进入默认选中第一个客服，保证工作台有可用身份
    if (!handlerId.value && csList.value.length) {
      setIdentity(csList.value[0])
    }
    return csList.value
  }

  const setIdentity = (cs) => {
    if (!cs) return
    handlerId.value = cs.id
    handlerName.value = cs.name
    localStorage.setItem('handlerId', String(cs.id))
    localStorage.setItem('handlerName', cs.name || '')
  }

  return { csList, handlerId, handlerName, loadCsList, setIdentity }
})
