<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import type { MemberRole } from "@/api/members";
import { listCharacters, type CharacterSummary } from "@/api/characters";
import { getStoryGraph, type StoryNodeSummary } from "@/api/storyGraph";
import {
  createKnowledge,
  createRelation,
  deleteKnowledge,
  deleteRelation,
  getNodeCast,
  listKnowledge,
  listRelations,
  replaceNodeCast,
  updateKnowledge,
  updateRelation,
  type CharacterKnowledge,
  type CharacterRelation,
  type KnowledgeLevel,
} from "@/api/characterDetails";

const props = defineProps<{
  currentUserRole: MemberRole;
  projectStatus: "ACTIVE" | "ARCHIVED";
}>();
const route = useRoute();
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
const characters = ref<CharacterSummary[]>([]);
const nodes = ref<StoryNodeSummary[]>([]);
const relations = ref<CharacterRelation[]>([]);
const knowledge = ref<CharacterKnowledge[]>([]);
const selectedCharacterId = ref<number | null>(null);
const selectedNodeId = ref<number | null>(null);
const castIds = ref<number[]>([]);
const loading = ref(true);
const loadError = ref("");
const saving = ref(false);
const deletingId = ref<number | null>(null);

function characterName(id: number) {
  return characters.value.find((item) => item.id === id)?.name || `角色 #${id}`;
}
function nodeName(id?: number | null) {
  return id
    ? nodes.value.find((item) => item.id === id)?.title || `节点 #${id}`
    : "初始即拥有";
}
async function loadBase() {
  loading.value = true;
  loadError.value = "";
  try {
    const [characterList, graph, relationList] = await Promise.all([
      listCharacters(projectId.value),
      getStoryGraph(projectId.value),
      listRelations(projectId.value),
    ]);
    characters.value = characterList.filter((item) => item.status === "ACTIVE");
    nodes.value = graph.nodes;
    relations.value = relationList;
    if (!selectedCharacterId.value && characters.value[0])
      selectedCharacterId.value = characters.value[0].id;
    if (!selectedNodeId.value && nodes.value[0])
      selectedNodeId.value = nodes.value[0].id;
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "角色扩展资料加载失败，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}
async function loadKnowledge() {
  if (!selectedCharacterId.value) {
    knowledge.value = [];
    return;
  }
  try {
    knowledge.value = await listKnowledge(
      projectId.value,
      selectedCharacterId.value,
    );
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "角色知识加载失败"));
  }
}
async function loadCast() {
  if (!selectedNodeId.value) {
    castIds.value = [];
    return;
  }
  try {
    castIds.value = await getNodeCast(projectId.value, selectedNodeId.value);
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "节点出场角色加载失败"));
  }
}
watch(selectedCharacterId, loadKnowledge);
watch(selectedNodeId, loadCast);

const relationVisible = ref(false);
const relationMode = ref<"create" | "edit">("create");
const editingRelationId = ref<number | null>(null);
const relationForm = reactive({
  sourceCharacterId: 0,
  targetCharacterId: 0,
  relationType: "",
  description: "",
});
function openRelation(relation?: CharacterRelation) {
  if (!canEdit.value) return void ElMessage.warning(readOnlyReason.value);
  relationMode.value = relation ? "edit" : "create";
  editingRelationId.value = relation?.id ?? null;
  Object.assign(
    relationForm,
    relation
      ? { ...relation, description: relation.description || "" }
      : {
          sourceCharacterId: characters.value[0]?.id ?? 0,
          targetCharacterId: characters.value[1]?.id ?? 0,
          relationType: "TRUST",
          description: "",
        },
  );
  relationVisible.value = true;
}
async function saveRelation() {
  if (!relationForm.sourceCharacterId || !relationForm.targetCharacterId)
    return void ElMessage.warning("请选择关系双方");
  if (relationForm.sourceCharacterId === relationForm.targetCharacterId)
    return void ElMessage.warning("角色不能与自己建立关系");
  if (!relationForm.relationType.trim())
    return void ElMessage.warning("请输入关系类型");
  saving.value = true;
  try {
    const input = {
      sourceCharacterId: relationForm.sourceCharacterId,
      targetCharacterId: relationForm.targetCharacterId,
      relationType: relationForm.relationType.trim(),
      description: relationForm.description.trim() || null,
    };
    if (relationMode.value === "create")
      await createRelation(projectId.value, input);
    else await updateRelation(projectId.value, editingRelationId.value!, input);
    relations.value = await listRelations(projectId.value);
    relationVisible.value = false;
    ElMessage.success("角色关系已保存");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "角色关系保存失败"));
  } finally {
    saving.value = false;
  }
}
async function removeRelation(item: CharacterRelation) {
  try {
    await ElMessageBox.confirm("确定删除这条角色关系吗？", "删除角色关系", {
      type: "warning",
    });
  } catch {
    return;
  }
  deletingId.value = item.id;
  try {
    await deleteRelation(projectId.value, item.id);
    relations.value = await listRelations(projectId.value);
    ElMessage.success("角色关系已删除");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "角色关系删除失败"));
  } finally {
    deletingId.value = null;
  }
}

