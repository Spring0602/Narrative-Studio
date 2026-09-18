<script setup lang="ts">
import { loadAllPages } from "@/api/pagination";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import type { MemberRole } from "@/api/members";
import { getStoryGraph } from "@/api/storyGraph";
import {
  listIssues,
  runAnalysis,
  updateIssueStatus as requestIssueStatus,
  type DetectedIssue,
  type IssueSeverity,
  type IssueStatus,
  type IssueType,
} from "@/api/issues";

const props = defineProps<{
  currentUserRole: MemberRole;
  projectStatus: "ACTIVE" | "ARCHIVED";
}>();
const emit = defineEmits<{ locate: [nodeId: number] }>();
const route = useRoute();
const projectId = computed(() => Number(route.params.id));
const canManage = computed(
  () =>
    props.projectStatus === "ACTIVE" &&
    ["OWNER", "EDITOR"].includes(props.currentUserRole),
);
const readOnlyReason = computed(() =>
  props.projectStatus === "ARCHIVED"
    ? "项目已归档，只能查看历史检测结果"
    : "当前角色只能查看检测结果",
);

const typeLabels: Record<IssueType, string> = {
  START_COUNT: "起点数量异常",
  ISOLATED: "孤立节点",
  UNREACHABLE: "不可达节点",
  DEAD_END: "意外死路",
  CYCLE: "剧情循环",
  BROKEN_REFERENCE: "连接引用异常",
  ENDING_OUTGOING: "结局存在出边",
};
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
const suggestions: Record<IssueType, string> = {
  START_COUNT: "把一个普通节点设为唯一的剧情起点。",
  ISOLATED: "为节点创建进入或离开选择，或删除不再使用的节点。",
  UNREACHABLE: "从可达节点补充通向该节点的正式选择。",
  DEAD_END: "为普通节点添加可用选择，或者将其改成结局节点。",
  CYCLE: "确认循环是否符合设计；刻意设计的循环可以标记为忽略。",
  BROKEN_REFERENCE: "检查选择的起点和目标节点，删除或重新连接异常选择。",
  ENDING_OUTGOING: "删除结局节点的出边，或将该节点改回普通节点。",
};

const issues = ref<DetectedIssue[]>([]);
const nodeLabels = ref<Record<number, string>>({});
const choiceSourceNodes = ref<Record<number, number>>({});
const selectedId = ref<number | null>(null);
const loading = ref(true);
const loadError = ref("");
const analyzing = ref(false);
const updatingId = ref<number | null>(null);
const filterType = ref<"ALL" | IssueType>("ALL");
const filterStatus = ref<"ALL" | IssueStatus>("OPEN");
const filterSeverity = ref<"ALL" | IssueSeverity>("ALL");
const keyword = ref("");
const filteredIssues = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return issues.value.filter(
    (issue) =>
      (filterType.value === "ALL" || issue.issueType === filterType.value) &&
      (filterStatus.value === "ALL" || issue.status === filterStatus.value) &&
      (filterSeverity.value === "ALL" ||
        issue.severity === filterSeverity.value) &&
      (!text ||
        [issue.message, typeLabels[issue.issueType], targetLabel(issue)].some(
          (value) => value.toLowerCase().includes(text),
        )),
  );
});
const selectedIssue = computed(
  () =>
    filteredIssues.value.find((issue) => issue.id === selectedId.value) ??
    null,
);

watch(
  filteredIssues,
  (visibleIssues) => {
    const selectedIsVisible = visibleIssues.some(
      (issue) => issue.id === selectedId.value,
    );
    if (!selectedIsVisible) selectedId.value = visibleIssues[0]?.id ?? null;
  },
  { immediate: true, flush: "sync" },
);
const counts = computed(() => ({
  open: issues.value.filter((issue) => issue.status === "OPEN").length,
  error: issues.value.filter(
    (issue) => issue.status === "OPEN" && issue.severity === "ERROR",
  ).length,
  warning: issues.value.filter(
    (issue) => issue.status === "OPEN" && issue.severity === "WARNING",
  ).length,
  handled: issues.value.filter((issue) => issue.status !== "OPEN").length,
}));

