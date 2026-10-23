<template>
  <div class="page-wrap" v-loading="loading">
    <PageHeader :title="`工单详情 ${vo.workOrder?.orderNo || ''}`" subtitle="流转记录、AI 结构化结果与回复草稿">
      <el-button :icon="Back" @click="$router.push('/work-orders')">返回列表</el-button>
    </PageHeader>

    <template v-if="vo.workOrder">
      <el-card>
        <template #header>
          <div class="card__header">
            <span>基本信息</span>
            <div>
              <StatusTag :status="vo.workOrder.status" />
              <PriorityTag :priority="vo.workOrder.priority" style="margin-left: 6px" />
            </div>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="工单编号">{{ vo.workOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="工单类型">
            {{ labelOf(workTypeMap, vo.workOrder.workType) }}
          </el-descriptions-item>
          <el-descriptions-item label="来源渠道">{{ labelOf(sourceChannelMap, vo.workOrder.sourceChannel) }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ vo.workOrder.userName }}（ID {{ vo.workOrder.userId }}）</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ vo.workOrder.handlerName || '未分配' }}</el-descriptions-item>
          <el-descriptions-item label="关联订单">{{ vo.detail?.relatedOrderId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="3">{{ vo.workOrder.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(vo.workOrder.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDateTime(vo.workOrder.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="工单ID">#{{ vo.workOrder.id }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="14">
          <el-card>
            <template #header>
              <div class="card__header">
                <span>用户原始诉求</span>
                <el-button
                  v-if="canReply"
                  type="primary"
                  size="small"
                  @click="openReply"
                >回复用户</el-button>
              </div>
            </template>
            <div class="content-block">{{ vo.detail?.content || '无内容' }}</div>

            <div class="section-title">附件</div>
            <div v-if="attachments.length" class="attachments">
              <el-tag v-for="(item, idx) in attachments" :key="idx" type="info" effect="plain">{{ item }}</el-tag>
            </div>
            <div v-else class="muted">暂无附件</div>
          </el-card>

          <el-card style="margin-top: 16px">
            <template #header>
              <div class="card__header">
                <span>AI 回复草稿</span>
                <el-button v-if="vo.detail?.aiDraftReply" size="small" @click="copyDraft">复制</el-button>
              </div>
            </template>
            <div v-if="vo.detail?.aiDraftReply" class="content-block">{{ vo.detail.aiDraftReply }}</div>
            <div v-else class="muted">暂无草稿，可在客服工作台生成</div>
          </el-card>
        </el-col>

        <el-col :span="10">
          <el-card>
            <template #header>
              <div class="card__header"><span>AI 结构化字段</span></div>
            </template>
            <el-table v-if="structuredRows.length" :data="structuredRows" size="small" border>
              <el-table-column prop="label" label="字段" width="130" />
              <el-table-column prop="value" label="值" />
            </el-table>
            <div v-else class="muted">尚未完成结构化</div>
          </el-card>

          <el-card style="margin-top: 16px">
            <template #header>
              <div class="card__header">
                <span>操作日志</span>
                <el-button
                  v-if="vo.workOrder.status !== 'CLOSED'"
                  type="danger"
                  size="small"
                  plain
                  @click="closeOrder"
                >关闭工单</el-button>
              </div>
            </template>
            <el-timeline v-if="vo.logs?.length">
              <el-timeline-item
                v-for="log in vo.logs"
                :key="log.id"
                :timestamp="formatDateTime(log.createTime)"
                placement="top"
              >
                <div class="log-title">
                  {{ labelOf(actionMap, log.action, log.action) }}
                  <span class="muted">· {{ labelOf(operatorTypeMap, log.operatorType) }} {{ log.operatorId }}</span>
                </div>
                <div class="muted" style="font-size: 12px; margin-top: 2px">
                  {{ log.beforeStatus || '-' }} → {{ log.afterStatus || '-' }}
                </div>
                <div v-if="log.content" style="margin-top: 4px">{{ log.content }}</div>
              </el-timeline-item>
            </el-timeline>
            <div v-else class="muted">暂无操作日志</div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <el-dialog v-model="replyVisible" title="回复用户" width="520px">
      <el-input v-model="replyContent" type="textarea" :rows="6" placeholder="请输入回复内容" />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">发送回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import PriorityTag from '../../components/PriorityTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import { closeWorkOrder, getWorkOrderDetail, replyWorkOrder } from '../../api/workorder'
import { useIdentityStore } from '../../store/identity'
import { actionMap, labelOf, operatorTypeMap, priorityMap, sourceChannelMap, workTypeMap } from '../../utils/dict'
import { formatDateTime, parseJson } from '../../utils/format'

const route = useRoute()
const identity = useIdentityStore()

const vo = reactive({ workOrder: null, detail: null, logs: [] })
const loading = ref(false)

const structureLabels = {
  workType: '工单类型',
  title: '标题',
  amount: '涉及金额',
  appealLevel: '诉求等级',
  relatedOrderId: '关联订单号',
  complaintIntent: '投诉意向',
  priority: '优先级'
}

const attachments = computed(() => {
  const list = parseJson(vo.detail?.attachments, [])
  return Array.isArray(list) ? list : []
})

const structuredRows = computed(() => {
  const obj = parseJson(vo.detail?.structuredFields, null)
  if (!obj || typeof obj !== 'object') return []
  return Object.entries(obj).map(([key, value]) => {
    let text = value
    if (typeof value === 'boolean') text = value ? '是' : '否'
    else if (value === null || value === undefined) text = '-'
    else if (key === 'workType') text = labelOf(workTypeMap, value)
    else if (key === 'priority') text = labelOf(priorityMap, value)
    if (Array.isArray(value)) text = value.join('、')
    return { label: structureLabels[key] || key, value: String(text) }
  })
})

const canReply = computed(() =>
  ['PENDING_HANDLE', 'PROCESSING'].includes(vo.workOrder?.status)
)

const load = async () => {
  loading.value = true
  try {
    const data = await getWorkOrderDetail(route.params.id)
    vo.workOrder = data?.workOrder || null
    vo.detail = data?.detail || null
    vo.logs = data?.logs || []
  } finally {
    loading.value = false
  }
}

const replyVisible = ref(false)
const replyContent = ref('')
const replying = ref(false)

const openReply = () => {
  if (!identity.handlerId) {
    ElMessage.warning('请先在右上角选择客服身份')
    return
  }
  replyContent.value = vo.detail?.aiDraftReply || ''
  replyVisible.value = true
}

const submitReply = async () => {
  if (!replyContent.value.trim()) return ElMessage.warning('回复内容不能为空')
  replying.value = true
  try {
    await replyWorkOrder(route.params.id, {
      handlerId: identity.handlerId,
      content: replyContent.value.trim()
    })
    ElMessage.success('回复已发送')
    replyVisible.value = false
    load()
  } finally {
    replying.value = false
  }
}

const closeOrder = async () => {
  await ElMessageBox.confirm('关闭后工单不可再流转，确认关闭？', '关闭工单', { type: 'warning' })
  await closeWorkOrder(route.params.id, '网页端人工关闭')
  ElMessage.success('工单已关闭')
  load()
}

const copyDraft = async () => {
  await navigator.clipboard.writeText(vo.detail?.aiDraftReply || '')
  ElMessage.success('已复制到剪贴板')
}

onMounted(() => {
  load()
  identity.loadCsList().catch(() => {})
})
</script>

<style scoped>
.section-title { font-size: 13px; font-weight: 600; margin: 18px 0 8px; color: #4b5563; }
.attachments { display: flex; flex-wrap: wrap; gap: 6px; }
.log-title { font-weight: 600; font-size: 13px; }
</style>
