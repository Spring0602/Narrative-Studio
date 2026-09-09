import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import MemberManagementView from '@/views/MemberManagementView.vue'
import ProjectListView from '@/views/ProjectListView.vue'
import WorkspaceView from '@/views/WorkspaceView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // TODO(B): 正式接入项目工作台后删除临时预览路由
    { path: '/preview/members', component: MemberManagementView },
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
