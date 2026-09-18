<script setup lang="ts">
import { loadAllPages } from "@/api/pagination";
import axios from "axios";
import PlayerProgressPanel from "@/components/PlayerProgressPanel.vue";
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import type { MemberRole } from "@/api/members";
import {
  choosePlaytestOption,
  getPlaytest,
  getPlaytestSteps,
  listPlaytests,
  restartPlaytest,
  startPlaytest,
  startReleasedPlaytest,
  stopPlaytest,
  type PlaytestChoice,
  type PlaytestSession,
  type PlaytestStep,
  type StateSnapshot,
} from "@/api/playtests";
import { listVariables } from "@/api/rules";
import { getStoryGraph } from "@/api/storyGraph";
import {
  listReleases,
  getRelease,
  publishRelease,
  type ReleaseSummary,
} from "@/api/releases";

const props = defineProps<{
  currentUserRole: MemberRole;
  projectStatus: "ACTIVE" | "ARCHIVED";
}>();
const route = useRoute();
const projectId = computed(() => Number(route.params.id));
const canRun = computed(() => props.projectStatus === "ACTIVE");
const canPublish = computed(
  () => canRun.value && props.currentUserRole === "OWNER",
);
const statusLabels: Record<PlaytestSession["status"], string> = {
  RUNNING: "进行中",
  COMPLETED: "已完成",
  ABORTED: "已终止",
};
const statusTags: Record<
  PlaytestSession["status"],
  "warning" | "success" | "info"
> = { RUNNING: "warning", COMPLETED: "success", ABORTED: "info" };

const sessions = ref<PlaytestSession[]>([]);
const releases = ref<ReleaseSummary[]>([]);
const selectedSource = ref<"current" | number>("current");
const currentSession = ref<PlaytestSession | null>(null);
const currentSteps = ref<PlaytestStep[]>([]);
const variableLabels = ref<Record<string, string>>({});
const nodeLabels = ref<Record<number, string>>({});
const loading = ref(true);
const loadError = ref("");
const operating = ref(false);
const processingChoiceId = ref<number | null>(null);
const sessionNotice = ref("");
const publishing = ref(false);

const sortedSessions = computed(() =>
  [...sessions.value].sort((a, b) => b.id - a.id),
);
const stateRows = computed(() =>
  snapshotRows(currentSession.value?.state ?? {}, variableLabels.value),
);
const knowledgeRows = computed(() =>
  snapshotRows(currentSession.value?.knowledge ?? {}, {}),
);

function snapshotRows(snapshot: StateSnapshot, labels: Record<string, string>) {
  return Object.entries(snapshot).map(([key, value]) => ({
    key,
    label: labels[key] || key,
    value,
  }));
}
function formatTime(value?: string) {
  return value
    ? new Date(value).toLocaleString("zh-CN", { hour12: false })
    : "—";
}
function nodeTitle(nodeId: number) {
  return nodeLabels.value[nodeId] || `节点 #${nodeId}`;
}

