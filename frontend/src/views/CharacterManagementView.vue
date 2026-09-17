<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import type { MemberRole } from "@/api/members";
import {
  createCharacter,
  deleteCharacter,
  listCharacters,
  updateCharacter,
  type CharacterSummary,
  type SaveCharacterPayload,
} from "@/api/characters";

interface CharacterForm {
  name: string;
  summary: string;
  personality: string;
  goal: string;
  valueOrder: string;
}

const route = useRoute();
const projectId = computed(() => Number(route.params.id));

const props = defineProps<{
  currentUserRole: MemberRole;
  projectStatus: "ACTIVE" | "ARCHIVED";
}>();

const characters = ref<CharacterSummary[]>([]);
const keyword = ref("");

const canEditCharacters = computed(
  () =>
    props.projectStatus === "ACTIVE" &&
    (props.currentUserRole === "OWNER" || props.currentUserRole === "EDITOR"),
);
const readOnlyReason = computed(() =>
  props.projectStatus === "ARCHIVED" ? "项目已归档" : "当前角色只能查看",
);

const activeCharacters = computed(() =>
  characters.value.filter((character) => character.status !== "DELETED"),
);

const filteredCharacters = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLocaleLowerCase();
  if (!normalizedKeyword) return activeCharacters.value;
  return activeCharacters.value.filter((character) =>
    character.name.toLocaleLowerCase().includes(normalizedKeyword),
  );
});

const loading = ref(false);
const loadError = ref("");
const saving = ref(false);
const deletingCharacterId = ref<number | null>(null);
const isOperating = computed(
  () => saving.value || deletingCharacterId.value !== null,
);

const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const editingCharacterId = ref<number | null>(null);
const form = reactive<CharacterForm>({
  name: "",
  summary: "",
  personality: "",
  goal: "",
  valueOrder: "",
});

const detailVisible = ref(false);
const selectedCharacter = ref<CharacterSummary | null>(null);

function optionalText(value: string) {
  const normalizedValue = value.trim();
  return normalizedValue || undefined;
}

function formPayload(): SaveCharacterPayload {
  return {
    name: form.name.trim(),
    summary: optionalText(form.summary),
    personality: optionalText(form.personality),
    goal: optionalText(form.goal),
    valueOrder: optionalText(form.valueOrder),
  };
}

function resetForm() {
  form.name = "";
  form.summary = "";
  form.personality = "";
  form.goal = "";
  form.valueOrder = "";
  editingCharacterId.value = null;
}

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    characters.value = await listCharacters(projectId.value);
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "无法获取角色档案，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  if (!canEditCharacters.value) {
    ElMessage.warning("当前角色没有编辑角色档案的权限");
    return;
  }
  dialogMode.value = "create";
  resetForm();
  dialogVisible.value = true;
}

function openEditDialog(character: CharacterSummary) {
  if (!canEditCharacters.value) {
    ElMessage.warning("当前角色没有编辑角色档案的权限");
    return;
  }
  dialogMode.value = "edit";
  editingCharacterId.value = character.id;
  form.name = character.name;
  form.summary = character.summary || "";
  form.personality = character.personality || "";
  form.goal = character.goal || "";
  form.valueOrder = character.valueOrder || "";
  dialogVisible.value = true;
}

function showCharacterDetail(character: CharacterSummary) {
  selectedCharacter.value = character;
  detailVisible.value = true;
}

async function saveCharacter() {
  if (!canEditCharacters.value || saving.value) return;

  const payload = formPayload();
  if (!payload.name) {
    ElMessage.warning("请输入角色姓名");
    return;
  }

  saving.value = true;
  try {
    if (dialogMode.value === "create") {
      characters.value.push(await createCharacter(projectId.value, payload));
      ElMessage.success("角色档案已创建并保存");
    } else {
      if (editingCharacterId.value === null) return;
      const updated = await updateCharacter(
        projectId.value,
        editingCharacterId.value,
        payload,
      );
      const characterIndex = characters.value.findIndex(
        (character) => character.id === updated.id,
      );
      if (characterIndex !== -1) characters.value[characterIndex] = updated;
      if (selectedCharacter.value?.id === editingCharacterId.value) {
        selectedCharacter.value = updated;
      }
      ElMessage.success("角色档案已更新并保存");
    }
    dialogVisible.value = false;
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "保存角色档案失败"));
  } finally {
    saving.value = false;
  }
}

