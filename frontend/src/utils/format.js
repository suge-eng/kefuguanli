/** 时间格式化：后端 LocalDateTime 序列化为 ISO 字符串 */
export const formatDateTime = (value) => {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length > 19 ? text.slice(0, 19) : text
}

/** 安全解析后端以 JSON 字符串存储的字段（结构化结果/附件列表） */
export const parseJson = (text, fallback = null) => {
  if (!text) return fallback
  if (typeof text === 'object') return text
  try {
    return JSON.parse(text)
  } catch (e) {
    return fallback
  }
}

/** 结构化字段转可展示的键值对，值里的布尔/数组转中文可读形式 */
export const structuredPairs = (text) => {
  const obj = parseJson(text, null)
  if (!obj || typeof obj !== 'object') return []
  return Object.entries(obj).map(([key, value]) => ({
    key,
    value: formatStructuredValue(value)
  }))
}

const formatStructuredValue = (value) => {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) return value.length ? value.join('、') : '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

/** JSON 字符串美化，解析失败原样返回 */
export const prettyJson = (text) => {
  const obj = parseJson(text, undefined)
  if (obj === undefined) return text || '-'
  return JSON.stringify(obj, null, 2)
}
