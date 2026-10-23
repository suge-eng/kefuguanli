<template>
  <div class="page-wrap">
    <PageHeader title="数据看板" subtitle="指标按 MQ 事件异步累加，客服负载为 Redis 实时值">
      <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard label="今日工单总量" :value="overview.totalCount" suffix="单" :hint="`统计日期 ${overview.statDate || '-'}`" />
      </el-col>
      <el-col :span="6">
        <StatCard label="今日已解决" :value="overview.resolvedCount" suffix="单" color="#16a34a" :hint="`解决率 ${overview.resolveRate ?? 0}%`" />
      </el-col>
      <el-col :span="6">
        <StatCard label="今日升级" :value="overview.escalatedCount" suffix="单" color="#dc2626" hint="预警规则命中并升级" />
      </el-col>
      <el-col :span="6">
        <StatCard label="平均解决时长" :value="overview.avgResolveMinutes ?? 0" suffix="分钟" color="#2563eb" hint="按增量加权平均计算" />
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card__header">
              <span>近 7 天工单趋势</span>
              <span class="muted" style="font-size: 12px">每 30 秒自动刷新</span>
            </div>
          </template>
          <div ref="trendRef" class="chart" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card__header"><span>工单类型分布</span></div>
          </template>
          <div ref="typeRef" class="chart" />
        </el-card>
      </el-col>
    </el-row>

    <el-card class="card" style="margin-top: 16px">
      <template #header>
        <div class="card__header">
          <span>客服实时负载</span>
          <span class="muted" style="font-size: 12px">来源：Redis 负载计数 + 客服档案</span>
        </div>
      </template>
      <el-table :data="csRows" size="default" empty-text="暂无客服数据">
        <el-table-column prop="name" label="客服" min-width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.onlineStatus === 'ONLINE' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前负载 / 上限" width="140">
          <template #default="{ row }">{{ row.load ?? 0 }} / {{ row.maxLoad ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="负载占用率" min-width="220">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.loadRate ?? 0)" :stroke-width="10" :color="loadColor(row.loadRate)" />
          </template>
        </el-table-column>
        <el-table-column label="历史解决率" width="120">
          <template #default="{ row }">{{ row.resolveRate ?? '-' }}%</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import { dashboardCsLoad, dashboardOverview, dashboardTrend, dashboardTypeDistribution } from '../../api/stats'
import { labelOf, workTypeMap } from '../../utils/dict'

const overview = ref({})
const csRows = ref([])
const trendRef = ref(null)
const typeRef = ref(null)
let trendChart = null
let typeChart = null
let timer = null

const loadColor = (rate) => {
  const value = Number(rate ?? 0)
  if (value >= 80) return '#dc2626'
  if (value >= 50) return '#f59e0b'
  return '#2563eb'
}

const renderTrend = (points) => {
  if (!trendChart) return
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['工单总量', '已解决'], right: 0, top: 0 },
    grid: { left: 40, right: 20, top: 40, bottom: 28 },
    xAxis: { type: 'category', boundaryGap: false, data: points.map((p) => p.statDate) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '工单总量',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        itemStyle: { color: '#2563eb' },
        areaStyle: { opacity: 0.08, color: '#2563eb' },
        data: points.map((p) => p.totalCount ?? 0)
      },
      {
        name: '已解决',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        itemStyle: { color: '#16a34a' },
        data: points.map((p) => p.resolvedCount ?? 0)
      }
    ]
  })
}

const renderType = (items) => {
  if (!typeChart) return
  typeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 单 ({d}%)' },
    legend: { bottom: 0, icon: 'circle' },
    series: [
      {
        name: '工单类型',
        type: 'pie',
        radius: ['45%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}\n{d}%', fontSize: 12 },
        data: items.map((item) => ({
          name: labelOf(workTypeMap, item.workType, item.workType),
          value: item.totalCount ?? 0
        }))
      }
    ]
  })
}

const loadAll = async () => {
  try {
    const [ov, trend, types, cs] = await Promise.all([
      dashboardOverview(),
      dashboardTrend(7),
      dashboardTypeDistribution(7),
      dashboardCsLoad()
    ])
    overview.value = ov || {}
    csRows.value = cs || []
    renderTrend(trend || [])
    renderType(types || [])
  } catch (e) {
    // 错误提示已由 http 拦截器统一弹出
  }
}

onMounted(() => {
  trendChart = echarts.init(trendRef.value)
  typeChart = echarts.init(typeRef.value)
  loadAll()
  timer = setInterval(loadAll, 30000)
  window.addEventListener('resize', resize)
})

const resize = () => {
  trendChart?.resize()
  typeChart?.resize()
}

onBeforeUnmount(() => {
  clearInterval(timer)
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  typeChart?.dispose()
})
</script>

<style scoped>
.chart { height: 300px; width: 100%; }
</style>