async function loadPage() {
  loading.value = true;
  loadError.value = "";
  try {
    const [sessionPage, releasePage, variables, graph] = await Promise.all([
      loadAllPages((page, size) => listPlaytests(projectId.value, page, size)),
      loadAllPages((page, size) => listReleases(projectId.value, page, size)),
      listVariables(projectId.value),
      getStoryGraph(projectId.value),
    ]);
    sessions.value = sessionPage.items;
    releases.value = releasePage.items;
    variableLabels.value = Object.fromEntries(
      variables.map((variable) => [variable.variableKey, variable.displayName]),
    );
    nodeLabels.value = Object.fromEntries(
      graph.nodes.map((node) => [node.id, node.title]),
    );
    if (currentSession.value) {
      const matching = sessions.value.find(
        (session) => session.id === currentSession.value?.id,
      );
      if (!matching) {
        currentSession.value = null;
        currentSteps.value = [];
      }
    }
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "无法加载模拟会话，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}

async function selectSession(session: PlaytestSession) {
  loading.value = true;
  sessionNotice.value = "";
  try {
    const [detail, steps] = await Promise.all([
      getPlaytest(projectId.value, session.id),
      loadAllPages((page, size) => getPlaytestSteps(projectId.value, session.id, page, size), 10001),
    ]);
    currentSession.value = detail;
    currentSteps.value = steps.items;
    const source = detail.releaseId
      ? await getRelease(projectId.value, detail.releaseId)
      : { ...(await getStoryGraph(projectId.value)), variables: await listVariables(projectId.value) };
    nodeLabels.value = Object.fromEntries(source.nodes.map(node => [node.id, node.title]));
    variableLabels.value = Object.fromEntries(source.variables.map(variable => [variable.variableKey, variable.displayName]));
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "模拟会话加载失败"));
  } finally {
    loading.value = false;
  }
}
async function refreshCurrent() {
  if (!currentSession.value) return;
  await selectSession(currentSession.value);
  const page = await loadAllPages((page, size) => listPlaytests(projectId.value, page, size));
  sessions.value = page.items;
}
async function start() {
  if (!canRun.value || operating.value)
    return void ElMessage.warning("已归档项目只能查看历史会话");
  operating.value = true;
  sessionNotice.value = "";
  try {
    const session =
      selectedSource.value === "current"
        ? await startPlaytest(projectId.value)
        : await startReleasedPlaytest(projectId.value, selectedSource.value);
    await loadPage();
    await selectSession(session);
    ElMessage.success(
      session.releaseId ? "已从冻结版本开始模拟" : "已从当前编辑版开始模拟",
    );
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "模拟会话创建失败"));
  } finally {
    operating.value = false;
  }
}
async function choose(choice: PlaytestChoice) {
  const session = currentSession.value;
  if (
    !session ||
    session.status !== "RUNNING" ||
    processingChoiceId.value !== null
  )
    return;
  if (!canRun.value) return void ElMessage.warning("已归档项目不能推进模拟");
  processingChoiceId.value = choice.id;
  sessionNotice.value = "";
  try {
    currentSession.value = await choosePlaytestOption(
      projectId.value,
      session.id,
      choice.id,
      session.stepNo,
    );
    await refreshCurrent();
    ElMessage.success(
      currentSession.value.status === "COMPLETED"
        ? "已经到达结局"
        : "选择已执行，快照已更新",
    );
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 409) {
      await refreshCurrent();
      sessionNotice.value =
        "会话进度已变化，页面已重新读取服务器状态。原选择没有自动重试，请确认后重新选择。";
    }
    ElMessage.error(apiErrorMessage(error, "执行选择失败"));
  } finally {
    processingChoiceId.value = null;
  }
}
async function stop() {
  const session = currentSession.value;
  if (!session || session.status !== "RUNNING" || operating.value) return;
  try {
    await ElMessageBox.confirm(
      "终止后仍可回放已有步骤，但不能继续选择。",
      "终止模拟",
      { type: "warning" },
    );
  } catch {
    return;
  }
  operating.value = true;
  try {
    currentSession.value = await stopPlaytest(projectId.value, session.id);
    await refreshCurrent();
    ElMessage.success("模拟会话已终止");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "终止模拟失败"));
  } finally {
    operating.value = false;
  }
}
async function restart() {
  const session = currentSession.value;
  if (!session || operating.value) return;
  if (!canRun.value)
    return void ElMessage.warning("已归档项目不能重新开始模拟");
  try {
    await ElMessageBox.confirm(
      session.releaseId
        ? "将从同一冻结版本创建新会话，旧历史继续保留。"
        : "将使用当前最新定义创建新会话，旧历史继续保留。",
      "重新开始模拟",
      { type: "warning" },
    );
  } catch {
    return;
  }
  operating.value = true;
  try {
    const next = await restartPlaytest(projectId.value, session.id);
    await loadPage();
    await selectSession(next);
    ElMessage.success("已创建新的模拟会话");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "重新开始失败"));
  } finally {
    operating.value = false;
  }
}
async function publish() {
  if (!canPublish.value || publishing.value) return;
  try {
    await ElMessageBox.confirm(
      "发布会冻结当前节点、选项、规则、变量和知识定义。确定继续吗？",
      "发布剧情版本",
      { type: "warning" },
    );
  } catch {
    return;
  }
  publishing.value = true;
  try {
    const release = await publishRelease(projectId.value);
    await loadPage();
    selectedSource.value = release.id;
    ElMessage.success(`版本 v${release.versionNo} 已发布`);
  } catch (error) {
    ElMessage.error(
      apiErrorMessage(error, "发布失败，请先修复剧情结构或规则错误"),
    );
  } finally {
    publishing.value = false;
  }
}
function stepStateChanges(step: PlaytestStep) {
  const keys = new Set([
    ...Object.keys(step.stateBefore),
    ...Object.keys(step.stateAfter),
  ]);
  return [...keys]
    .filter(
      (key) =>
        step.stepNo === 0 || step.stateBefore[key] !== step.stateAfter[key],
    )
    .map((key) => ({
      key,
      before: step.stepNo === 0 ? "—" : (step.stateBefore[key] ?? "—"),
      after: step.stateAfter[key] ?? "—",
    }));
}
function stepKnowledgeChanges(step: PlaytestStep) {
  const keys = new Set([
    ...Object.keys(step.knowledgeBefore),
    ...Object.keys(step.knowledgeAfter),
  ]);
  return [...keys]
    .filter((key) => step.knowledgeBefore[key] !== step.knowledgeAfter[key])
    .map((key) => ({
      key,
      before: step.knowledgeBefore[key] ?? "—",
      after: step.knowledgeAfter[key] ?? "—",
    }));
}

