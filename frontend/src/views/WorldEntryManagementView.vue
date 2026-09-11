<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import type { MemberRole } from "@/api/members";
import {
  createWorldEntry,
  deleteWorldEntry,
  listWorldEntries,
  updateWorldEntry,
  type SaveWorldEntryPayload,
  type WorldEntrySummary,
  type WorldEntryType,
} from "@/api/worldEntries";

const props = defineProps<{
  currentUserRole: MemberRole;
  projectStatus: "ACTIVE" | "ARCHIVED";
}>();

const route = useRoute();
const projectId = computed(() => Number(route.params.id));

const entryTypeOptions: Array<{ label: string; value: WorldEntryType }> = [
  { label: "基础设定", value: "SETTING" },
  { label: "地点", value: "LOCATION" },
  { label: "阵营", value: "FACTION" },
  { label: "历史", value: "HISTORY" },
  { label: "规则", value: "RULE" },
  { label: "其他", value: "OTHER" },
];

const entryTypeLabels: Record<WorldEntryType, string> = {
  SETTING: "基础设定",
  LOCATION: "地点",
  FACTION: "阵营",
  HISTORY: "历史",
  RULE: "规则",
  OTHER: "其他",
};

const entryTypeTagTypes: Record<
  WorldEntryType,
  "primary" | "success" | "warning" | "info" | "danger"
> = {
  SETTING: "primary",
  LOCATION: "success",
  FACTION: "warning",
  HISTORY: "info",
  RULE: "danger",
  OTHER: "info",
};

const entries = ref<WorldEntrySummary[]>([]);
const filterType = ref<"ALL" | WorldEntryType>("ALL");
const keyword = ref("");

const canEditEntries = computed(
  () =>
    props.projectStatus === "ACTIVE" &&
    (props.currentUserRole === "OWNER" || props.currentUserRole === "EDITOR"),
);
const readOnlyReason = computed(() =>
  props.projectStatus === "ARCHIVED" ? "项目已归档" : "当前角色只能查看",
);

const loading = ref(false);
const loadError = ref("");
const saving = ref(false);
const deletingEntryId = ref<number | null>(null);
const isOperating = computed(
  () => saving.value || deletingEntryId.value !== null,
);

const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const editingEntryId = ref<number | null>(null);
const form = reactive<SaveWorldEntryPayload>({
  entryType: "SETTING",
  title: "",
  content: "",
  sortOrder: 0,
});

const filteredEntries = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLocaleLowerCase();
  return entries.value
    .filter(
      (entry) =>
        filterType.value === "ALL" || entry.entryType === filterType.value,
    )
    .filter(
      (entry) =>
        !normalizedKeyword ||
        entry.title.toLocaleLowerCase().includes(normalizedKeyword) ||
        entry.content.toLocaleLowerCase().includes(normalizedKeyword),
    )
    .slice()
    .sort(
      (left, right) => left.sortOrder - right.sortOrder || left.id - right.id,
    );
});

function resetForm() {
  form.entryType = "SETTING";
  form.title = "";
  form.content = "";
  form.sortOrder = 0;
  editingEntryId.value = null;
}

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    entries.value = await listWorldEntries(projectId.value);
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "无法获取世界观条目，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  if (!canEditEntries.value) {
    ElMessage.warning("当前角色没有编辑世界观的权限");
    return;
  }
  dialogMode.value = "create";
  resetForm();
  dialogVisible.value = true;
}

function openEditDialog(entry: WorldEntrySummary) {
  if (!canEditEntries.value) {
    ElMessage.warning("当前角色没有编辑世界观的权限");
    return;
  }
  dialogMode.value = "edit";
  editingEntryId.value = entry.id;
  form.entryType = entry.entryType;
  form.title = entry.title;
  form.content = entry.content;
  form.sortOrder = entry.sortOrder;
  dialogVisible.value = true;
}

async function saveEntry() {
  if (!canEditEntries.value || saving.value) return;

  const title = form.title.trim();
  const content = form.content.trim();
  if (!title) {
    ElMessage.warning("请输入条目标题");
    return;
  }
  if (!content) {
    ElMessage.warning("请输入条目内容");
    return;
  }
  if (!Number.isInteger(form.sortOrder) || form.sortOrder < 0) {
    ElMessage.warning("排序值必须是大于或等于 0 的整数");
    return;
  }

  saving.value = true;
  try {
    const payload: SaveWorldEntryPayload = {
      entryType: form.entryType,
      title,
      content,
      sortOrder: form.sortOrder,
    };
    if (dialogMode.value === "create") {
      entries.value.push(await createWorldEntry(projectId.value, payload));
      ElMessage.success("世界观条目已创建并保存");
    } else {
      if (editingEntryId.value === null) return;
      const updated = await updateWorldEntry(
        projectId.value,
        editingEntryId.value,
        payload,
      );
      const entryIndex = entries.value.findIndex(
        (entry) => entry.id === updated.id,
      );
      if (entryIndex !== -1) entries.value[entryIndex] = updated;
      ElMessage.success("世界观条目已更新并保存");
    }
    dialogVisible.value = false;
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "保存世界观条目失败"));
  } finally {
    saving.value = false;
  }
}

