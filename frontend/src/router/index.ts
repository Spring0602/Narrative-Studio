import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import ProjectListView from '@/views/ProjectListView.vue'
import WorkspaceView from '@/views/WorkspaceView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // TODO(B): 后端登录联调完成后删除临时工作台预览路由
    ...(import.meta.env.DEV
      ? [{ path: '/preview/projects/:id', component: WorkspaceView }]
      : []),
    { path: '/', redirect: '/projects' },
    { path: '/login', component: LoginView },
    { path: '/projects', component: ProjectListView, meta: { requiresAuth: true } },
    { path: '/projects/:id', component: WorkspaceView, meta: { requiresAuth: true } },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !localStorage.getItem('narrative_token')) return '/login'
})

export default router