async function removeCharacter(character: CharacterSummary) {
  if (!canEditCharacters.value || deletingCharacterId.value !== null) return;

  try {
    await ElMessageBox.confirm(
      `确定要删除角色档案“${character.name}”吗？删除后将不再出现在角色列表中。`,
      "删除角色档案",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
  } catch {
    return;
  }

  deletingCharacterId.value = character.id;
  try {
    await deleteCharacter(projectId.value, character.id);
    characters.value = characters.value.filter(
      (item) => item.id !== character.id,
    );
    if (selectedCharacter.value?.id === character.id) {
      selectedCharacter.value = null;
      detailVisible.value = false;
    }
    ElMessage.success("角色档案已删除");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "删除角色档案失败"));
  } finally {
    deletingCharacterId.value = null;
  }
}

function formatUpdatedAt(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString("zh-CN", { hour12: false });
}

onMounted(load);
</script>

<template>
  <section class="character-page">
    <div class="character-heading">
      <div>
        <p class="eyebrow">CHARACTER ARCHIVE</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/profile-card.png"
            alt=""
            aria-hidden="true"
          />
          <h1>角色档案</h1>
        </div>
        <p class="muted">记录角色的背景、性格、目标与价值排序。</p>
      </div>
      <el-button
        v-if="canEditCharacters"
        type="primary"
        size="large"
        :disabled="loading || Boolean(loadError) || isOperating"
        @click="openCreateDialog"
      >
        新建角色
      </el-button>
    </div>

    <div class="filters">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索角色名"
        class="keyword-input"
      />
      <span class="result-count"
        >共 {{ filteredCharacters.length }} 名角色</span
      >
    </div>

    <el-result
      v-if="loadError"
      icon="error"
      title="角色档案加载失败"
      :sub-title="loadError"
      class="load-result"
    >
      <template #extra>
        <el-button type="primary" :loading="loading" @click="load">
          重新加载
        </el-button>
      </template>
    </el-result>

    <div
      v-else
      v-loading="loading"
      element-loading-text="正在加载角色档案……"
      class="character-list"
    >
      <div v-if="filteredCharacters.length" class="character-grid">
        <article
          v-for="character in filteredCharacters"
          :key="character.id"
          class="character-card"
        >
          <div class="card-heading">
            <div class="character-mark">{{ character.name.slice(0, 2) }}</div>
            <div>
              <h2>{{ character.name }}</h2>
              <span>ACTIVE</span>
            </div>
          </div>
          <p class="summary">
            {{ character.summary || "尚未填写角色简介。" }}
          </p>
          <dl>
            <div>
              <dt>目标</dt>
              <dd>{{ character.goal || "尚未填写" }}</dd>
            </div>
            <div>
              <dt>价值排序</dt>
              <dd>{{ character.valueOrder || "尚未填写" }}</dd>
            </div>
          </dl>
          <footer>
            <el-button link @click="showCharacterDetail(character)"
              >查看档案</el-button
            >
            <div v-if="canEditCharacters">
              <el-button
                type="primary"
                link
                :disabled="isOperating"
                @click="openEditDialog(character)"
              >
                编辑
              </el-button>
              <el-button
                type="danger"
                link
                :loading="deletingCharacterId === character.id"
                :disabled="isOperating && deletingCharacterId !== character.id"
                @click="removeCharacter(character)"
              >
                删除
              </el-button>
            </div>
            <span v-else class="readonly-text">{{ readOnlyReason }}</span>
          </footer>
        </article>
      </div>

      <el-empty
        v-else
        :description="
          activeCharacters.length
            ? '没有符合名称搜索条件的角色'
            : '当前项目暂无角色档案'
        "
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建角色档案' : '编辑角色档案'"
      width="620px"
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
    >
      <el-form label-position="top">
        <el-form-item label="角色姓名" required>
          <el-input
            v-model="form.name"
            maxlength="80"
            show-word-limit
            placeholder="输入角色姓名"
            :disabled="saving"
          />
        </el-form-item>
        <el-form-item label="角色简介">
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="用一两句话概括角色身份与背景"
            :disabled="saving"
          />
        </el-form-item>
        <el-form-item label="性格特征">
          <el-input
            v-model="form.personality"
            type="textarea"
            :rows="4"
            maxlength="5000"
            show-word-limit
            placeholder="描述性格、习惯、优点和弱点"
            :disabled="saving"
          />
        </el-form-item>
        <el-form-item label="角色目标">
          <el-input
            v-model="form.goal"
            type="textarea"
            :rows="4"
            maxlength="5000"
            show-word-limit
            placeholder="描述角色当前想要实现的目标"
            :disabled="saving"
          />
        </el-form-item>
        <el-form-item label="价值排序">
          <el-input
            v-model="form.valueOrder"
            type="textarea"
            :rows="3"
            maxlength="5000"
            show-word-limit
            placeholder="例如：家人 > 真相 > 责任 > 规则"
            :disabled="saving"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="saving" @click="saveCharacter">
          {{ dialogMode === "create" ? "确定创建" : "保存修改" }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="角色档案详情" size="440px">
      <template v-if="selectedCharacter">
        <div class="detail-heading">
          <div class="character-mark large">
            {{ selectedCharacter.name.slice(0, 2) }}
          </div>
          <div>
            <h2>{{ selectedCharacter.name }}</h2>
            <span
              >最后更新：{{
                formatUpdatedAt(selectedCharacter.updatedAt)
              }}</span
            >
          </div>
        </div>
        <el-descriptions :column="1" border class="details">
          <el-descriptions-item label="角色简介">
            {{ selectedCharacter.summary || "尚未填写" }}
          </el-descriptions-item>
          <el-descriptions-item label="性格特征">
            {{ selectedCharacter.personality || "尚未填写" }}
          </el-descriptions-item>
          <el-descriptions-item label="角色目标">
            {{ selectedCharacter.goal || "尚未填写" }}
          </el-descriptions-item>
          <el-descriptions-item label="价值排序">
            {{ selectedCharacter.valueOrder || "尚未填写" }}
          </el-descriptions-item>
        </el-descriptions>
        <el-button
          v-if="canEditCharacters"
          type="primary"
          class="detail-edit"
          @click="openEditDialog(selectedCharacter)"
        >
          编辑角色档案
        </el-button>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.character-page {
  max-width: 1180px;
}

.character-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}

