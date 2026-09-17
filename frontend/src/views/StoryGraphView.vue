<script setup lang="ts">
import { loadAllPages } from "@/api/pagination";
import { computed, defineAsyncComponent, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Handle,
  MarkerType,
  Position,
  VueFlow,
  type Connection,
  type Edge,
  type EdgeMouseEvent,
  type Node,
  type NodeDragEvent,
  type NodeMouseEvent,
} from "@vue-flow/core";
import "@vue-flow/core/dist/style.css";
import "@vue-flow/core/dist/theme-default.css";
import { apiErrorMessage } from "@/api/errors";
import type { MemberRole } from "@/api/members";
import {
  createStoryChoice,
  createStoryNode,
  deleteStoryChoice,
  deleteStoryNode,
  getStoryGraph,
  updateStoryChoice,
  updateStoryNode,
  updateStoryNodePositions,
  type SaveStoryChoicePayload,
  type SaveStoryNodePayload,
  type StoryChoiceSummary,
  type StoryNodeSummary,
  type StoryNodeType,
} from "@/api/storyGraph";
import { getChoiceRules } from "@/api/rules";
import {
  createChoiceDraft,
  deleteChoiceDraft,
  listChoiceDrafts,
  publishChoiceDraft,
  updateChoiceDraft,
  type ChoiceDraft,
} from "@/api/choiceDrafts";
import ChoiceRuleEditorDialog from "@/components/ChoiceRuleEditorDialog.vue";

interface StoryNodeData {
  summary: StoryNodeSummary;
}
interface StoryChoiceData {
  choice: StoryChoiceSummary;
}
type StoryFlowNode = Node<StoryNodeData>;
type StoryFlowEdge = Edge<StoryChoiceData>;
interface RuleSummary {
  choiceId: number;
  conditionCount: number;
  effectCount: number;
}

const props = defineProps<{
  currentUserRole: MemberRole;
  projectStatus: "ACTIVE" | "ARCHIVED";
  focusNodeId?: number | null;
}>();
const route = useRoute();
const router = useRouter();
const ExcelImportDialog = defineAsyncComponent(() => import("@/components/ExcelImportDialog.vue"));
const excelVisible = ref(false);
async function excelImported(id:number) {
  if(id===projectId.value) await loadGraph();
  else await router.push('/projects/'+id+'?module=story');
}
const projectId = computed(() => Number(route.params.id));
const canEdit = computed(
  () =>
    props.projectStatus === "ACTIVE" &&
    ["OWNER", "EDITOR"].includes(props.currentUserRole),
);
const readOnlyReason = computed(() =>
  props.projectStatus === "ARCHIVED"
    ? "项目已归档，只能查看"
    : "当前角色只能查看",
);

function createFlowNode(summary: StoryNodeSummary): StoryFlowNode {
  return {
    id: String(summary.id),
    type: "story",
    position: { x: summary.positionX, y: summary.positionY },
    data: { summary },
  };
}
function createFlowEdge(choice: StoryChoiceSummary): StoryFlowEdge {
  return {
    id: `choice-${choice.id}`,
    source: String(choice.sourceNodeId),
    target: String(choice.targetNodeId),
    type: "smoothstep",
    label: choice.choiceText,
    animated: choice.enabled,
    markerEnd: MarkerType.ArrowClosed,
    style: choice.enabled
      ? { stroke: "#55786a", strokeWidth: 2 }
      : { stroke: "#9aa49f", strokeWidth: 2, strokeDasharray: "6 5" },
    labelBgPadding: [8, 4],
    labelBgBorderRadius: 5,
    labelShowBg: true,
    labelBgStyle: { fill: "#fbfbf8" },
    data: { choice },
  };
}

