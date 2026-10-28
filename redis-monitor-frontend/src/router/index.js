import Vue from 'vue'
import VueRouter from 'vue-router'
import { resetRequestCancelToken } from '@/api'

Vue.use(VueRouter)

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '监控仪表' } },
  { path: '/realtime', name: 'Realtime', component: () => import('@/views/RealtimeMonitor.vue'), meta: { title: '实时监控' } },
  { path: '/history', name: 'History', component: () => import('@/views/HistoryMonitor.vue'), meta: { title: '历史监控' } },
  { path: '/alarm-rules', name: 'AlarmRules', component: () => import('@/views/AlarmRules.vue'), meta: { title: '告警监控' } },
  { path: '/alarm-records', name: 'AlarmRecords', component: () => import('@/views/AlarmRecords.vue'), meta: { title: '告警记录' } }
]

const router = new VueRouter({ routes })

// 路由切换时取消上一页未完成的请求，避免慢请求回写已销毁页面
router.beforeEach((to, from, next) => {
  resetRequestCancelToken()
  next()
})

export default router
