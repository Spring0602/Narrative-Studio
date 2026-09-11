<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  createProject,
  listProjects,
  type ProjectSummary,
} from "@/api/projects";

const router = useRouter();
const projects = ref<ProjectSummary[]>([]);
const loading = ref(true);
const dialogVisible = ref(false);
const saving = ref(false);
const form = reactive({ name: "", description: "" });
const user = JSON.parse(localStorage.getItem("narrative_user") || "{}");

async function load() {
  loading.value = true;
  try {
    projects.value = await listProjects();
  } catch {
    ElMessage.error("项目列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function create() {
  if (!form.name.trim()) return ElMessage.warning("请输入项目名称");
  saving.value = true;
  try {
    const project = await createProject({
      name: form.name,
      description: form.description || undefined,
    });
    dialogVisible.value = false;
    await router.push(`/projects/${project.id}`);
  } catch (error: any) {
    ElMessage.error(error.response?.data?.error?.message || "创建失败");
  } finally {
    saving.value = false;
  }
}

function logout() {
  localStorage.removeItem("narrative_token");
  localStorage.removeItem("narrative_user");
  router.push("/login");
}

onMounted(load);
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="logo">叙事工坊 <span>STUDIO</span></div>
      <div class="user-actions">
        <span>{{ user.displayName || user.username }}</span
        ><el-button text @click="logout">退出</el-button>
      </div>
    </header>
    <main class="content">
      <div class="page-heading">
        <div>
          <p class="eyebrow">PROJECTS</p>
          <div class="heading-title-row">
            <img
              class="page-heading-icon"
              src="../assets/icons/quill-writing.png"
              alt=""
              aria-hidden="true"
            />
            <h1>你的剧情项目</h1>
          </div>
          <p class="muted">从结构化设定开始，逐步搭出一条能走通的故事。</p>
        </div>
        <el-button type="primary" size="large" @click="dialogVisible = true"
          >新建项目</el-button
        >
      </div>
      <el-skeleton v-if="loading" :rows="5" animated />
      <div v-else-if="projects.length" class="project-grid">
        <article
          v-for="item in projects"
          :key="item.id"
          class="project-card"
          @click="router.push(`/projects/${item.id}`)"
        >
          <div class="card-top">
            <span class="role-tag">{{ item.memberRole }}</span
            ><span class="status-dot">{{ item.status }}</span>
          </div>
          <h3>{{ item.name }}</h3>
          <p>{{ item.description || "尚未填写项目简介。" }}</p>
          <footer>
            更新于 {{ new Date(item.updatedAt).toLocaleString() }}
          </footer>
        </article>
      </div>
      <el-empty v-else description="还没有项目，先创建第一个互动故事吧" />
    </main>
    <el-dialog v-model="dialogVisible" title="新建剧情项目" width="480px">
      <el-form label-position="top">
        <el-form-item label="项目名称"
          ><el-input v-model="form.name" maxlength="100" show-word-limit
        /></el-form-item>
        <el-form-item label="项目简介"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="dialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="create"
          >创建</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