const flowNodes = ref<StoryFlowNode[]>([]);
const flowEdges = ref<StoryFlowEdge[]>([]);
const loading = ref(true);
const loadError = ref("");
const saving = ref(false);
const deleting = ref(false);
const positionSaving = ref(false);
const lastSavedAt = ref("尚未保存");
const selectedNodeId = ref<number | null>(null);
const selectedChoiceId = ref<number | null>(null);
const ruleSummaries = ref<Record<number, RuleSummary>>({});
const selectedNode = computed(
  () =>
    flowNodes.value.find((node) => Number(node.id) === selectedNodeId.value)
      ?.data?.summary ?? null,
);
const selectedChoice = computed(
  () =>
    flowEdges.value.find(
      (edge) => edge.data?.choice.id === selectedChoiceId.value,
    )?.data?.choice ?? null,
);
const selectedRuleSummary = computed(() =>
  selectedChoice.value
    ? ruleSummaries.value[selectedChoice.value.id]
    : undefined,
);
const normalNodes = computed(() =>
  flowNodes.value
    .map((node) => node.data?.summary)
    .filter((node): node is StoryNodeSummary =>
      Boolean(node && node.nodeType === "NORMAL"),
    ),
);
const allNodes = computed(() =>
  flowNodes.value
    .map((node) => node.data?.summary)
    .filter((node): node is StoryNodeSummary => Boolean(node)),
);

async function loadGraph() {
  loading.value = true;
  loadError.value = "";
  try {
    const graph = await getStoryGraph(projectId.value);
    flowNodes.value = graph.nodes.map(createFlowNode);
    flowEdges.value = graph.choices.map(createFlowEdge);
    lastSavedAt.value = "已从服务器加载";
    if (
      props.focusNodeId &&
      graph.nodes.some((node) => node.id === props.focusNodeId)
    ) {
      selectedNodeId.value = props.focusNodeId;
      selectedChoiceId.value = null;
    } else if (
      selectedNodeId.value &&
      !graph.nodes.some((node) => node.id === selectedNodeId.value)
    ) {
      selectedNodeId.value = null;
    }
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "无法加载剧情图，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}

function markSavedNow() {
  lastSavedAt.value = new Date().toLocaleTimeString("zh-CN", { hour12: false });
}
function clearSelection() {
  selectedNodeId.value = null;
  selectedChoiceId.value = null;
}
function nodeTitle(nodeId: number) {
  return (
    allNodes.value.find((node) => node.id === nodeId)?.title ||
    `节点 #${nodeId}`
  );
}
function optionalText(value: string) {
  return value.trim() || undefined;
}

watch(
  () => props.focusNodeId,
  (nodeId) => {
    if (nodeId && allNodes.value.some((node) => node.id === nodeId)) {
      selectedNodeId.value = nodeId;
      selectedChoiceId.value = null;
    }
  },
);

const nodeDialogVisible = ref(false);
const nodeDialogMode = ref<"create" | "edit">("create");
const editingNodeId = ref<number | null>(null);
const nodeForm = reactive({
  nodeKey: "",
  title: "",
  content: "",
  nodeType: "NORMAL" as StoryNodeType,
  scene: "",
  isStart: false,
  positionX: 120,
  positionY: 120,
});

function resetNodeForm() {
  Object.assign(nodeForm, {
    nodeKey: "",
    title: "",
    content: "",
    nodeType: "NORMAL",
    scene: "",
    isStart: false,
    positionX: 120 + flowNodes.value.length * 30,
    positionY: 120 + flowNodes.value.length * 30,
  });
  editingNodeId.value = null;
}
function openCreateNode() {
  if (!canEdit.value) return void ElMessage.warning(readOnlyReason.value);
  nodeDialogMode.value = "create";
  resetNodeForm();
  nodeDialogVisible.value = true;
}
function openEditNode(node: StoryNodeSummary) {
  if (!canEdit.value) return void ElMessage.warning(readOnlyReason.value);
  nodeDialogMode.value = "edit";
  editingNodeId.value = node.id;
  Object.assign(nodeForm, {
    nodeKey: node.nodeKey,
    title: node.title,
    content: node.content || "",
    nodeType: node.nodeType,
    scene: node.scene || "",
    isStart: node.isStart,
    positionX: node.positionX,
    positionY: node.positionY,
  });
  nodeDialogVisible.value = true;
}
function nodePayload(): SaveStoryNodePayload {
  return {
    nodeKey: nodeForm.nodeKey.trim(),
    title: nodeForm.title.trim(),
    content: optionalText(nodeForm.content),
    nodeType: nodeForm.nodeType,
    scene: optionalText(nodeForm.scene),
    isStart: nodeForm.isStart,
    positionX: nodeForm.positionX,
    positionY: nodeForm.positionY,
  };
}
function validateNode(payload: SaveStoryNodePayload) {
  if (!payload.nodeKey) return "请输入节点标识";
  if (!/^[A-Za-z0-9_-]+$/.test(payload.nodeKey))
    return "节点标识只能包含字母、数字、下划线和短横线";
  if (!payload.title) return "请输入节点标题";
  if (
    flowNodes.value.some(
      (node) =>
        node.data?.summary.nodeKey === payload.nodeKey &&
        Number(node.id) !== editingNodeId.value,
    )
  )
    return "节点标识在项目内必须唯一";
  if (payload.isStart && payload.nodeType === "ENDING")
    return "结局节点不能设为起点";
  if (
    payload.isStart &&
    flowNodes.value.some(
      (node) =>
        node.data?.summary.isStart && Number(node.id) !== editingNodeId.value,
    )
  )
    return "项目只能有一个起点";
  if (
    editingNodeId.value &&
    payload.nodeType === "ENDING" &&
    flowEdges.value.some((edge) => Number(edge.source) === editingNodeId.value)
  )
    return "存在出边的节点不能改为结局节点";
  return "";
}
async function saveNode() {
  if (!canEdit.value || saving.value) return;
  const payload = nodePayload();
  const validation = validateNode(payload);
  if (validation) return void ElMessage.warning(validation);
  saving.value = true;
  try {
    const saved =
      nodeDialogMode.value === "create"
        ? await createStoryNode(projectId.value, payload)
        : await updateStoryNode(projectId.value, editingNodeId.value!, payload);
    await loadGraph();
    selectedNodeId.value = saved.id;
    nodeDialogVisible.value = false;
    markSavedNow();
    ElMessage.success(
      nodeDialogMode.value === "create" ? "剧情节点已创建" : "剧情节点已更新",
    );
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "剧情节点保存失败"));
  } finally {
    saving.value = false;
  }
}
async function removeNode(node: StoryNodeSummary) {
  if (!canEdit.value || deleting.value) return;
  try {
    await ElMessageBox.confirm(
      `确定删除节点“${node.title}”吗？被其他选择引用时服务器会拒绝删除。`,
      "删除剧情节点",
      { type: "warning" },
    );
  } catch {
    return;
  }
  deleting.value = true;
  try {
    await deleteStoryNode(projectId.value, node.id);
    clearSelection();
    await loadGraph();
    markSavedNow();
    ElMessage.success("剧情节点已删除");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "剧情节点删除失败"));
  } finally {
    deleting.value = false;
  }
}

