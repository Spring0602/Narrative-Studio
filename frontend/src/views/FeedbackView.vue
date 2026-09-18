<script setup lang="ts">
import { loadAllPages } from "@/api/pagination";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import type { MemberRole } from "@/api/members";
import {
  createFeedback,
  listFeedback,
  updateFeedbackStatus,
  type TestFeedback,
} from "@/api/feedback";
import {
  getPlaytestSteps,
  listPlaytests,
  type PlaytestSession,
  type PlaytestStep,
} from "@/api/playtests";
import type { IssueStatus } from "@/api/issues";

const props = defineProps<{
  currentUserRole: MemberRole;
  projectStatus: "ACTIVE" | "ARCHIVED";
}>();
const route = useRoute();
const projectId = computed(() => Number(route.params.id));
const canWrite = computed(() => props.projectStatus === "ACTIVE");
const canManage = computed(
  () => canWrite.value && ["OWNER", "EDITOR"].includes(props.currentUserRole),
);
const feedback = ref<TestFeedback[]>([]);
const sessions = ref<PlaytestSession[]>([]);
const steps = ref<PlaytestStep[]>([]);
const loading = ref(true);
const loadError = ref("");
const saving = ref(false);
const updatingId = ref<number | null>(null);
const statusFilter = ref<"ALL" | IssueStatus>("ALL");
const keyword = ref("");
const filtered = computed(() => {
  const key = keyword.value.trim().toLowerCase();
  return feedback.value.filter(
    (item) =>
      (statusFilter.value === "ALL" || item.status === statusFilter.value) &&
      (!key ||
        [item.title, item.description].some((value) =>
          value.toLowerCase().includes(key),
        )),
  );
});
const statusLabels: Record<IssueStatus, string> = {
  OPEN: "待处理",
  RESOLVED: "已解决",
  IGNORED: "已忽略",
};
const statusTags: Record<IssueStatus, "danger" | "success" | "info"> = {
  OPEN: "danger",
  RESOLVED: "success",
  IGNORED: "info",
};
function formatTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}
async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const [page, sessionPage] = await Promise.all([
      loadAllPages((page, size) => listFeedback(projectId.value, page, size)),
      loadAllPages((page, size) => listPlaytests(projectId.value, page, size)),
    ]);
    feedback.value = page.items;
    sessions.value = sessionPage.items;
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "测试反馈加载失败，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}
const dialogVisible = ref(false);
const form = reactive({
  sessionId: null as number | null,
  stepId: null as number | null,
  title: "",
  description: "",
});
function openCreate() {
  if (!canWrite.value) return void ElMessage.warning("归档项目不能提交新反馈");
  Object.assign(form, {
    sessionId: null,
    stepId: null,
    title: "",
    description: "",
  });
  steps.value = [];
  dialogVisible.value = true;
}
async function onSessionChange(sessionId: number | null) {
  form.stepId = null;
  if (!sessionId) {
    steps.value = [];
    return;
  }
  try {
    steps.value = (
      await loadAllPages((page, size) => getPlaytestSteps(projectId.value, sessionId, page, size), 10001)
    ).items;
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "会话步骤加载失败"));
  }
}
async function save() {
  if (!form.title.trim()) return void ElMessage.warning("请输入反馈标题");
  if (!form.description.trim()) return void ElMessage.warning("请输入反馈说明");
  saving.value = true;
  try {
    await createFeedback(projectId.value, {
      sessionId: form.sessionId,
      stepId: form.stepId,
      title: form.title.trim(),
      description: form.description.trim(),
    });
    await load();
    dialogVisible.value = false;
    ElMessage.success("测试反馈已提交");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "测试反馈提交失败"));
  } finally {
    saving.value = false;
  }
}
async function changeStatus(item: TestFeedback, status: IssueStatus) {
  if (!canManage.value) return void ElMessage.warning("当前角色不能处理反馈");
  if (status === "RESOLVED") {
    try {
      await ElMessageBox.confirm("请确认该反馈已经处理。", "标记已解决", {
        type: "warning",
      });
    } catch {
      return;
    }
  }
  updatingId.value = item.id;
  try {
    const updated = await updateFeedbackStatus(
      projectId.value,
      item.id,
      status,
    );
    const index = feedback.value.findIndex((entry) => entry.id === updated.id);
    if (index >= 0) feedback.value[index] = updated;
    ElMessage.success("反馈状态已更新");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "反馈状态更新失败"));
  } finally {
    updatingId.value = null;
  }
}
onMounted(load);
</script>

