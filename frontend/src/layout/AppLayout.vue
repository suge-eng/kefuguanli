<template>
  <el-container class="layout">
    <el-aside width="220px" class="sider">
      <div class="brand">
        <el-icon :size="22"><Headset /></el-icon>
        <span>售后调度平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="menu"
        background-color="#1f2937"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.name }}</span>
        </el-menu-item>
      </el-menu>
      <div class="sider-footer">智能售后工单全链路调度与处理</div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ route.meta.title || '售后调度平台' }}</div>
        <div class="header-right">
          <span class="muted">当前身份</span>
          <el-select
            :model-value="identity.handlerId"
            style="width: 140px"
            placeholder="选择客服"
            @change="onIdentityChange"
          >
            <el-option v-for="cs in identity.csList" :key="cs.id" :label="cs.name" :value="cs.id" />
          </el-select>
          <el-tag v-if="currentCs" :type="currentCs.onlineStatus === 'ONLINE' ? 'success' : 'info'" effect="plain">
            {{ currentCs.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
          </el-tag>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useIdentityStore } from '../store/identity'

const route = useRoute()
const identity = useIdentityStore()

const menus = [
  { path: '/dashboard', name: '数据看板', icon: 'DataAnalysis' },
  { path: '/work-orders', name: '工单管理', icon: 'Tickets' },
  { path: '/workbench', name: '客服工作台', icon: 'Service' },
  { path: '/cs', name: '客服管理', icon: 'User' },
  { path: '/strategy', name: '派单策略', icon: 'SetUp' },
  { path: '/knowledge', name: '知识库', icon: 'Collection' },
  { path: '/warning', name: '预警规则', icon: 'Warning' }
]

// 详情页也高亮所属列表菜单
const activeMenu = computed(() => route.meta.activeMenu || route.path)

const currentCs = computed(() => identity.csList.find((cs) => cs.id === identity.handlerId) || null)

const onIdentityChange = (id) => {
  const cs = identity.csList.find((item) => item.id === id)
  identity.setIdentity(cs)
}

onMounted(() => {
  identity.loadCsList().catch(() => {})
})
</script>

<style scoped>
.layout { height: 100vh; }
.sider {
  background: var(--app-sider-bg);
  display: flex;
  flex-direction: column;
}
.brand {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.menu { border-right: none; flex: 1; padding-top: 8px; }
.menu :deep(.el-menu-item) { height: 44px; margin: 2px 8px; border-radius: 6px; }
.menu :deep(.el-menu-item.is-active) { background: var(--app-primary) !important; }
.sider-footer {
  padding: 14px 18px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.6;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid var(--app-card-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-title { font-size: 16px; font-weight: 600; }
.header-right { display: flex; align-items: center; gap: 10px; }
.main { background: var(--app-bg); padding: 0; overflow-y: auto; }
</style>