onMounted(loadPage);
</script>

<template>
  <section class="playtest-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">PLAYTEST</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/timeline-nodes.png"
            alt=""
            aria-hidden="true"
          />
          <h1>模拟测试</h1>
        </div>
        <p class="muted">从起点运行剧情，核对条件显隐、状态与知识快照。</p>
      </div>
      <div class="heading-actions">
        <el-button v-if="canPublish" :loading="publishing" @click="publish"
          >发布当前版本</el-button
        ><el-select v-model="selectedSource" class="source-select"
          ><el-option label="当前编辑版" value="current" /><el-option
            v-for="release in releases"
            :key="release.id"
            :label="`冻结版本 v${release.versionNo}`"
            :value="release.id" /></el-select
        ><el-button
          type="primary"
          size="large"
          :loading="operating"
          :disabled="!canRun || loading || Boolean(loadError)"
          @click="start"
          >开始新模拟</el-button
        >
      </div>
    </div>
    <PlayerProgressPanel
      :project-id="projectId"
      :release-id="selectedSource === 'current' ? undefined : selectedSource"
      :refresh-key="currentSession ? currentSession.id+':'+currentSession.stepNo+':'+currentSession.status : ''"
      :can-edit="canRun && currentUserRole !== 'TESTER'"
      :can-run="canRun"
      @changed="refreshCurrent"
    />
    <el-alert
      v-if="!canRun"
      title="项目已归档，只能查看历史模拟和快照"
      type="warning"
      show-icon
      :closable="false"
      class="notice"
    />
    <el-alert
      v-if="sessionNotice"
      :title="sessionNotice"
      type="warning"
      show-icon
      :closable="false"
      class="notice"
    />
    <el-result
      v-if="loadError"
      icon="error"
      title="模拟数据加载失败"
      :sub-title="loadError"
      ><template #extra
        ><el-button type="primary" @click="loadPage"
          >重新加载</el-button
        ></template
      ></el-result
    >
    <div v-else class="layout" v-loading="loading">
      <aside class="sessions">
        <div class="panel-title">
          <strong>我的会话</strong
          ><el-button link @click="loadPage">刷新</el-button>
        </div>
        <button
          v-for="session in sortedSessions"
          :key="session.id"
          :class="{ active: currentSession?.id === session.id }"
          @click="selectSession(session)"
        >
          <span>#{{ session.id }} · {{ session.currentNode.title }}</span
          ><small
            >{{
              session.releaseId ? `版本 #${session.releaseId}` : "当前编辑版"
            }}
            · {{ formatTime(session.startedAt) }}</small
          ><el-tag size="small" :type="statusTags[session.status]">{{
            statusLabels[session.status]
          }}</el-tag></button
        ><el-empty
          v-if="!sessions.length"
          description="还没有模拟会话"
          :image-size="70"
        />
      </aside>
      <main class="runner">
        <el-empty
          v-if="!currentSession"
          description="选择历史会话，或开始一次新模拟"
        />
        <template v-else
          ><div class="session-head">
            <div>
              <p>
                会话 #{{ currentSession.id }} · 步骤 {{ currentSession.stepNo }}
              </p>
              <h2>{{ currentSession.currentNode.title }}</h2>
            </div>
            <div>
              <el-tag :type="statusTags[currentSession.status]">{{
                statusLabels[currentSession.status]
              }}</el-tag
              ><el-tag v-if="currentSession.releaseId" type="info"
                >冻结版本 #{{ currentSession.releaseId }}</el-tag
              >
            </div>
          </div>
          <article class="story-content">
            {{ currentSession.currentNode.content || "该节点没有正文。" }}
          </article>
          <el-alert
            v-if="currentSession.deadEnd"
            title="当前没有可用选择：请检查前置条件或跨路线解锁要求"
            type="error"
            show-icon
            :closable="false"
          />
          <section class="choice-list">
            <h3>可用选择</h3>
            <el-empty
              v-if="!currentSession.availableChoices.length"
              :description="
                currentSession.status === 'COMPLETED'
                  ? '已经到达结局'
                  : currentSession.status === 'ABORTED'
                    ? '会话已终止'
                    : '当前没有满足条件的选择'
              "
              :image-size="58"
            /><el-button
              v-for="choice in currentSession.availableChoices"
              :key="choice.id"
              class="choice"
              :loading="processingChoiceId === choice.id"
              :disabled="processingChoiceId !== null || !canRun"
              @click="choose(choice)"
              >{{ choice.choiceText }}</el-button
            >
          </section>
          <div class="session-actions">
            <el-popover v-if="currentSession.lockedChoices?.length" trigger="click" width="440">
              <template #reference><el-button>为什么其他选择未解锁？</el-button></template>
              <div v-for="choice in currentSession.lockedChoices" :key="choice.id"><strong>{{choice.choiceText}}</strong><p>{{choice.reason}}</p></div>
            </el-popover>
            <el-button :loading="operating" :disabled="!canRun" @click="restart"
              >重新开始</el-button
            ><el-button
              v-if="currentSession.status === 'RUNNING'"
              type="danger"
              plain
              :loading="operating"
              :disabled="!canRun"
              @click="stop"
              >终止会话</el-button
            >
          </div>
        </template>
      </main>
      <aside class="snapshots">
        <section>
          <h3>当前状态</h3>
          <el-empty
            v-if="!stateRows.length"
            description="无状态变量"
            :image-size="48"
          />
          <dl v-else>
            <div v-for="row in stateRows" :key="row.key">
              <dt>{{ row.label }}</dt>
              <dd>
                <code>{{ row.value === "" ? "（空字符串）" : row.value }}</code>
              </dd>
            </div>
          </dl>
        </section>
        <section>
          <h3>当前知识</h3>
          <el-empty
            v-if="!knowledgeRows.length"
            description="暂无已获得知识"
            :image-size="48"
          />
          <dl v-else>
            <div v-for="row in knowledgeRows" :key="row.key">
              <dt>{{ row.key }}</dt>
              <dd>
                <code>{{ row.value }}</code>
              </dd>
            </div>
          </dl>
        </section>
      </aside>
    </div>
    <section v-if="currentSession && !loadError" class="history">
      <h2>步骤回放</h2>
      <el-timeline
        ><el-timeline-item
          v-for="step in currentSteps"
          :key="step.id"
          :timestamp="formatTime(step.createdAt)"
          placement="top"
          ><el-card shadow="never"
            ><div class="step-head">
              <strong
                >步骤 {{ step.stepNo }} · {{ nodeTitle(step.nodeId) }}</strong
              ><span>{{
                step.choiceId ? `选择 #${step.choiceId}` : "初始快照"
              }}</span>
            </div>
            <div class="changes">
              <div>
                <b>状态变化</b>
                <p v-for="change in stepStateChanges(step)" :key="change.key">
                  <code>{{ variableLabels[change.key] || change.key }}</code
                  >：{{ change.before }} → {{ change.after }}
                </p>
                <p v-if="!stepStateChanges(step).length">无变化</p>
              </div>
              <div>
                <b>知识变化</b>
                <p
                  v-for="change in stepKnowledgeChanges(step)"
                  :key="change.key"
                >
                  <code>{{ change.key }}</code
                  >：{{ change.before }} → {{ change.after }}
                </p>
                <p v-if="!stepKnowledgeChanges(step).length">无变化</p>
              </div>
              <div v-if="step.progressAfter">
                <b>当步跨局记录（不会随清档重写）</b>
                <p>通关：{{step.progressBefore?.completedEndings.join('、') || '无'}} → {{step.progressAfter.completedEndings.join('、') || '无'}}</p>
              </div>
            </div></el-card
          ></el-timeline-item
        ></el-timeline
      >
    </section>
  </section>