const choiceDialogVisible = ref(false);
const choiceDialogMode = ref<"create" | "edit">("create");
const editingChoiceId = ref<number | null>(null);
const choiceForm = reactive({
  sourceNodeId: 0,
  targetNodeId: 0,
  choiceText: "",
  sortOrder: 0,
  enabled: true,
});
function onConnect(connection: Connection) {
  if (!canEdit.value) return void ElMessage.warning(readOnlyReason.value);
  const source = allNodes.value.find(
    (node) => String(node.id) === connection.source,
  );
  const target = allNodes.value.find(
    (node) => String(node.id) === connection.target,
  );
  if (!source || !target) return;
  if (source.nodeType === "ENDING")
    return void ElMessage.warning("结局节点不能创建选择");
  choiceDialogMode.value = "create";
  editingChoiceId.value = null;
  Object.assign(choiceForm, {
    sourceNodeId: source.id,
    targetNodeId: target.id,
    choiceText: "",
    sortOrder: 0,
    enabled: true,
  });
  choiceDialogVisible.value = true;
}
function openEditChoice(choice: StoryChoiceSummary) {
  if (!canEdit.value) return void ElMessage.warning(readOnlyReason.value);
  choiceDialogMode.value = "edit";
  editingChoiceId.value = choice.id;
  Object.assign(choiceForm, choice);
  choiceDialogVisible.value = true;
}
function choicePayload(): SaveStoryChoicePayload {
  return {
    targetNodeId: choiceForm.targetNodeId,
    choiceText: choiceForm.choiceText.trim(),
    sortOrder: choiceForm.sortOrder,
    enabled: choiceForm.enabled,
  };
}
async function saveChoice() {
  if (!canEdit.value || saving.value) return;
  const payload = choicePayload();
  if (!payload.choiceText) return void ElMessage.warning("请输入选项文字");
  if (!Number.isInteger(payload.sortOrder) || payload.sortOrder < 0)
    return void ElMessage.warning("排序值必须是非负整数");
  saving.value = true;
  try {
    const saved =
      choiceDialogMode.value === "create"
        ? await createStoryChoice(
            projectId.value,
            choiceForm.sourceNodeId,
            payload,
          )
        : await updateStoryChoice(
            projectId.value,
            choiceForm.sourceNodeId,
            editingChoiceId.value!,
            payload,
          );
    await loadGraph();
    selectedChoiceId.value = saved.id;
    selectedNodeId.value = null;
    choiceDialogVisible.value = false;
    markSavedNow();
    ElMessage.success(
      choiceDialogMode.value === "create" ? "剧情选择已创建" : "剧情选择已更新",
    );
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "剧情选择保存失败"));
  } finally {
    saving.value = false;
  }
}
async function removeChoice(choice: StoryChoiceSummary) {
  if (!canEdit.value || deleting.value) return;
  try {
    await ElMessageBox.confirm(
      `确定删除选择“${choice.choiceText}”吗？`,
      "删除剧情选择",
      { type: "warning" },
    );
  } catch {
    return;
  }
  deleting.value = true;
  try {
    await deleteStoryChoice(projectId.value, choice.sourceNodeId, choice.id);
    selectedChoiceId.value = null;
    await loadGraph();
    markSavedNow();
    ElMessage.success("剧情选择已删除");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "剧情选择删除失败"));
  } finally {
    deleting.value = false;
  }
}

