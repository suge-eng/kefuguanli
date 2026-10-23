<template>
  <div class="page-wrap">
    <PageHeader title="知识库" subtitle="MySQL 为唯一事实源，向量副本同步到 Milvus">
      <el-button :icon="Refresh" :loading="vectorizing" @click="syncPending">同步待向量化</el-button>
      <el-button type="warning" :icon="RefreshRight" :loading="rebuilding" @click="rebuildAll">全量重建向量</el-button>
    </PageHeader>

    <el-card>
      <div class="toolbar" style="margin-bottom: 14px">
        <el-select v-model="category" placeholder="全部分类" clearable filterable allow-create style="width: 180px" @change="search">
          <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
        </el-select>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" empty-text="暂无知识条目">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.category }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="320" show-overflow-tooltip />
        <el-table-column label="向量状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.vectorized === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.vectorized === 1 ? '已向量化' : '待同步' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-drawer v-model="drawerVisible" :title="current?.title || '知识详情'" size="520px">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="分类">{{ current.category }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ current.status }}</el-descriptions-item>
          <el-descriptions-item label="向量化">{{ current.vectorized === 1 ? '已同步' : '待同步' }}</el-descriptions-item>
        </el-descriptions>
        <div class="section-title">解决方案正文</div>
        <div class="content-block">{{ current.content }}</div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshRight } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import { knowledgePage, rebuildKnowledge, vectorizeKnowledge } from '../../api/ai'
import { formatDateTime } from '../../utils/format'

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const category = ref('')
const loading = ref(false)
const vectorizing = ref(false)
const rebuilding = ref(false)
const drawerVisible = ref(false)
const current = ref(null)

const categories = computed(() => [...new Set(rows.value.map((row) => row.category).filter(Boolean))])

const load = async () => {
  loading.value = true
  try {
    const data = await knowledgePage({ pageNum: pageNum.value, pageSize: pageSize.value, category: category.value || undefined })
    rows.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  pageNum.value = 1
  load()
}

const openDetail = (row) => {
  current.value = row
  drawerVisible.value = true
}

const syncPending = async () => {
  vectorizing.value = true
  try {
    await vectorizeKnowledge()
    ElMessage.success('待同步条目已写入 Milvus')
    load()
  } finally {
    vectorizing.value = false
  }
}

const rebuildAll = async () => {
  await ElMessageBox.confirm('将清空向量集合并逐条重新调用 embedding 模型，耗时可能数十秒，确认执行？', '全量重建向量', {
    type: 'warning'
  })
  rebuilding.value = true
  try {
    await rebuildKnowledge()
    ElMessage.success('向量集合已全量重建')
    load()
  } finally {
    rebuilding.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.section-title { font-size: 13px; font-weight: 600; margin: 16px 0 8px; color: #4b5563; }
</style>
