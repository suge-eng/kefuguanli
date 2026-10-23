import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layout/AppLayout.vue'

const routes = [
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/dashboard/DashboardView.vue'),
        meta: { title: '数据看板' }
      },
      {
        path: 'work-orders',
        name: 'work-orders',
        component: () => import('../views/workorder/WorkOrderListView.vue'),
        meta: { title: '工单管理' }
      },
      {
        path: 'work-orders/:id',
        name: 'work-order-detail',
        component: () => import('../views/workorder/WorkOrderDetailView.vue'),
        meta: { title: '工单详情', activeMenu: '/work-orders' }
      },
      {
        path: 'workbench',
        name: 'workbench',
        component: () => import('../views/workbench/WorkbenchView.vue'),
        meta: { title: '客服工作台' }
      },
      {
        path: 'cs',
        name: 'cs',
        component: () => import('../views/cs/CsManageView.vue'),
        meta: { title: '客服管理' }
      },
      {
        path: 'strategy',
        name: 'strategy',
        component: () => import('../views/strategy/StrategyView.vue'),
        meta: { title: '派单策略' }
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('../views/knowledge/KnowledgeView.vue'),
        meta: { title: '知识库' }
      },
      {
        path: 'warning',
        name: 'warning',
        component: () => import('../views/warning/WarningView.vue'),
        meta: { title: '预警规则' }
      }
    ]
  }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
