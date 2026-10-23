<template>
  <div class="page-wrap">
    <PageHeader title="客服工作台" subtitle="按当前身份处理我的工单：领取、AI 草稿、回复、解决、转交">
      <el-button :icon="Refresh" @click="reloadAll">刷新</el-button>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="10">
        <el-card>
          <template #header>
            <div class="card__header">
              <span>我的工单</span>
              <el-button v-if="selectedRows.length" type="primary" size="small" :loading="batchLoading" @click="batchClaimOrders">
                批量领取（{{ selectedRows.length }}）
              </el-button>
            </div>
          </template>

          <div class="toolbar" style="margin-bottom: 12px">
            <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="search">
              <el-option v-for="(item, key) in statusMap" :key="key" :label="item.label" :value="key" />
            </el-select>
            <el-tag v-if="identity.handlerName" effect="plain">处理人：{{ identity.handlerName }}</el-tag>
          </div>

          <el-table
            ref="tableRef"
            v-loading="loading"
            :data="rows"
            size="small"
            empty-text="暂无工单"
            @row-click="selectOrder"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="42" />
            <el-table-column prop="orderNo" label="工单编号" width="170" />
            <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
            <el-table-column label="优先级" width="80">
              <template #default="{ row }"><PriorityTag :priority="row.priority" /></template>
            </el-table-column>
            <el-table-column label="状态" width="95">
              <template #default="{ row }"><StatusTag :status="row.status" /></template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="query.pageNum"
              v-model:page-size="query.pageSize"
              :total="total"
              layout="total, prev, pager, next"
              small
              background
              @current-change="load"
            />
          </div>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card v-if="current" v-loading="detailLoading">
          <template #header>
            <div class="card__header">
              <span>工单 {{ current.workOrder?.orderNo }}</span>
              <div>
                <StatusTag :status="current.workOrder?.status" />
                <PriorityTag :priority="current.workOrder?.priority" style="margin-left: 6px" />
              </div>
            </div>
          </template>

          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="用户">{{ current.workOrder?.userName }}（#{{ current.workOrder?.userId }}）</el-descriptions-item>
            <el-descriptions-item label="类型">{{ labelOf(workTypeMap, current.workOrder?.workType) }}</el-descriptions-item>
            <el-descriptions-item label="处理人">{{ current.workOrder?.handlerName || '未分配' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(current.workOrder?.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="关联订单">{{ current.detail?.relatedOrderId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="附件">
              <span v-if="attachments.length">{{ attachments.length }} 个</span>
              <span v-else class="muted">无</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">用户诉求</div>
          <div class="content-block">{{ current.detail?.content || '-' }}</div>

          <div class="section-title">回复内容</div>
          <el-input v-model="replyContent" type="textarea" :rows="5" placeholder="可点击 AI 生成草稿，再人工修改后发送" />
          <div class="action-row">
            <el-button :loading="draftLoading" :icon="MagicStick" @click="generateDraft">AI 生成草稿</el-button>
            <el-button type="primary" :loading="replying" @click="sendReply">发送回复</el-button>
          </div>

          <div class="section-title">工单操作</div>
          <div class="action-row">
            <el-button size="small" :disabled="current.workOrder?.status !== 'PENDING_HANDLE'" @click="claimCurrent">领取</el-button>
            <el-button size="small" type="success" :disabled="current.workOrder?.status !== 'PROCESSING'" @click="resolveCurrent">标记解决</el-button>
            <el-button size="small" @click="transferVisible = true">转交</el-button>
            <el-button size="small" type="danger" plain @click="closeCurrent">关闭</el-button>
            <el-upload :show-file-list="false" :http-request="uploadFile" style="display: inline-block; margin-left: 10px">
              <el-button size="small" :loading="uploading">上传附件</el-button>
            </el-upload>
          </div>

          <div class="section-title">操作记录</div>
          <el-timeline v-if="current.logs?.length">
            <el-timeline-item v-for="log in current.logs" :key="log.id" :timestamp="formatDateTime(log.createTime)" placement="top">
              <div class="log-title">
                {{ labelOf(actionMap, log.action, log.action) }}
                <span class="muted">· {{ labelOf(operatorTypeMap, log.operatorType) }} {{ log.operatorId }}</span>
              </div>
              <div v-if="log.content" class="muted" style="font-size: 12px">{{ log.content }}</div>
            </el-timeline-item>
          </el-timeline>
          <div v-else class="muted">暂无记录</div>
        </el-card>

        <el-card v-else>
          <el-empty description="从左侧选择一张工单开始处理" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="transferVisible" title="转交工单" width="420px">
      <el-form label-width="90px">
        <el-form-item label="转交给">
          <el-select v-model="transferTarget" placeholder="选择客服" style="width: 100%">
            <el-option
              v-for="cs in otherCsList"
              :key="cs.id"
              :label="`${cs.name}（${cs.onlineStatus === 'ONLINE' ? '在线' : '离线'}）`"
              :value="cs.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="转交原因">
          <el-input v-model="transferReason" placeholder="如 该工单需要退款权限" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferring" @click="submitTransfer">确认转交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Refresh } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import PriorityTag from '../../components/PriorityTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import {
  batchClaim,
  claimOrder,
  closeOrder,
  myPage,
  replyDraft,
  replyOrder,
  resolveOrder,
  transferOrder,
  uploadAttachment,
  workbenchDetail
} from '../../api/workbench'
import { useIdentityStore } from '../../store/identity'
import { actionMap, labelOf, operatorTypeMap, statusMap, workTypeMap } from '../../utils/dict'
import { formatDateTime, parseJson } from '../../utils/format'

const identity = useIdentityStore()
const query = reactive({ pageNum: 1, pageSize: 10, status: 'PENDING_HANDLE' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const tableRef = ref(null)
const selectedRows = ref([])

const current = ref(null)
const detailLoading = ref(false)
const replyContent = ref('')
const draftLoading = ref(false)
const replying = ref(false)
const batchLoading = ref(false)

const transferVisible = ref(false)
const transferTarget = ref(null)
const transferReason = ref('')
const transferring = ref(false)
const uploading = ref(false)

const attachments = computed(() => {
  const list = parseJson(current.value?.detail?.attachments, [])
  return Array.isArray(list) ? list : []
})

const otherCsList = computed(() => identity.csList.filter((cs) => cs.id !== identity.handlerId))

const load = async () => {
  if (!identity.handlerId) return
  loading.value = true
  try {
    const data = await myPage({
      handlerId: identity.handlerId,
      status: query.status || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.pageNum = 1
  load()
}

const selectOrder = async (row) => {
  detailLoading.value = true
  try {
    current.value = await workbenchDetail(row.id)
    replyContent.value = current.value?.detail?.aiDraftReply || ''
  } finally {
    detailLoading.value = false
  }
}

const reloadAll = async () => {
  await load()
  if (current.value?.workOrder?.id) {
    current.value = await workbenchDetail(current.value.workOrder.id)
  }
}

const requireIdentity = () => {
  if (!identity.handlerId) {
    ElMessage.warning('请先在右上角选择客服身份')
    return false
  }
  return true
}

const onSelectionChange = (selection) => {
  selectedRows.value = selection
}

const batchClaimOrders = async () => {
  if (!requireIdentity()) return
  batchLoading.value = true
  try {
    const result = await batchClaim({
      ids: selectedRows.value.map((row) => row.id),
      handlerId: identity.handlerId
    })
    const successCount = result?.successIds?.length || 0
    ElMessage.success(`成功领取 ${successCount} 张工单`)
    tableRef.value?.clearSelection()
    reloadAll()
  } finally {
    batchLoading.value = false
  }
}

const currentId = () => current.value?.workOrder?.id

const claimCurrent = async () => {
  if (!requireIdentity()) return
  await claimOrder(currentId(), identity.handlerId)
  ElMessage.success('已领取，请开始处理')
  reloadAll()
}

const generateDraft = async () => {
  draftLoading.value = true
  try {
    const text = await replyDraft(currentId())
    replyContent.value = text || ''
    ElMessage.success('AI 草稿已生成，请审核后发送')
  } finally {
    draftLoading.value = false
  }
}

const sendReply = async () => {
  if (!requireIdentity()) return
  if (!replyContent.value.trim()) return ElMessage.warning('回复内容不能为空')
  replying.value = true
  try {
    await replyOrder(currentId(), { handlerId: identity.handlerId, content: replyContent.value.trim() })
    ElMessage.success('回复已发送')
    reloadAll()
  } finally {
    replying.value = false
  }
}

const resolveCurrent = async () => {
  if (!requireIdentity()) return
  await resolveOrder(currentId(), identity.handlerId)
  ElMessage.success('工单已标记解决')
  reloadAll()
}

const submitTransfer = async () => {
  if (!transferTarget.value) return ElMessage.warning('请选择转交客服')
  const target = identity.csList.find((cs) => cs.id === transferTarget.value)
  transferring.value = true
  try {
    await transferOrder(currentId(), {
      operatorId: identity.handlerId,
      newHandlerId: target.id,
      newHandlerName: target.name,
      reason: transferReason.value || '工作台转交'
    })
    ElMessage.success(`已转交给 ${target.name}`)
    transferVisible.value = false
    transferReason.value = ''
    reloadAll()
  } finally {
    transferring.value = false
  }
}

const closeCurrent = async () => {
  await closeOrder(currentId(), String(identity.handlerId || 'web-console'), '工作台人工关闭')
  ElMessage.success('工单已关闭')
  reloadAll()
}

const uploadFile = async ({ file }) => {
  uploading.value = true
  try {
    await uploadAttachment(currentId(), String(identity.handlerId || 'unknown'), file)
    ElMessage.success('附件已上传并关联工单')
    reloadAll()
  } finally {
    uploading.value = false
  }
}

onMounted(async () => {
  await identity.loadCsList().catch(() => {})
  load()
})
</script>

<style scoped>
.section-title { font-size: 13px; font-weight: 600; margin: 16px 0 8px; color: #4b5563; }
.action-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 10px; }
.action-row .el-button + .el-button { margin-left: 0; }
.log-title { font-weight: 600; font-size: 13px; }
</style>