const knowledgeVisible = ref(false);
const knowledgeMode = ref<"create" | "edit">("create");
const editingKnowledgeId = ref<number | null>(null);
const knowledgeForm = reactive({
  knowledgeKey: "",
  knowledgeLevel: "UNKNOWN" as KnowledgeLevel,
  description: "",
  acquiredNodeId: null as number | null,
});
function openKnowledge(item?: CharacterKnowledge) {
  if (!canEdit.value) return void ElMessage.warning(readOnlyReason.value);
  if (!selectedCharacterId.value) return void ElMessage.warning("请先选择角色");
  knowledgeMode.value = item ? "edit" : "create";
  editingKnowledgeId.value = item?.id ?? null;
  Object.assign(
    knowledgeForm,
    item
      ? {
          knowledgeKey: item.knowledgeKey,
          knowledgeLevel: item.knowledgeLevel,
          description: item.description || "",
          acquiredNodeId: item.acquiredNodeId ?? null,
        }
      : {
          knowledgeKey: "",
          knowledgeLevel: "UNKNOWN",
          description: "",
          acquiredNodeId: null,
        },
  );
  knowledgeVisible.value = true;
}
async function saveKnowledge() {
  if (!selectedCharacterId.value) return;
  if (!/^[A-Za-z][A-Za-z0-9_]{0,99}$/.test(knowledgeForm.knowledgeKey))
    return void ElMessage.warning(
      "知识标识必须以字母开头，只能包含字母、数字和下划线",
    );
  saving.value = true;
  try {
    const input = {
      knowledgeKey: knowledgeForm.knowledgeKey,
      knowledgeLevel: knowledgeForm.knowledgeLevel,
      description: knowledgeForm.description.trim() || null,
      acquiredNodeId: knowledgeForm.acquiredNodeId,
    };
    if (knowledgeMode.value === "create")
      await createKnowledge(projectId.value, selectedCharacterId.value, input);
    else
      await updateKnowledge(
        projectId.value,
        selectedCharacterId.value,
        editingKnowledgeId.value!,
        input,
      );
    await loadKnowledge();
    knowledgeVisible.value = false;
    ElMessage.success("角色知识已保存");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "角色知识保存失败"));
  } finally {
    saving.value = false;
  }
}
async function removeKnowledge(item: CharacterKnowledge) {
  if (!selectedCharacterId.value) return;
  try {
    await ElMessageBox.confirm("确定删除这条角色知识吗？", "删除角色知识", {
      type: "warning",
    });
  } catch {
    return;
  }
  deletingId.value = item.id;
  try {
    await deleteKnowledge(projectId.value, selectedCharacterId.value, item.id);
    await loadKnowledge();
    ElMessage.success("角色知识已删除");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "角色知识删除失败"));
  } finally {
    deletingId.value = null;
  }
}
async function saveCast() {
  if (!canEdit.value || !selectedNodeId.value)
    return void ElMessage.warning(readOnlyReason.value);
  saving.value = true;
  try {
    castIds.value = await replaceNodeCast(
      projectId.value,
      selectedNodeId.value,
      castIds.value,
    );
    ElMessage.success("节点出场角色已保存");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "出场角色保存失败"));
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  await loadBase();
  await Promise.all([loadKnowledge(), loadCast()]);
});
</script>