function formatTime(value?: string | null) {
  return value
    ? new Date(value).toLocaleString("zh-CN", { hour12: false })
    : "—";
}
function targetNodeId(issue: DetectedIssue) {
  if (!issue.targetId) return null;
  if (issue.targetType === "NODE") return issue.targetId;
  if (issue.targetType === "CHOICE")
    return choiceSourceNodes.value[issue.targetId] ?? null;
  return null;
}
function targetLabel(issue: DetectedIssue) {
  if (!issue.targetId) return "整个剧情图";
  if (issue.targetType === "NODE")
    return nodeLabels.value[issue.targetId] || `节点 #${issue.targetId}`;
  if (issue.targetType === "CHOICE") return `选择 #${issue.targetId}`;
  return `图结构 #${issue.targetId}`;
}
async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const [page, graph] = await Promise.all([
      loadAllPages((page, size) => listIssues(projectId.value, page, size)),
      getStoryGraph(projectId.value),
    ]);
    issues.value = page.items;
    nodeLabels.value = Object.fromEntries(
      graph.nodes.map((node) => [node.id, node.title]),
    );
    choiceSourceNodes.value = Object.fromEntries(
      graph.choices.map((choice) => [choice.id, choice.sourceNodeId]),
    );
    if (
      selectedId.value &&
      !issues.value.some((issue) => issue.id === selectedId.value)
    )
      selectedId.value = null;
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "无法加载检测结果，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}
async function analyze() {
  if (!canManage.value || analyzing.value)
    return void ElMessage.warning(readOnlyReason.value);
  analyzing.value = true;
  try {
    const detected = await runAnalysis(projectId.value);
    await load();
    filterStatus.value = "OPEN";
    selectedId.value = detected[0]?.id ?? null;
    ElMessage.success(
      detected.length
        ? `检测完成，当前发现 ${detected.length} 个问题`
        : "检测完成，没有发现结构问题",
    );
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "运行剧情检测失败"));
  } finally {
    analyzing.value = false;
  }
}
async function changeStatus(issue: DetectedIssue, status: IssueStatus) {
  if (!canManage.value || updatingId.value !== null)
    return void ElMessage.warning(readOnlyReason.value);
  if (status === "RESOLVED") {
    try {
      await ElMessageBox.confirm(
        "此操作只更新处理状态，不会自动修改剧情图。请确认问题已经修复。",
        "标记为已解决",
        { type: "warning" },
      );
    } catch {
      return;
    }
  }
  updatingId.value = issue.id;
  try {
    const updated = await requestIssueStatus(projectId.value, issue.id, status);
    const index = issues.value.findIndex((item) => item.id === updated.id);
    if (index >= 0) issues.value[index] = updated;
    ElMessage.success(
      status === "OPEN"
        ? "问题已重新打开"
        : status === "RESOLVED"
          ? "问题已标记为解决"
          : "问题已忽略",
    );
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "问题状态更新失败"));
  } finally {
    updatingId.value = null;
  }
}
function locate(issue: DetectedIssue) {
  const nodeId = targetNodeId(issue);
  if (!nodeId)
    return void ElMessage.info("该问题针对整个剧情图，没有单一节点可定位");
  emit("locate", nodeId);
}

onMounted(load);
</script>

<template>
  <section class="issue-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">STORY ANALYSIS</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/document-search.png"
            alt=""
            aria-hidden="true"
          />
          <h1>问题检测</h1>
        </div>
        <p class="muted">检测起点、孤立、不可达、死路、循环及异常连接。</p>
      </div>
      <el-button
        type="primary"
        size="large"
        :loading="analyzing"
        :disabled="loading || Boolean(loadError) || !canManage"
        @click="analyze"
        >{{ analyzing ? "正在检测" : "运行检测" }}</el-button
      >
    </div>
    <el-alert
      v-if="!canManage"
      :title="readOnlyReason"
      type="warning"
      show-icon
      :closable="false"
      class="notice"
    />
    <div class="summary">
      <div>
        <strong>{{ counts.open }}</strong
        ><span>待处理</span>
      </div>
      <div>
        <strong>{{ counts.error }}</strong
        ><span>错误</span>
      </div>
      <div>
        <strong>{{ counts.warning }}</strong
        ><span>警告</span>
      </div>
      <div>
        <strong>{{ counts.handled }}</strong
        ><span>已处理</span>
      </div>
    </div>
    <div class="filters">
      <el-select v-model="filterType"
        ><el-option label="全部类型" value="ALL" /><el-option
          v-for="(label, value) in typeLabels"
          :key="value"
          :label="label"
          :value="value" /></el-select
      ><el-select v-model="filterStatus"
        ><el-option label="全部状态" value="ALL" /><el-option
          label="待处理"
          value="OPEN" /><el-option label="已解决" value="RESOLVED" /><el-option
          label="已忽略"
          value="IGNORED" /></el-select
      ><el-select v-model="filterSeverity"
        ><el-option label="全部等级" value="ALL" /><el-option
          label="错误"
          value="ERROR" /><el-option label="警告" value="WARNING" /></el-select
      ><el-input v-model="keyword" clearable placeholder="搜索问题或节点" />
    </div>
    <el-result
      v-if="loadError"
      icon="error"
      title="检测结果加载失败"
      :sub-title="loadError"
      ><template #extra
        ><el-button type="primary" @click="load">重新加载</el-button></template
      ></el-result
    >
    <div v-else class="layout" v-loading="loading">
      <section class="list">
        <button
          v-for="issue in filteredIssues"
          :key="issue.id"
          :class="{ active: selectedId === issue.id }"
          @click="selectedId = issue.id"
        >
          <div>
            <el-tag
              :type="issue.severity === 'ERROR' ? 'danger' : 'warning'"
              size="small"
              >{{ issue.severity === "ERROR" ? "错误" : "警告" }}</el-tag
            ><strong>{{ typeLabels[issue.issueType] }}</strong
            ><el-tag :type="statusTags[issue.status]" size="small">{{
              statusLabels[issue.status]
            }}</el-tag>
          </div>
          <p>{{ issue.message }}</p>
          <small
            >{{ targetLabel(issue) }} ·
            {{ formatTime(issue.detectedAt) }}</small
          ></button
        ><el-empty
          v-if="!filteredIssues.length"
          :description="
            issues.length
              ? '当前筛选条件下没有问题'
              : '尚无检测问题，请运行一次检测'
          "
        />
      </section>
      <aside class="detail">
        <el-empty
          v-if="!selectedIssue"
          :description="
            filteredIssues.length
              ? '选择一个问题查看详情'
              : issues.length
                ? '当前筛选结果中没有可查看的问题'
                : '尚无检测问题，请运行一次检测'
          "
        /><template v-else
          ><p class="label">问题详情</p>
          <h2>{{ typeLabels[selectedIssue.issueType] }}</h2>
          <el-tag
            :type="selectedIssue.severity === 'ERROR' ? 'danger' : 'warning'"
            >{{ selectedIssue.severity }}</el-tag
          ><el-tag :type="statusTags[selectedIssue.status]">{{
            statusLabels[selectedIssue.status]
          }}</el-tag>
          <dl>
            <div>
              <dt>目标</dt>
              <dd>{{ targetLabel(selectedIssue) }}</dd>
            </div>
            <div>
              <dt>检测时间</dt>
              <dd>{{ formatTime(selectedIssue.detectedAt) }}</dd>
            </div>
            <div>
              <dt>处理时间</dt>
              <dd>{{ formatTime(selectedIssue.resolvedAt) }}</dd>
            </div>
            <div>
              <dt>说明</dt>
              <dd>{{ selectedIssue.message }}</dd>
            </div>
            <div>
              <dt>建议</dt>
              <dd>{{ suggestions[selectedIssue.issueType] }}</dd>
            </div>
          </dl>
          <el-button
            v-if="targetNodeId(selectedIssue)"
            class="wide"
            @click="locate(selectedIssue)"
            >去剧情图定位节点</el-button
          >
          <div v-if="canManage" class="actions">
            <template v-if="selectedIssue.status === 'OPEN'"
              ><el-button
                type="success"
                :loading="updatingId === selectedIssue.id"
                @click="changeStatus(selectedIssue, 'RESOLVED')"
                >标记已解决</el-button
              ><el-button
                :disabled="updatingId !== null"
                @click="changeStatus(selectedIssue, 'IGNORED')"
                >忽略</el-button
              ></template
            ><el-button
              v-else
              type="primary"
              plain
              :loading="updatingId === selectedIssue.id"
              @click="changeStatus(selectedIssue, 'OPEN')"
              >重新打开</el-button
            >
          </div></template
        >
      </aside>
    </div>
  </section>
