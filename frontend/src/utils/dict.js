/**
 * 前后端共享的字典映射：后端返回英文枚举，前端统一转中文与标签色
 */
export const statusMap = {
  PENDING_STRUCTURE: { label: '待结构化', type: 'info' },
  PENDING_DISPATCH: { label: '待派单', type: 'warning' },
  PENDING_HANDLE: { label: '待处理', type: 'primary' },
  PROCESSING: { label: '处理中', type: 'primary' },
  PENDING_CONFIRM: { label: '待确认', type: 'warning' },
  RESOLVED: { label: '已解决', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info' },
  ESCALATED: { label: '已升级', type: 'danger' }
}

export const workTypeMap = {
  COMPLAINT: { label: '投诉', type: 'danger' },
  REFUND: { label: '退款', type: 'warning' },
  EXCHANGE: { label: '换货', type: 'primary' },
  LOGISTICS: { label: '物流', type: 'info' },
  INVOICE: { label: '发票', type: 'success' },
  OTHER: { label: '其他', type: 'info' }
}

export const priorityMap = {
  LOW: { label: '低', type: 'info' },
  NORMAL: { label: '普通', type: 'primary' },
  HIGH: { label: '高', type: 'warning' },
  URGENT: { label: '紧急', type: 'danger' }
}

export const sourceChannelMap = {
  WEB: '网页',
  API: '接口',
  PLATFORM: '平台'
}

export const operatorTypeMap = {
  USER: '用户',
  CS: '客服',
  SYSTEM: '系统',
  AI: 'AI Agent'
}

export const actionMap = {
  CREATE: '创建工单',
  STRUCTURE: 'AI 结构化',
  DISPATCH: '派单',
  RE_DISPATCH: '超时转派',
  CLAIM: '领取工单',
  REPLY: '回复用户',
  RESOLVE: '标记解决',
  TRANSFER: '转交',
  CLOSE: '关闭工单',
  ESCALATE: '预警升级',
  STATUS_CHANGE: '状态变更'
}

export const labelOf = (map, key, fallback = '-') => {
  if (!key) return fallback
  const item = map[key]
  if (!item) return key
  return typeof item === 'string' ? item : item.label
}