<template>
  <section class="detail-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">CHARACTER DETAILS</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/relationship-rings.png"
            alt=""
            aria-hidden="true"
          />
          <h1>关系与知识</h1>
        </div>
        <p class="muted">维护角色之间的有向关系、知识边界和节点出场名单。</p>
      </div>
    </div>
    <el-alert
      v-if="!canEdit"
      :title="readOnlyReason"
      type="warning"
      show-icon
      :closable="false"
      class="notice"
    /><el-result
      v-if="loadError"
      icon="error"
      title="扩展资料加载失败"
      :sub-title="loadError"
      ><template #extra
        ><el-button type="primary" @click="loadBase"
          >重新加载</el-button
        ></template
      ></el-result
    >
    <div v-else v-loading="loading" class="sections">
      <section class="card">
        <div class="section-head">
          <div>
            <h2>角色关系</h2>
            <p>关系有方向，例如“林澈 → 苏弥：TRUST”。</p>
          </div>
          <el-button v-if="canEdit" type="primary" @click="openRelation()"
            >新增关系</el-button
          >
        </div>
        <el-table :data="relations"
          ><el-table-column label="来源角色"
            ><template #default="{ row }">{{
              characterName(row.sourceCharacterId)
            }}</template></el-table-column
          ><el-table-column
            prop="relationType"
            label="关系类型" /><el-table-column label="目标角色"
            ><template #default="{ row }">{{
              characterName(row.targetCharacterId)
            }}</template></el-table-column
          ><el-table-column label="说明" min-width="220"
            ><template #default="{ row }">{{
              row.description || "—"
            }}</template></el-table-column
          ><el-table-column v-if="canEdit" label="操作" width="120"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="openRelation(row)"
                >编辑</el-button
              ><el-button
                link
                type="danger"
                :loading="deletingId === row.id"
                @click="removeRelation(row)"
                >删除</el-button
              ></template
            ></el-table-column
          ><template #empty><el-empty description="暂无角色关系" /></template
        ></el-table>
      </section>
      <section class="card">
        <div class="section-head">
          <div>
            <h2>角色知识边界</h2>
            <p>到达指定节点时，该知识首次进入模拟快照。</p>
          </div>
          <div class="select-actions">
            <el-select v-model="selectedCharacterId" placeholder="选择角色"
              ><el-option
                v-for="character in characters"
                :key="character.id"
                :label="character.name"
                :value="character.id" /></el-select
            ><el-button
              v-if="canEdit"
              type="primary"
              :disabled="!selectedCharacterId"
              @click="openKnowledge()"
              >新增知识</el-button
            >
          </div>
        </div>
        <el-table :data="knowledge"
          ><el-table-column
            prop="knowledgeKey"
            label="知识标识" /><el-table-column label="等级"
            ><template #default="{ row }"
              ><el-tag>{{ row.knowledgeLevel }}</el-tag></template
            ></el-table-column
          ><el-table-column label="获得节点"
            ><template #default="{ row }">{{
              nodeName(row.acquiredNodeId)
            }}</template></el-table-column
          ><el-table-column label="说明" min-width="220"
            ><template #default="{ row }">{{
              row.description || "—"
            }}</template></el-table-column
          ><el-table-column v-if="canEdit" label="操作" width="120"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="openKnowledge(row)"
                >编辑</el-button
              ><el-button
                link
                type="danger"
                :loading="deletingId === row.id"
                @click="removeKnowledge(row)"
                >删除</el-button
              ></template
            ></el-table-column
          ><template #empty
            ><el-empty description="该角色暂无知识定义" /></template
        ></el-table>
      </section>
      <section class="card">
        <div class="section-head">
          <div>
            <h2>节点出场角色</h2>
            <p>整体保存一个节点的出场角色列表，失败时服务器整批回滚。</p>
          </div>
          <el-button
            v-if="canEdit"
            type="primary"
            :loading="saving"
            :disabled="!selectedNodeId"
            @click="saveCast"
            >保存出场名单</el-button
          >
        </div>
        <div class="cast-form">
          <el-select v-model="selectedNodeId" filterable placeholder="选择节点"
            ><el-option
              v-for="node in nodes"
              :key="node.id"
              :label="node.title"
              :value="node.id" /></el-select
          ><el-select
            v-model="castIds"
            multiple
            filterable
            placeholder="选择出场角色"
            ><el-option
              v-for="character in characters"
              :key="character.id"
              :label="character.name"
              :value="character.id"
          /></el-select>
        </div>
      </section>
    </div>
    <el-dialog
      v-model="relationVisible"
      :title="relationMode === 'create' ? '新增角色关系' : '编辑角色关系'"
      width="560px"
      ><el-form label-position="top"
        ><div class="form-row">
          <el-form-item label="来源角色" required
            ><el-select v-model="relationForm.sourceCharacterId"
              ><el-option
                v-for="character in characters"
                :key="character.id"
                :label="character.name"
                :value="character.id" /></el-select></el-form-item
          ><el-form-item label="目标角色" required
            ><el-select v-model="relationForm.targetCharacterId"
              ><el-option
                v-for="character in characters"
                :key="character.id"
                :label="character.name"
                :value="character.id" /></el-select
          ></el-form-item>
        </div>
        <el-form-item label="关系类型" required
          ><el-input
            v-model="relationForm.relationType"
            maxlength="40"
            placeholder="例如 TRUST、RIVAL" /></el-form-item
        ><el-form-item label="说明"
          ><el-input
            v-model="relationForm.description"
            type="textarea"
            maxlength="1000" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="relationVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveRelation"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="knowledgeVisible"
      :title="knowledgeMode === 'create' ? '新增角色知识' : '编辑角色知识'"
      width="560px"
      ><el-form label-position="top"
        ><el-form-item label="知识标识" required
          ><el-input
            v-model="knowledgeForm.knowledgeKey"
            maxlength="100"
            placeholder="例如 secret_map" /></el-form-item
        ><el-form-item label="知识等级" required
          ><el-select v-model="knowledgeForm.knowledgeLevel"
            ><el-option label="UNKNOWN" value="UNKNOWN" /><el-option
              label="SUSPECTED"
              value="SUSPECTED" /><el-option
              label="KNOWN"
              value="KNOWN" /></el-select></el-form-item
        ><el-form-item label="获得节点"
          ><el-select
            v-model="knowledgeForm.acquiredNodeId"
            clearable
            placeholder="留空表示初始即拥有"
            ><el-option
              v-for="node in nodes"
              :key="node.id"
              :label="node.title"
              :value="node.id" /></el-select></el-form-item
        ><el-form-item label="说明"
          ><el-input
            v-model="knowledgeForm.description"
            type="textarea"
            maxlength="1000" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="knowledgeVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveKnowledge"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>

<style scoped>
.detail-page {
  max-width: 1200px;
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
.muted,
.section-head p {
  color: #78847e;
}
.notice {
  margin-top: 18px;
}
.sections {
  display: grid;
  gap: 18px;
  margin-top: 24px;
}
.card {
  padding: 22px;
  border: 1px solid #dfe3de;
  border-radius: 14px;
  background: #fff;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}
.section-head h2 {
  margin: 0;
}
.section-head p {
  margin: 6px 0 0;
}
.select-actions {
  display: flex;
  gap: 10px;
}
.select-actions .el-select {
  width: 180px;
}
.cast-form {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 14px;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.el-form .el-select {
  width: 100%;
}
@media (max-width: 760px) {
  .section-head {
    align-items: stretch;
    flex-direction: column;
  }
  .cast-form,
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