async function onNodeDragStop({ node }: NodeDragEvent) {
  if (!canEdit.value) return;
  positionSaving.value = true;
  try {
    await updateStoryNodePositions(projectId.value, [
      {
        nodeId: Number(node.id),
        positionX: node.position.x,
        positionY: node.position.y,
      },
    ]);
    const item = flowNodes.value.find((candidate) => candidate.id === node.id);
    if (item?.data)
      item.data.summary = {
        ...item.data.summary,
        positionX: node.position.x,
        positionY: node.position.y,
      };
    markSavedNow();
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "节点坐标保存失败"));
    await loadGraph();
  } finally {
    positionSaving.value = false;
  }
}
function onNodeClick({ node }: NodeMouseEvent) {
  selectedNodeId.value = Number(node.id);
  selectedChoiceId.value = null;
}
async function onEdgeClick({ edge }: EdgeMouseEvent) {
  const choice = edge.data?.choice as StoryChoiceSummary | undefined;
  if (!choice) return;
  selectedChoiceId.value = choice.id;
  selectedNodeId.value = null;
  try {
    const rules = await getChoiceRules(
      projectId.value,
      choice.sourceNodeId,
      choice.id,
    );
    onRulesSummary({
      choiceId: rules.choiceId,
      conditionCount: rules.conditions.length,
      effectCount: rules.effects.length,
    });
  } catch {
    /* 规则对话框会展示可重试错误 */
  }
}
const ruleDialogVisible = ref(false);
function onRulesSummary(summary: RuleSummary) {
  ruleSummaries.value = { ...ruleSummaries.value, [summary.choiceId]: summary };
}