async function removeEntry(entry: WorldEntrySummary) {
  if (!canEditEntries.value || deletingEntryId.value !== null) return;

  try {
    await ElMessageBox.confirm(
      `确定要删除世界观条目“${entry.title}”吗？`,
      "删除世界观条目",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
  } catch {
    return;
  }

  deletingEntryId.value = entry.id;
  try {
    await deleteWorldEntry(projectId.value, entry.id);
    entries.value = entries.value.filter((item) => item.id !== entry.id);
    ElMessage.success("世界观条目已删除");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "删除世界观条目失败"));
  } finally {
    deletingEntryId.value = null;
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
  <section class="world-page">
    <div class="world-heading">
      <div>
        <p class="eyebrow">WORLD BUILDING</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/open-book.png"
            alt=""
            aria-hidden="true"
          />
          <h1>世界观</h1>
        </div>
        <p class="muted">集中维护故事的设定、地点、阵营、历史与运行规则。</p>
      </div>
      <el-button
        v-if="canEditEntries"
        type="primary"
        size="large"
        :disabled="loading || Boolean(loadError) || isOperating"
        @click="openCreateDialog"
      >
        新建条目
      </el-button>
    </div>

    <div class="filters">
      <el-select v-model="filterType" class="type-filter">
        <el-option label="全部类型" value="ALL" />
        <el-option
          v-for="option in entryTypeOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索标题或内容"
        class="keyword-input"
      />
      <span class="result-count">共 {{ filteredEntries.length }} 条</span>
    </div>

    <el-result
      v-if="loadError"
      icon="error"
      title="世界观条目加载失败"
      :sub-title="loadError"
      class="load-result"
    >
      <template #extra>
        <el-button type="primary" :loading="loading" @click="load">
          重新加载
        </el-button>
      </template>
    </el-result>

    <el-table
      v-else
      v-loading="loading"
      :data="filteredEntries"
      element-loading-text="正在加载世界观条目……"
      class="world-table"
    >
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="entryTypeTagTypes[row.entryType as WorldEntryType]">
            {{ entryTypeLabels[row.entryType as WorldEntryType] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="170" />
      <el-table-column label="内容" min-width="320">
        <template #default="{ row }">
          <p class="content-preview">{{ row.content }}</p>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="更新时间" min-width="180">
        <template #default="{ row }">
          {{ formatUpdatedAt(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="145" align="right">
        <template #default="{ row }">
          <template v-if="canEditEntries">
            <el-button
              type="primary"
              link
              :disabled="isOperating"
              @click="openEditDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              type="danger"
              link
              :loading="deletingEntryId === row.id"
              :disabled="isOperating && deletingEntryId !== row.id"
              @click="removeEntry(row)"
            >
              删除
            </el-button>
          </template>
          <span v-else class="readonly-text">{{ readOnlyReason }}</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty
          :description="
            entries.length
              ? '当前筛选条件下没有世界观条目'
              : '当前项目暂无世界观条目'
          "
        />
      </template>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建世界观条目' : '编辑世界观条目'"
      width="560px"
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
    >
      <el-form label-position="top">
        <el-form-item label="条目类型" required>
          <el-select
            v-model="form.entryType"
            class="full-width"
            :disabled="saving"
          >
            <el-option
              v-for="option in entryTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="条目标题" required>
          <el-input
            v-model="form.title"
            maxlength="100"
            show-word-limit
            placeholder="例如：王都、魔法规则或历史事件"
            :disabled="saving"
          />
        </el-form-item>
        <el-form-item label="条目内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            maxlength="10000"
            show-word-limit
            placeholder="描述这个设定在故事世界中的作用和边界"
            :disabled="saving"
          />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :precision="0"
            :step="10"
            :disabled="saving"
          />
          <span class="sort-tip">数值越小，在列表中的位置越靠前。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="saving" @click="saveEntry">
          {{ dialogMode === "create" ? "确定创建" : "保存修改" }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.world-page {
  max-width: 1180px;
}

.world-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}

.world-page h1 {
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

.preview-role {
  width: 120px;
}

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

.type-filter {
  width: 150px;
}

.keyword-input {
  max-width: 320px;
}

.result-count {
  margin-left: auto;
  color: #7b857f;
  font-size: 13px;
  white-space: nowrap;
}

.world-table,
.load-result {
  margin-top: 16px;
}

.load-result {
  background: #fff;
}

.content-preview {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #5f6963;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.readonly-text {
  color: #909399;
  font-size: 13px;
}

.full-width {
  width: 100%;
}

.sort-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 760px) {
  .world-heading,
  .preview-tools,
  .filters {
    align-items: start;
    flex-direction: column;
  }

  .preview-controls {
    justify-content: flex-start;
  }

  .keyword-input,
  .type-filter {
    width: 100%;
    max-width: none;
  }

  .result-count {
    margin-left: 0;
  }
}
</style>