</template>

<style scoped>
.playtest-page {
  max-width: 1320px;
}
.page-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}
.page-heading h1 {
  margin: 6px 0 8px;
  font-size: 42px;
}
.heading-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.source-select {
  width: 180px;
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
.layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr) 250px;
  min-height: 560px;
  margin-top: 22px;
  overflow: hidden;
  border: 1px solid #dfe3de;
  border-radius: 14px;
  background: #fff;
}
.sessions,
.snapshots {
  padding: 18px;
  background: #f8f9f6;
}
.sessions {
  border-right: 1px solid #e1e5e0;
}
.snapshots {
  border-left: 1px solid #e1e5e0;
}
.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.sessions > button {
  position: relative;
  width: 100%;
  margin-bottom: 9px;
  padding: 13px;
  text-align: left;
  border: 1px solid #dfe3de;
  border-radius: 9px;
  background: #fff;
  cursor: pointer;
}
.sessions > button.active {
  border-color: #55786a;
  box-shadow: 0 0 0 2px #55786a18;
}
.sessions span,
.sessions small {
  display: block;
  padding-right: 60px;
}
.sessions small {
  margin-top: 5px;
  color: #7d8882;
}
.sessions .el-tag {
  position: absolute;
  top: 11px;
  right: 9px;
}
.runner {
  padding: 28px;
}
.session-head {
  display: flex;
  justify-content: space-between;
}
.session-head p {
  margin: 0;
  color: #7c8781;
  font-size: 12px;
}
.session-head h2 {
  margin: 6px 0 0;
  font-size: 28px;
}
.session-head .el-tag + .el-tag {
  margin-left: 6px;
}
.story-content {
  margin: 22px 0;
  padding: 22px;
  border-left: 4px solid #b87547;
  background: #fbfaf7;
  white-space: pre-wrap;
  line-height: 1.8;
}
.choice-list h3,
.snapshots h3 {
  margin: 0 0 12px;
}
.choice {
  display: block;
  width: 100%;
  height: auto;
  margin: 8px 0;
  padding: 13px;
  white-space: normal;
}
.session-actions {
  display: flex;
  gap: 10px;
  margin-top: 22px;
}
.snapshots section + section {
  margin-top: 24px;
}
.snapshots dl div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #e2e6e2;
}
.snapshots dt {
  color: #69756e;
}
.snapshots dd {
  margin: 0;
}
.history {
  margin-top: 22px;
  padding: 24px;
  border: 1px solid #dfe3de;
  border-radius: 14px;
  background: #fff;
}
.step-head {
  display: flex;
  justify-content: space-between;
}
.step-head span {
  color: #7d8882;
}
.changes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-top: 14px;
}
.changes p {
  margin: 7px 0;
  color: #647169;
}
.changes code {
  color: #81553c;
}
@media (max-width: 1050px) {
  .layout {
    grid-template-columns: 220px 1fr;
  }
  .snapshots {
    grid-column: 1/-1;
    border-top: 1px solid #e1e5e0;
    border-left: 0;
  }
  .snapshots {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px;
  }
  .snapshots section + section {
    margin-top: 0;
  }
}
@media (max-width: 760px) {
  .page-heading,
  .heading-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .source-select {
    width: 100%;
  }
  .layout {
    grid-template-columns: 1fr;
  }
  .sessions {
    border-right: 0;
    border-bottom: 1px solid #e1e5e0;
  }
  .changes,
  .snapshots {
    grid-template-columns: 1fr;
  }
}
</style>