const draftVisible = ref(false);
const drafts = ref<ChoiceDraft[]>([]);
const draftLoading = ref(false);
const draftSavingId = ref<number | null>(null);
const draftTargets = reactive<Record<number, number>>({});
const newDraft = reactive({ sourceNodeId: 0, choiceText: "", sortOrder: 0 });
async function loadDrafts() {
  draftLoading.value = true;
  try {
    drafts.value = (await loadAllPages((page, size) => listChoiceDrafts(projectId.value, page, size))).items;
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "选项草稿加载失败"));
  } finally {
    draftLoading.value = false;
  }
}
async function openDrafts() {
  draftVisible.value = true;
  newDraft.sourceNodeId = normalNodes.value[0]?.id ?? 0;
  await loadDrafts();
}
async function addDraft() {
  if (!canEdit.value || !newDraft.sourceNodeId)
    return void ElMessage.warning("请先创建普通剧情节点");
  draftSavingId.value = 0;
  try {
    await createChoiceDraft(projectId.value, {
      ...newDraft,
      choiceText: newDraft.choiceText.trim() || null,
    });
    Object.assign(newDraft, {
      sourceNodeId: newDraft.sourceNodeId,
      choiceText: "",
      sortOrder: 0,
    });
    await loadDrafts();
    ElMessage.success("草稿已保存");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "草稿保存失败"));
  } finally {
    draftSavingId.value = null;
  }
}
async function saveDraft(draft: ChoiceDraft) {
  draftSavingId.value = draft.id;
  try {
    await updateChoiceDraft(projectId.value, draft.id, {
      sourceNodeId: draft.sourceNodeId,
      choiceText: draft.choiceText?.trim() || null,
      sortOrder: draft.sortOrder,
    });
    await loadDrafts();
    ElMessage.success("草稿已更新");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "草稿更新失败"));
  } finally {
    draftSavingId.value = null;
  }
}
async function removeDraft(draft: ChoiceDraft) {
  try {
    await ElMessageBox.confirm("确定删除这条选项草稿吗？", "删除草稿", {
      type: "warning",
    });
  } catch {
    return;
  }
  draftSavingId.value = draft.id;
  try {
    await deleteChoiceDraft(projectId.value, draft.id);
    await loadDrafts();
    ElMessage.success("草稿已删除");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "草稿删除失败"));
  } finally {
    draftSavingId.value = null;
  }
}
async function promoteDraft(draft: ChoiceDraft) {
  const targetNodeId = draftTargets[draft.id];
  if (!draft.choiceText?.trim())
    return void ElMessage.warning("请先填写并保存选项文字");
  if (!targetNodeId) return void ElMessage.warning("请选择转正后的目标节点");
  draftSavingId.value = draft.id;
  try {
    await publishChoiceDraft(projectId.value, draft.id, targetNodeId);
    await Promise.all([loadDrafts(), loadGraph()]);
    ElMessage.success("草稿已转为正式选择");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "草稿转正失败"));
  } finally {
    draftSavingId.value = null;
  }
}

onMounted(loadGraph);
</script>

