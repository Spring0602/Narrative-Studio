<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import {
  archiveProject,
  getProject,
  updateProject,
  type ProjectSummary,
} from "@/api/projects";
import MemberManagementView from "@/views/MemberManagementView.vue";
import WorldEntryManagementView from "@/views/WorldEntryManagementView.vue";
import CharacterManagementView from "@/views/CharacterManagementView.vue";
import StoryGraphView from "@/views/StoryGraphView.vue";
import StateVariableView from "@/views/StateVariableView.vue";
import PlaytestView from "@/views/PlaytestView.vue";
import IssueDetectionView from "@/views/IssueDetectionView.vue";
import CharacterDetailView from "@/views/CharacterDetailView.vue";
import FeedbackView from "@/views/FeedbackView.vue";
import AccountSettingsView from "@/views/AccountSettingsView.vue";
import compassIcon from "@/assets/icons/compass.png";
import profileCardIcon from "@/assets/icons/profile-card.png";
import openBookIcon from "@/assets/icons/open-book.png";
import relationshipRingsIcon from "@/assets/icons/relationship-rings.png";
import branchFlowIcon from "@/assets/icons/branch-flow.png";
import stackedLayersIcon from "@/assets/icons/stacked-layers.png";
import timelineNodesIcon from "@/assets/icons/timeline-nodes.png";
import documentSearchIcon from "@/assets/icons/document-search.png";
import growthSproutIcon from "@/assets/icons/growth-sprout.png";
import shieldCheckIcon from "@/assets/icons/shield-check.png";

const route = useRoute();
const router = useRouter();
const projectId = computed(() => Number(route.params.id));
const isPreviewMode = computed(
  () => import.meta.env.DEV && route.query.preview === "1",
);
const activeModule = ref("overview");
const focusedStoryNodeId = ref<number | null>(null);
const modules = [
  { key: "overview", label: "项目概览", icon: compassIcon },
  { key: "members", label: "成员管理", icon: profileCardIcon },
  { key: "world", label: "世界观", icon: openBookIcon },
  { key: "characters", label: "角色档案", icon: profileCardIcon },
  {
    key: "character-details",
    label: "关系与知识",
    icon: relationshipRingsIcon,
  },
  { key: "story", label: "剧情节点", icon: branchFlowIcon },
  { key: "variables", label: "状态变量", icon: stackedLayersIcon },
  { key: "playtest", label: "模拟测试", icon: timelineNodesIcon },
  { key: "issues", label: "问题检测", icon: documentSearchIcon },
  { key: "feedback", label: "测试反馈", icon: growthSproutIcon },
  { key: "account", label: "账户设置", icon: shieldCheckIcon },
];

const project = ref<ProjectSummary | null>(null);
const loading = ref(true);
const loadError = ref("");
const saving = ref(false);
const archiving = ref(false);
const editVisible = ref(false);
const editForm = reactive({ name: "", description: "" });

const canEditProject = computed(
  () =>
    project.value?.status === "ACTIVE" &&
    (project.value.memberRole === "OWNER" ||
      project.value.memberRole === "EDITOR"),
);
const canArchiveProject = computed(
  () =>
    project.value?.status === "ACTIVE" && project.value.memberRole === "OWNER",
);

async function loadProject() {
  loading.value = true;
  loadError.value = "";

  if (isPreviewMode.value) {
    project.value = {
      id: projectId.value || 1,
      name: "叙事工坊演示项目",
      description: "本地界面预览，用于检查页面布局、导航与图标显示。",
      ownerId: 0,
      status: "ARCHIVED",
      memberRole: "OWNER",
      updatedAt: new Date().toISOString(),
    };
    loading.value = false;
    return;
  }

  try {
    project.value = await getProject(projectId.value);
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "无法加载项目详情，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}

function locateStoryNode(nodeId: number) {
  focusedStoryNodeId.value = nodeId;
  activeModule.value = "story";
}

function openEditProject() {
  if (!project.value || !canEditProject.value) return;
  editForm.name = project.value.name;
  editForm.description = project.value.description || "";
  editVisible.value = true;
}

async function saveProject() {
  if (!project.value || !canEditProject.value || saving.value) return;
  const name = editForm.name.trim();
  if (!name) return void ElMessage.warning("请输入项目名称");

  saving.value = true;
  try {
    project.value = await updateProject(project.value.id, {
      name,
      description: editForm.description.trim() || undefined,
    });
    editVisible.value = false;
    ElMessage.success("项目资料已保存");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "保存项目资料失败"));
  } finally {
    saving.value = false;
  }
}

