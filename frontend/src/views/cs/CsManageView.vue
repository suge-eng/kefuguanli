<template>
  <div class="page-wrap">
    <PageHeader title="客服管理" subtitle="客服档案、技能标签与在线状态；负载取自 Redis 实时计数">
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增客服</el-button>
    </PageHeader>

    <el-card>
      <div class="toolbar" style="margin-bottom: 14px">
        <el-select v-model="onlineStatus" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option label="在线" value="ONLINE" />
          <el-option label="离线" value="OFFLINE" />
        </el-select>
        <el-button :icon="Refresh" @click="reload">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" empty-text="暂无客服">
        <el-table-column prop="account" label="登录账号" width="130" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="skillTags" label="技能标签" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="tag in splitTags(row.skillTags)" :key="tag" size="small" effect="plain" style="margin-right: 4px">
              {{ tag }}
            </el-tag>
            <span v-if="!splitTags(row.skillTags).length" class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="实时负载" width="130">
          <template #default="{ row }">{{ loadOf(row.id) }} / {{ row.maxLoad }}</template>
        </el-table-column>
        <el-table-column label="历史解决率" width="120">
          <template #default="{ row }">{{ row.resolveRate ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="在线状态" width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="row.onlineStatus === 'ONLINE'"
              inline-prompt
              active-text="在线"
              inactive-text="离线"
              @change="(val) => toggleOnline(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑客服' : '新增客服'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="登录账号" prop="account">
          <el-input v-model="form.account" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="密码" :prop="form.id ? '' : 'password'">
          <el-input v-model="form.password" placeholder="演示项目明文存储" />
        </el-form-item>
        <el-form-item label="技能标签">
          <el-input v-model="form.skillTags" placeholder="逗号分隔，如 退货,退款,物流" />
        </el-form-item>
        <el-form-item label="最大并发">
          <el-input-number v-model="form.maxLoad" :min="1" :max="99" controls-position="right" />
        </el-form-item>
        <el-form-item label="历史解决率">
          <el-input v-model="form.resolveRate" placeholder="如 0.85" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import { csCreate, csOffline, csOnline, csPage, csUpdate } from '../../api/dispatch'
import { dashboardCsLoad } from '../../api/stats'

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const onlineStatus = ref('')
const loading = ref(false)
const loadMap = ref({})

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, account: '', name: '', password: '', skillTags: '', maxLoad: 5, resolveRate: '' })
const rules = {
  account: [{ required: true, message: '请填写登录账号', trigger: 'blur' }],
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请填写密码', trigger: 'blur' }]
}

const splitTags = (text) => (text ? text.split(',').map((item) => item.trim()).filter(Boolean) : [])
const loadOf = (id) => loadMap.value[id] ?? 0

const load = async () => {
  loading.value = true
  try {
    const data = await csPage({ pageNum: pageNum.value, pageSize: pageSize.value, onlineStatus: onlineStatus.value || undefined })
    rows.value = data?.records || []
    total.value = data?.total || 0
    const loadData = await dashboardCsLoad()
    loadMap.value = Object.fromEntries((loadData || []).map((item) => [item.csId, item.load ?? 0]))
  } finally {
    loading.value = false
  }
}

const reload = () => load()

const toggleOnline = async (row, val) => {
  if (val) await csOnline(row.id)
  else await csOffline(row.id)
  ElMessage.success(val ? `${row.name} 已上线` : `${row.name} 已下线`)
  load()
}

const openDialog = (row) => {
  Object.assign(form, {
    id: row?.id || null,
    account: row?.account || '',
    name: row?.name || '',
    password: '',
    skillTags: row?.skillTags || '',
    maxLoad: row?.maxLoad || 5,
    resolveRate: row?.resolveRate ?? ''
  })
  dialogVisible.value = true
}

const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      // 编辑时不改密码则不清空原密码（后端会按非空字段更新）
      await csUpdate({ ...form, password: form.password || undefined })
    } else {
      await csCreate({ ...form, onlineStatus: 'OFFLINE' })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
