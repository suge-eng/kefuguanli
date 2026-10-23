<template>
  <div class="page-wrap">
    <PageHeader title="派单策略" subtitle="策略热切换，同一时刻只有一条启用；切换后立即影响新派单" />

    <el-card>
      <el-table v-loading="loading" :data="rows" empty-text="暂无策略" :row-class-name="rowClass">
        <el-table-column prop="strategyCode" label="策略编码" width="140" />
        <el-table-column prop="strategyName" label="策略名称" width="160" />
        <el-table-column label="权重配置" min-width="300">
          <template #default="{ row }">
            <pre v-if="row.weights" class="json-block" style="margin: 0">{{ prettyJson(row.weights) }}</pre>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.enabled === 1" type="success" effect="light">已启用</el-tag>
            <el-tag v-else type="info" effect="plain">未启用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.enabled === 1" @click="enable(row)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import { enableStrategy, strategyList } from '../../api/dispatch'
import { prettyJson } from '../../utils/format'

const rows = ref([])
const loading = ref(false)

const rowClass = ({ row }) => (row.enabled === 1 ? 'enabled-row' : '')

const load = async () => {
  loading.value = true
  try {
    rows.value = (await strategyList()) || []
  } finally {
    loading.value = false
  }
}

const enable = async (row) => {
  await ElMessageBox.confirm(
    `启用「${row.strategyName}」后将自动禁用其他策略，确认切换？`,
    '切换派单策略',
    { type: 'warning' }
  )
  await enableStrategy(row.strategyCode)
  ElMessage.success(`已启用 ${row.strategyName}`)
  load()
}

onMounted(load)
</script>

<style scoped>
:deep(.enabled-row) { background: #f0f9eb !important; }
</style>