async function archiveCurrentProject() {
  if (!project.value || !canArchiveProject.value || archiving.value) return;
  try {
    await ElMessageBox.confirm(
      "归档后项目及其内容只能查看，不能继续编辑。确定归档吗？",
      "归档项目",
      {
        confirmButtonText: "确定归档",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
  } catch {
    return;
  }

  archiving.value = true;
  try {
    await archiveProject(project.value.id);
    project.value = { ...project.value, status: "ARCHIVED" };
    ElMessage.success("项目已归档");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "归档项目失败"));
  } finally {
    archiving.value = false;
  }
}

onMounted(loadProject);
</script>

<template>
  <div class="workspace">
    <aside>
      <button class="back" @click="router.push('/projects')">← 返回项目</button>
      <div class="workspace-title">
        {{ project?.name || `项目 #${route.params.id}` }}
      </div>
      <div v-if="isPreviewMode" class="workspace-preview-badge">
        本地界面预览
      </div>
      <nav>
        <button
          v-for="item in modules"
          :key="item.key"
          :class="{ active: activeModule === item.key }"
          @click="activeModule = item.key"
        >
          <span class="nav-label">
            <img class="nav-icon" :src="item.icon" alt="" aria-hidden="true" />
            {{ item.label }}
          </span>
        </button>
      </nav>
    </aside>
    <main>
      <el-skeleton v-if="loading" :rows="7" animated />
      <el-result
        v-else-if="loadError"
        icon="error"
        title="项目加载失败"
        :sub-title="loadError"
      >
        <template #extra>
          <el-button type="primary" @click="loadProject">重新加载</el-button>
        </template>
      </el-result>

      <MemberManagementView
        v-else-if="activeModule === 'members' && project"
        :current-user-role="project.memberRole"
        :project-status="project.status"
      />
      <WorldEntryManagementView
        v-else-if="activeModule === 'world' && project"
        :current-user-role="project.memberRole"
        :project-status="project.status"
      />
      <CharacterManagementView
        v-else-if="activeModule === 'characters' && project"
        :current-user-role="project.memberRole"
        :project-status="project.status"
      />
      <CharacterDetailView
        v-else-if="activeModule === 'character-details' && project"
        :current-user-role="project.memberRole"
        :project-status="project.status"
      />
      <StoryGraphView
        v-else-if="activeModule === 'story' && project"
        :current-user-role="project.memberRole"
        :project-status="project.status"
        :focus-node-id="focusedStoryNodeId"
      />
      <StateVariableView
        v-else-if="activeModule === 'variables' && project"
        :current-user-role="project.memberRole"
        :project-status="project.status"
      />
      <PlaytestView
        v-else-if="activeModule === 'playtest' && project"
        :current-user-role="project.memberRole"
        :project-status="project.status"
      />
      <IssueDetectionView
        v-else-if="activeModule === 'issues' && project"
        :current-user-role="project.memberRole"
        :project-status="project.status"
        @locate="locateStoryNode"
      />
      <FeedbackView
        v-else-if="activeModule === 'feedback' && project"
        :current-user-role="project.memberRole"
        :project-status="project.status"
      />
      <AccountSettingsView v-else-if="activeModule === 'account'" />

      <template v-else-if="activeModule === 'overview' && project">
        <p class="eyebrow">WORKSPACE</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/compass.png"
            alt=""
            aria-hidden="true"
          />
          <h1>{{ project.name }}</h1>
        </div>
        <div class="notice">
          <strong>项目资料</strong>
          <p>{{ project.description || "尚未填写项目简介。" }}</p>
          <div class="project-meta">
            <el-tag>{{ project.memberRole }}</el-tag>
            <el-tag :type="project.status === 'ACTIVE' ? 'success' : 'info'">
              {{ project.status === "ACTIVE" ? "进行中" : "已归档" }}
            </el-tag>
            <span
              >更新于
              {{ new Date(project.updatedAt).toLocaleString("zh-CN") }}</span
            >
          </div>
          <div class="project-actions">
            <el-button
              v-if="canEditProject"
              type="primary"
              @click="openEditProject"
              >编辑项目资料</el-button
            >
            <el-button
              v-if="canArchiveProject"
              type="danger"
              plain
              :loading="archiving"
              @click="archiveCurrentProject"
              >归档项目</el-button
            >
          </div>
        </div>
      </template>
    </main>
    <el-dialog v-model="editVisible" title="编辑项目资料" width="520px">
      <el-form label-position="top">
        <el-form-item label="项目名称" required>
          <el-input v-model="editForm.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="项目简介">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="editVisible = false"
          >取消</el-button
        >
        <el-button type="primary" :loading="saving" @click="saveProject"
          >保存</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>
