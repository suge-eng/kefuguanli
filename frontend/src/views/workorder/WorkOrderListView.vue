<template>
  <div class="page-wrap">
    <PageHeader title="工单管理" subtitle="全量工单查询与新建，创建后由 AI 自动结构化并触发派单">
      <el-button type="primary" :icon="Plus" @click="openCreate">新建工单</el-button>
    </PageHeader>

    <el-card>
      <div class="toolbar" style="margin-bottom: 14px">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option v-for="(item, key) in statusMap" :key="key" :label="item.label" :value="key" />
        </el-select>
        <el-select v-model="query.workType" placeholder="全部类型" clearable style="width: 140px">
          <el-option v-for="(item, key) in workTypeMap" :key="key" :label="item.label" :value="key" />
        </el-select>
        <el-select v-model="query.handlerId" placeholder="全部处理人" clearable filterable style="width: 160px">
          <el-option v-for="cs in identity.csList" :key="cs.id" :label="cs.name" :value="cs.id" />
        </el-select>
        <el-input v-model="query.userId" placeholder="用户ID" clearable style="width: 140px" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" empty-text="暂无工单" @row-click="goDetail" style="cursor: pointer">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="工单编号" width="180" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.workType" size="small" effect="plain" :type="workTypeMap[row.workType]?.type || 'info'">
              {{ labelOf(workTypeMap, row.workType) }}
            </el-tag>
            <span v-else class="muted">待识别</span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }"><PriorityTag :priority="row.priority" /></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="用户" width="130">
          <template #default="{ row }">{{ row.userName || '-' }} <span class="muted">#{{ row.userId }}</span></template>
        </el-table-column>
        <el-table-column prop="handlerName" label="处理人" width="100">
          <template #default="{ row }">{{ row.handlerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-drawer v-model="createVisible" title="新建工单" size="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户ID" prop="userId">
          <el-input-number v-model="form.userId" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="用户姓名" prop="userName">
          <el-input v-model="form.userName" placeholder="如 张三" />
        </el-form-item>
        <el-form-item label="来源渠道">
          <el-select v-model="form.sourceChannel" style="width: 100%">
            <el-option label="网页" value="WEB" />
            <el-option label="接口" value="API" />
            <el-option label="平台" value="PLATFORM" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联订单">
          <el-input v-model="form.relatedOrderId" placeholder="电商平台订单号，可留空" />
        </el-form-item>
        <el-form-item label="诉求内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="请描述用户诉求原文，AI 将据此结构化" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import PriorityTag from '../../components/PriorityTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import { createWorkOrder, pageWorkOrders } from '../../api/workorder'
import { useIdentityStore } from '../../store/identity'
import { labelOf, statusMap, workTypeMap } from '../../utils/dict'
import { formatDateTime } from '../../utils/format'

const router = useRouter()
const identity = useIdentityStore()

const query = reactive({ pageNum: 1, pageSize: 10, status: '', workType: '', handlerId: null, userId: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)

const createVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ userId: 1, userName: '', sourceChannel: 'WEB', relatedOrderId: '', content: '' })
const rules = {
  userId: [{ required: true, message: '请填写用户ID', trigger: 'change' }],
  userName: [{ required: true, message: '请填写用户姓名', trigger: 'blur' }],
  content: [{ required: true, message: '请填写诉求内容', trigger: 'blur' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await pageWorkOrders({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      workType: query.workType || undefined,
      handlerId: query.handlerId || undefined,
      userId: query.userId || undefined
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

const reset = () => {
  Object.assign(query, { pageNum: 1, status: '', workType: '', handlerId: null, userId: '' })
  load()
}

const goDetail = (row) => router.push(`/work-orders/${row.id}`)

const openCreate = () => {
  Object.assign(form, { userId: 1, userName: '', sourceChannel: 'WEB', relatedOrderId: '', content: '' })
  createVisible.value = true
}

const submitCreate = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    await createWorkOrder({ ...form, userId: Number(form.userId) })
    ElMessage.success('工单已创建，AI 将在数秒内完成结构化与派单')
    createVisible.value = false
    query.pageNum = 1
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  load()
  identity.loadCsList().catch(() => {})
})
</script>