</template>

<style scoped>
.issue-page {
  max-width: 1220px;
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
.summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 22px;
}
.summary div {
  padding: 18px;
  border: 1px solid #e1e5e0;
  border-radius: 10px;
  background: #fff;
}
.summary strong,
.summary span {
  display: block;
}
.summary strong {
  font-size: 25px;
}
.summary span {
  color: #7c8781;
}
.filters {
  display: grid;
  grid-template-columns: repeat(3, 170px) minmax(220px, 1fr);
  gap: 10px;
  margin-top: 14px;
  padding: 14px;
  border: 1px solid #e1e5e0;
  border-radius: 10px;
  background: #fff;
}
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  min-height: 560px;
  margin-top: 14px;
  overflow: hidden;
  border: 1px solid #dfe3de;
  border-radius: 14px;
  background: #fff;
}
.list {
  padding: 16px;
  background: #f8f9f6;
}
.list > button {
  width: 100%;
  margin-bottom: 10px;
  padding: 15px;
  text-align: left;
  border: 1px solid #dfe3de;
  border-radius: 9px;
  background: #fff;
  cursor: pointer;
}
.list > button.active {
  border-color: #55786a;
  box-shadow: 0 0 0 2px #55786a18;
}
.list > button div {
  display: flex;
  align-items: center;
  gap: 8px;
}
.list > button p {
  margin: 10px 0;
  line-height: 1.5;
}
.list > button small {
  color: #818b85;
}
.detail {
  padding: 24px;
  border-left: 1px solid #e1e5e0;
}
.detail .label {
  color: #b2663d;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}
.detail h2 {
  margin: 7px 0 12px;
}
.detail > .el-tag + .el-tag {
  margin-left: 6px;
}
.detail dl div {
  padding: 11px 0;
  border-bottom: 1px solid #edf0ed;
}
.detail dt {
  color: #8a938e;
  font-size: 11px;
}
.detail dd {
  margin: 5px 0 0;
  white-space: pre-wrap;
}
.wide {
  width: 100%;
  margin-top: 16px;
}
.actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
@media (max-width: 950px) {
  .filters {
    grid-template-columns: 1fr 1fr;
  }
  .layout {
    grid-template-columns: 1fr;
  }
  .detail {
    border-top: 1px solid #e1e5e0;
    border-left: 0;
  }
}
@media (max-width: 650px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }
  .summary,
  .filters {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