<template>
  <section class="story-page">
    <ExcelImportDialog v-if="excelVisible" v-model="excelVisible" :project-id="projectId" :existing-nodes="flowNodes.length>0" @imported="excelImported" />
    <div class="page-heading">
      <div>
        <p class="eyebrow">STORY GRAPH</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/branch-flow.png"
            alt=""
            aria-hidden="true"
          />
          <h1>剧情节点</h1>
        </div>
        <p class="muted">编辑节点、选择、条件效果和未完成的选项草稿。</p>
      </div>
      <div class="heading-actions">
        <el-button v-if="canEdit" :disabled="loading || Boolean(loadError)" @click="excelVisible=true">Excel 生成关系图</el-button>
        <el-button @click="openDrafts">选项草稿</el-button
        ><el-button
          v-if="canEdit"
          type="primary"
          size="large"
          :disabled="loading || Boolean(loadError)"
          @click="openCreateNode"
          >新增节点</el-button
        >
      </div>
    </div>
    <el-alert
      v-if="!canEdit"
      :title="readOnlyReason"
      type="warning"
      show-icon
      :closable="false"
      class="readonly-alert"
    />
    <div class="graph-meta">
      <span>{{ flowNodes.length }} 个节点</span
      ><span>{{ flowEdges.length }} 条正式选择</span
      ><span>{{
        positionSaving ? "正在保存坐标……" : `状态：${lastSavedAt}`
      }}</span>
    </div>
    <el-result
      v-if="loadError"
      icon="error"
      title="剧情图加载失败"
      :sub-title="loadError"
      ><template #extra
        ><el-button type="primary" @click="loadGraph"
          >重新加载</el-button
        ></template
      ></el-result
    >
    <div v-else class="graph-workspace" v-loading="loading">
      <div class="canvas-panel">
        <VueFlow
          v-model:nodes="flowNodes"
          v-model:edges="flowEdges"
          fit-view-on-init
          :min-zoom="0.3"
          :max-zoom="1.8"
          :nodes-draggable="canEdit"
          :nodes-connectable="canEdit"
          :delete-key-code="null"
          class="story-flow"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @pane-click="clearSelection"
          @node-drag-stop="onNodeDragStop"
          @connect="onConnect"
        >
          <template #node-story="{ data }"
            ><div
              class="story-node-card"
              :class="{
                start: data.summary.isStart,
                ending: data.summary.nodeType === 'ENDING',
              }"
            >
              <Handle type="target" :position="Position.Left" />
              <div class="badges">
                <span v-if="data.summary.isStart">起点</span
                ><span>{{
                  data.summary.nodeType === "ENDING" ? "结局" : "普通"
                }}</span>
              </div>
              <strong>{{ data.summary.title }}</strong
              ><small>{{ data.summary.nodeKey }}</small>
              <p>{{ data.summary.scene || "未设置场景" }}</p>
              <Handle
                v-if="data.summary.nodeType !== 'ENDING'"
                type="source"
                :position="Position.Right"
              /></div
          ></template>
        </VueFlow>
        <div v-if="!loading && !flowNodes.length" class="empty">
          <el-empty description="当前项目暂无剧情节点"
            ><el-button v-if="canEdit" type="primary" @click="openCreateNode"
              >创建第一个节点</el-button
            ></el-empty
          >
        </div>
        <div class="canvas-tip">
          拖动节点保存坐标；从右侧连接点拖向另一节点可创建选择
        </div>
      </div>
      <aside class="inspector">
        <template v-if="selectedNode"
          ><p class="label">已选择节点</p>
          <h2>{{ selectedNode.title }}</h2>
          <el-tag v-if="selectedNode.isStart" type="success">起点</el-tag
          ><el-tag
            :type="selectedNode.nodeType === 'ENDING' ? 'warning' : 'info'"
            >{{ selectedNode.nodeType }}</el-tag
          >
          <dl>
            <div>
              <dt>标识</dt>
              <dd>{{ selectedNode.nodeKey }}</dd>
            </div>
            <div>
              <dt>场景</dt>
              <dd>{{ selectedNode.scene || "未设置" }}</dd>
            </div>
            <div>
              <dt>正文</dt>
              <dd>{{ selectedNode.content || "未填写" }}</dd>
            </div>
          </dl>
          <div v-if="canEdit" class="actions">
            <el-button type="primary" @click="openEditNode(selectedNode)"
              >编辑</el-button
            ><el-button
              type="danger"
              plain
              :loading="deleting"
              @click="removeNode(selectedNode)"
              >删除</el-button
            >
          </div></template
        >
        <template v-else-if="selectedChoice"
          ><p class="label">已选择剧情选择</p>
          <h2>{{ selectedChoice.choiceText }}</h2>
          <el-tag :type="selectedChoice.enabled ? 'success' : 'info'">{{
            selectedChoice.enabled ? "已启用" : "已停用"
          }}</el-tag>
          <dl>
            <div>
              <dt>起点</dt>
              <dd>{{ nodeTitle(selectedChoice.sourceNodeId) }}</dd>
            </div>
            <div>
              <dt>目标</dt>
              <dd>{{ nodeTitle(selectedChoice.targetNodeId) }}</dd>
            </div>
            <div>
              <dt>规则</dt>
              <dd>
                条件 {{ selectedRuleSummary?.conditionCount ?? "—" }} · 效果
                {{ selectedRuleSummary?.effectCount ?? "—" }}
              </dd>
            </div>
          </dl>
          <el-button class="wide" @click="ruleDialogVisible = true">{{
            canEdit ? "编辑条件与效果" : "查看条件与效果"
          }}</el-button>
          <div v-if="canEdit" class="actions">
            <el-button type="primary" @click="openEditChoice(selectedChoice)"
              >编辑</el-button
            ><el-button
              type="danger"
              plain
              :loading="deleting"
              @click="removeChoice(selectedChoice)"
              >删除</el-button
            >
          </div></template
        >
        <template v-else
          ><p class="label">检查器</p>
          <h2>选择画布元素</h2>
          <p class="muted">点击节点或连线查看详细信息。</p></template
        >
      </aside>
    </div>

    <el-dialog
      v-model="nodeDialogVisible"
      :title="nodeDialogMode === 'create' ? '新增剧情节点' : '编辑剧情节点'"
      width="620px"
      ><el-form label-position="top"
        ><div class="form-row">
          <el-form-item label="节点标识" required
            ><el-input
              v-model="nodeForm.nodeKey"
              maxlength="64" /></el-form-item
          ><el-form-item label="节点类型" required
            ><el-select v-model="nodeForm.nodeType"
              ><el-option label="普通节点" value="NORMAL" /><el-option
                label="结局节点"
                value="ENDING" /></el-select
          ></el-form-item>
        </div>
        <el-form-item label="节点标题" required
          ><el-input v-model="nodeForm.title" maxlength="120" /></el-form-item
        ><el-form-item label="场景"
          ><el-input v-model="nodeForm.scene" maxlength="100" /></el-form-item
        ><el-form-item label="剧情正文"
          ><el-input
            v-model="nodeForm.content"
            type="textarea"
            :rows="6"
            maxlength="10000"
            show-word-limit /></el-form-item
        ><el-form-item
          ><el-switch
            v-model="nodeForm.isStart"
            active-text="设为项目起点"
            :disabled="
              nodeForm.nodeType === 'ENDING'
            " /></el-form-item></el-form
      ><template #footer
        ><el-button @click="nodeDialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveNode"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="choiceDialogVisible"
      :title="choiceDialogMode === 'create' ? '创建剧情选择' : '编辑剧情选择'"
      width="520px"
      ><el-form label-position="top"
        ><el-form-item label="起始节点"
          ><el-input
            :model-value="nodeTitle(choiceForm.sourceNodeId)"
            disabled /></el-form-item
        ><el-form-item label="目标节点" required
          ><el-select v-model="choiceForm.targetNodeId" class="wide"
            ><el-option
              v-for="node in allNodes"
              :key="node.id"
              :label="node.title"
              :value="node.id" /></el-select></el-form-item
        ><el-form-item label="选项文字" required
          ><el-input
            v-model="choiceForm.choiceText"
            type="textarea"
            maxlength="500"
        /></el-form-item>
        <div class="form-row">
          <el-form-item label="排序值"
            ><el-input-number
              v-model="choiceForm.sortOrder"
              :min="0"
              step-strictly /></el-form-item
          ><el-form-item label="启用"
            ><el-switch v-model="choiceForm.enabled"
          /></el-form-item></div></el-form
      ><template #footer
        ><el-button @click="choiceDialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveChoice"
          >保存</el-button
        ></template
      ></el-dialog
    >

    <el-dialog v-model="draftVisible" title="选项草稿" width="980px"
      ><el-alert
        title="草稿不会进入正式剧情图、检测或试玩；转正后才成为正式选择。"
        type="info"
        :closable="false" />
      <div v-if="canEdit" class="new-draft">
        <el-select v-model="newDraft.sourceNodeId" placeholder="起始节点"
          ><el-option
            v-for="node in normalNodes"
            :key="node.id"
            :label="node.title"
            :value="node.id" /></el-select
        ><el-input
          v-model="newDraft.choiceText"
          placeholder="草稿文字（允许稍后填写）"
          maxlength="500"
        /><el-input-number
          v-model="newDraft.sortOrder"
          :min="0"
          step-strictly
        /><el-button
          type="primary"
          :loading="draftSavingId === 0"
          @click="addDraft"
          >保存新草稿</el-button
        >
      </div>
      <el-table v-loading="draftLoading" :data="drafts"
        ><el-table-column label="起始节点" min-width="150"
          ><template #default="{ row }"
            ><el-select v-model="row.sourceNodeId" :disabled="!canEdit"
              ><el-option
                v-for="node in normalNodes"
                :key="node.id"
                :label="node.title"
                :value="node.id" /></el-select></template></el-table-column
        ><el-table-column label="草稿文字" min-width="230"
          ><template #default="{ row }"
            ><el-input
              v-model="row.choiceText"
              :disabled="!canEdit" /></template></el-table-column
        ><el-table-column label="排序" width="100"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.sortOrder"
              :min="0"
              controls-position="right"
              :disabled="!canEdit" /></template></el-table-column
        ><el-table-column label="转正目标" min-width="150"
          ><template #default="{ row }"
            ><el-select
              v-model="draftTargets[row.id]"
              placeholder="选择目标"
              :disabled="!canEdit"
              ><el-option
                v-for="node in allNodes"
                :key="node.id"
                :label="node.title"
                :value="node.id" /></el-select></template></el-table-column
        ><el-table-column label="操作" width="210"
          ><template #default="{ row }"
            ><template v-if="canEdit"
              ><el-button
                link
                type="primary"
                :loading="draftSavingId === row.id"
                @click="saveDraft(row)"
                >保存</el-button
              ><el-button
                link
                type="success"
                :disabled="draftSavingId !== null"
                @click="promoteDraft(row)"
                >转正</el-button
              ><el-button
                link
                type="danger"
                :disabled="draftSavingId !== null"
                @click="removeDraft(row)"
                >删除</el-button
              ></template
            ><span v-else>只读</span></template
          ></el-table-column
        ><template #empty
          ><el-empty description="暂无选项草稿" /></template></el-table
    ></el-dialog>

    <ChoiceRuleEditorDialog
      v-model="ruleDialogVisible"
      :project-id="projectId"
      :node-id="selectedChoice?.sourceNodeId ?? null"
      :choice-id="selectedChoice?.id ?? null"
      :choice-text="selectedChoice?.choiceText ?? ''"
      :readonly="!canEdit"
      @loaded="onRulesSummary"
      @saved="onRulesSummary"
    />
  </section>