.character-page h1 {
  margin: 6px 0 8px;
  font-size: 42px;
}

.preview-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 28px;
  padding: 16px 18px;
  border: 1px dashed #c7cec9;
  border-radius: 10px;
  background: #f8f9f6;
}

.preview-tools strong,
.preview-tools span {
  display: block;
}

.preview-tools span {
  margin-top: 4px;
  color: #818b84;
  font-size: 12px;
}

.preview-controls {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.preview-role,
.preview-status {
  width: 120px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  padding: 14px 16px;
  border: 1px solid #e0e4df;
  border-radius: 10px;
  background: #fff;
}

.keyword-input {
  max-width: 430px;
}

.result-count {
  margin-left: auto;
  color: #7b857f;
  font-size: 13px;
  white-space: nowrap;
}

.character-list {
  min-height: 260px;
  margin-top: 16px;
}

.character-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.character-card {
  display: flex;
  min-height: 300px;
  padding: 24px;
  border: 1px solid #dfe3de;
  border-radius: 14px;
  background: #fff;
  flex-direction: column;
  transition: 0.2s;
}

.character-card:hover {
  border-color: #9baba2;
  box-shadow: 0 12px 28px #41584d12;
  transform: translateY(-2px);
}

.card-heading,
.detail-heading {
  display: flex;
  align-items: center;
  gap: 14px;
}

.character-mark {
  display: grid;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  color: #f5eee7;
  background: #49675c;
  font-weight: 700;
  place-items: center;
}

.character-mark.large {
  width: 64px;
  height: 64px;
  font-size: 18px;
}

.card-heading h2,
.detail-heading h2 {
  margin: 0 0 4px;
}

.card-heading span,
.detail-heading span {
  color: #88918c;
  font-size: 11px;
}

.summary {
  min-height: 48px;
  margin: 20px 0;
  color: #5e6963;
  line-height: 1.65;
}

.character-card dl {
  display: grid;
  gap: 12px;
  margin: 0;
}

.character-card dl div {
  display: grid;
  grid-template-columns: 70px 1fr;
  gap: 10px;
}

.character-card dt {
  color: #89928d;
  font-size: 12px;
}

.character-card dd {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #46514b;
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.character-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 20px;
}

.readonly-text {
  color: #909399;
  font-size: 13px;
}

.load-result {
  margin-top: 16px;
  background: #fff;
}

.details {
  margin-top: 28px;
}

.detail-edit {
  width: 100%;
  margin-top: 22px;
}

@media (max-width: 900px) {
  .character-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .character-heading,
  .preview-tools,
  .filters {
    align-items: start;
    flex-direction: column;
  }

  .preview-controls {
    justify-content: flex-start;
  }

  .keyword-input {
    width: 100%;
    max-width: none;
  }

  .result-count {
    margin-left: 0;
  }
}
</style>