<template>
  <section class="feedback-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">TEST FEEDBACK</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/growth-sprout.png"
            alt=""
            aria-hidden="true"
          />
          <h1>测试反馈</h1>
        </div>
        <p class="muted">关联自己的模拟会话和步骤，记录并跟踪测试问题。</p>
      </div>
      <el-button v-if="canWrite" type="primary" size="large" @click="openCreate"
        >提交反馈</el-button
      >
    </div>
    <el-alert
      v-if="!canWrite"
      title="项目已归档，只能查看历史反馈"
      type="warning"
      show-icon
      :closable="false"
      class="notice"
    /><el-alert
      v-else-if="currentUserRole === 'TESTER'"
      title="TESTER 只能查看自己提交的反馈；OWNER 和 EDITOR 可以处理项目反馈。"
      type="info"
      show-icon
      :closable="false"
      class="notice"
    />
    <div class="filters">
      <el-select v-model="statusFilter"
        ><el-option label="全部状态" value="ALL" /><el-option
          label="待处理"
          value="OPEN" /><el-option label="已解决" value="RESOLVED" /><el-option
          label="已忽略"
          value="IGNORED" /></el-select
      ><el-input
        v-model="keyword"
        clearable
        placeholder="搜索标题或说明"
      /><span>{{ filtered.length }} 条</span>
    </div>
    <el-result
      v-if="loadError"
      icon="error"
      title="反馈加载失败"
      :sub-title="loadError"
      ><template #extra
        ><el-button type="primary" @click="load">重新加载</el-button></template
      ></el-result
    ><el-table v-else v-loading="loading" :data="filtered" class="table"
      ><el-table-column
        prop="title"
        label="标题"
        min-width="180" /><el-table-column label="定位" min-width="150"
        ><template #default="{ row }"
          ><span v-if="row.sessionId"
            >会话 #{{ row.sessionId
            }}<template v-if="row.stepId">
              / 步骤 #{{ row.stepId }}</template
            ></span
          ><span v-else>未关联会话</span></template
        ></el-table-column
      ><el-table-column
        prop="description"
        label="说明"
        min-width="280" /><el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="statusTags[row.status as IssueStatus]">{{
            statusLabels[row.status as IssueStatus]
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="提交时间" width="180"
        ><template #default="{ row }">{{
          formatTime(row.createdAt)
        }}</template></el-table-column
      ><el-table-column v-if="canManage" label="处理" width="190"
        ><template #default="{ row }"
          ><template v-if="row.status === 'OPEN'"
            ><el-button
              link
              type="success"
              :loading="updatingId === row.id"
              @click="changeStatus(row, 'RESOLVED')"
              >解决</el-button
            ><el-button
              link
              :disabled="updatingId !== null"
              @click="changeStatus(row, 'IGNORED')"
              >忽略</el-button
            ></template
          ><el-button
            v-else
            link
            type="primary"
            :loading="updatingId === row.id"
            @click="changeStatus(row, 'OPEN')"
            >重新打开</el-button
          ></template
        ></el-table-column
      ><template #empty><el-empty description="暂无测试反馈" /></template
    ></el-table>
    <el-dialog v-model="dialogVisible" title="提交测试反馈" width="600px"
      ><el-form label-position="top"
        ><el-form-item label="关联模拟会话（可选）"
          ><el-select
            v-model="form.sessionId"
            clearable
            class="wide"
            placeholder="不关联会话"
            @change="onSessionChange"
            ><el-option
              v-for="session in sessions"
              :key="session.id"
              :label="`会话 #${session.id} · ${session.currentNode.title}`"
              :value="session.id" /></el-select></el-form-item
        ><el-form-item label="关联步骤（选择会话后可选）"
          ><el-select
            v-model="form.stepId"
            clearable
            class="wide"
            :disabled="!form.sessionId"
            placeholder="不关联步骤"
            ><el-option
              v-for="step in steps"
              :key="step.id"
              :label="`步骤 ${step.stepNo} · 记录 #${step.id}`"
              :value="step.id" /></el-select></el-form-item
        ><el-form-item label="标题" required
          ><el-input
            v-model="form.title"
            maxlength="150"
            show-word-limit /></el-form-item
        ><el-form-item label="详细说明" required
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="6"
            maxlength="10000"
            show-word-limit /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >提交反馈</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>

<style scoped>
.feedback-page {
  max-width: 1200px;
}
.page-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}
.page-heading h1 {
  margin: 6px 0 8px;
  font-size: 42px;
}
.eyebrow {
  color: #b2663d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}
.muted {
  color: #78847e;
}
.notice {
  margin-top: 18px;
}
.filters {
  display: grid;
  grid-template-columns: 180px 1fr auto;
  align-items: center;
  gap: 12px;
  margin-top: 22px;
  padding: 14px;
  border: 1px solid #e1e5e0;
  border-radius: 10px;
  background: #fff;
}
.table {
  margin-top: 14px;
  border-radius: 12px;
}
.wide {
  width: 100%;
}
@media (max-width: 650px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