</template>

<style scoped>
.story-page {
  max-width: 1320px;
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
.heading-actions,
.actions {
  display: flex;
  gap: 10px;
}
.readonly-alert {
  margin-top: 20px;
}
.graph-meta {
  display: flex;
  gap: 20px;
  margin-top: 20px;
  padding: 11px 14px;
  border: 1px solid #e1e5e0;
  border-radius: 8px;
  background: #fff;
  color: #6d7872;
}
.graph-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  min-height: 620px;
  margin-top: 14px;
  overflow: hidden;
  border: 1px solid #dfe3de;
  border-radius: 14px;
  background: #fff;
}
.canvas-panel {
  position: relative;
  min-width: 0;
  background-color: #f8f9f6;
  background-image: radial-gradient(#cbd2cd 1px, transparent 1px);
  background-size: 20px 20px;
}
.story-flow {
  width: 100%;
  height: 620px;
}
.story-node-card {
  position: relative;
  width: 190px;
  min-height: 112px;
  padding: 14px 16px;
  border: 2px solid #8fa198;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 20px #394d4314;
}
.story-node-card.start {
  border-color: #4d8b69;
}
.story-node-card.ending {
  border-color: #b87547;
  background: #fffaf5;
}
.badges {
  display: flex;
  gap: 6px;
  color: #7c8881;
  font-size: 10px;
}
.badges span {
  padding: 2px 6px;
  border-radius: 10px;
  background: #edf0ed;
}
.story-node-card strong,
.story-node-card small,
.story-node-card p {
  display: block;
}
.story-node-card strong {
  margin-top: 9px;
}
.story-node-card small {
  color: #89938e;
}
.story-node-card p {
  color: #69746e;
  font-size: 11px;
}
.empty {
  position: absolute;
  inset: 0;
  display: grid;
  background: #f8f9f6e8;
  place-items: center;
}
.canvas-tip {
  position: absolute;
  bottom: 14px;
  left: 16px;
  padding: 7px 10px;
  border-radius: 6px;
  background: #ffffffdf;
  color: #69746e;
  font-size: 11px;
}
.inspector {
  padding: 24px;
  border-left: 1px solid #e1e5e0;
}
.inspector .label {
  color: #b2663d;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}
.inspector h2 {
  margin: 8px 0 14px;
}
.inspector .el-tag + .el-tag {
  margin-left: 6px;
}
.inspector dl div {
  padding: 10px 0;
  border-bottom: 1px solid #edf0ed;
}
.inspector dt {
  color: #8a938e;
  font-size: 11px;
}
.inspector dd {
  margin: 5px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.actions {
  margin-top: 18px;
}
.wide {
  width: 100%;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.new-draft {
  display: grid;
  grid-template-columns: 180px 1fr 120px 130px;
  gap: 10px;
  margin: 16px 0;
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
@media (max-width: 980px) {
  .graph-workspace {
    grid-template-columns: 1fr;
  }
  .inspector {
    border-top: 1px solid #e1e5e0;
    border-left: 0;
  }
  .new-draft {
    grid-template-columns: 1fr;
  }
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
