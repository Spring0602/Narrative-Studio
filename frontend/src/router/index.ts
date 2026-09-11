import { createRouter, createWebHistory } from "vue-router";
import LoginView from "@/views/LoginView.vue";
import ProjectListView from "@/views/ProjectListView.vue";
import WorkspaceView from "@/views/WorkspaceView.vue";
import AccountRecoveryView from "@/views/AccountRecoveryView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/projects" },
    { path: "/login", component: LoginView },
    { path: "/account-recovery", component: AccountRecoveryView },
    {
      path: "/projects",
      component: ProjectListView,
      meta: { requiresAuth: true },
    },
    {
      path: "/projects/:id",
      component: WorkspaceView,
      meta: { requiresAuth: true },
    },
  ],
});

router.beforeEach((to) => {
  const isLocalWorkspacePreview =
    import.meta.env.DEV &&
    to.path.startsWith("/projects/") &&
    to.query.preview === "1";

  if (
    to.meta.requiresAuth &&
    !localStorage.getItem("narrative_token") &&
    !isLocalWorkspacePreview
  )
    return "/login";
});

export default router;
