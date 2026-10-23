<template>
  <div class="page-wrap">
    <PageHeader title="预警规则" subtitle="规则配置存 MySQL，启停即时生效；命中后按动作发事件或升级工单">
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </PageHeader>

    <el-card>
      <el-table v-loading="loading" :data="rows" empty-text="暂无预警规则">
        <el-table-column prop="ruleCode" label="规则编码" width="180" />
        <el-table-column prop="ruleName" label="规则名称" width="170" />
        <el-table-column label="规则类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ typeLabel(row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发条件" min-width="300">
          <template #default="{ row }">
            <pre class="json-block" style="margin: 0">{{ prettyJson(row.conditionConfig) }}</pre>
          </template>
        </el-table-column>
        <el-table-column label="命中动作" width="150">
          <template #default="{ row }">
            <el-tag :type="row.action === 'ESCALATE' ? 'danger' : 'warning'" size="small" effect="light">
              {{ row.action === 'ESCALATE' ? '升级工单' : '仅发预警' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="(val) => toggle(row, val)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import { toggleWarningRule, warningRules } from '../../api/ai'
import { prettyJson } from '../../utils/format'

const rows = ref([])
const loading = ref(false)

const typeLabels = {
  KEYWORD: '关键词',
  REPEAT: '重复诉求',
  TIMEOUT: '超时未处理',
  HIGH_VALUE: '高金额',
  SENTIMENT: '投诉情绪'
}
const typeLabel = (type) => typeLabels[type] || type

const load = async () => {
  loading.value = true
  try {
    rows.value = (await warningRules()) || []
  } finally {
    loading.value = false
  }
}

const toggle = async (row, enabled) => {
  await toggleWarningRule(row.ruleCode, enabled)
  row.enabled = enabled ? 1 : 0
  ElMessage.success(`${row.ruleName} 已${enabled ? '启用' : '停用'}`)
}

onMounted(load)
</script>
