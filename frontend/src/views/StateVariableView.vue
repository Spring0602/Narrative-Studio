<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import type { MemberRole } from "@/api/members";
import {
  createVariable,
  deleteVariable,
  listVariables,
  updateVariable,
  type StateVariable,
  type VariableInput,
  type VariableType,
} from "@/api/rules";

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
const typeLabels: Record<VariableType, string> = {
  BOOLEAN: "布尔值",
  INTEGER: "整数",
  STRING: "文本",
};
const tagTypes: Record<VariableType, "success" | "warning" | "info"> = {
  BOOLEAN: "success",
  INTEGER: "warning",
  STRING: "info",
};

const variables = ref<StateVariable[]>([]);
const loading = ref(true);
const loadError = ref("");
const saving = ref(false);
const deletingId = ref<number | null>(null);
const mutationNotice = ref("");
const keyword = ref("");
const filterType = ref<"ALL" | VariableType>("ALL");
const filteredVariables = computed(() => {
  const key = keyword.value.trim().toLowerCase();
  return variables.value.filter(
    (variable) =>
      (filterType.value === "ALL" || variable.valueType === filterType.value) &&
      (!key ||
        [
          variable.variableKey,
          variable.displayName,
          variable.description || "",
        ].some((value) => value.toLowerCase().includes(key))),
  );
});
const counts = computed(() => ({
  BOOLEAN: variables.value.filter((item) => item.valueType === "BOOLEAN")
    .length,
  INTEGER: variables.value.filter((item) => item.valueType === "INTEGER")
    .length,
  STRING: variables.value.filter((item) => item.valueType === "STRING").length,
}));

async function loadVariables() {
  loading.value = true;
  loadError.value = "";
  try {
    variables.value = await listVariables(projectId.value);
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "无法加载状态变量，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}

const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const editingId = ref<number | null>(null);
const form = reactive({
  variableKey: "",
  displayName: "",
  valueType: "INTEGER" as VariableType,
  persistenceScope: "SESSION" as "SESSION" | "PROFILE",
  initialValue: "0",
  description: "",
});
function resetForm() {
  Object.assign(form, {
    variableKey: "",
    displayName: "",
    valueType: "INTEGER",
    persistenceScope: "SESSION",
    initialValue: "0",
    description: "",
  });
  editingId.value = null;
}
function ensureWritable() {
  if (canEdit.value) return true;
  ElMessage.warning(readOnlyReason.value);
  return false;
}
function openCreate() {
  if (!ensureWritable()) return;
  if (variables.value.length >= 200)
    return void ElMessage.warning("每个项目最多 200 个状态变量");
  dialogMode.value = "create";
  resetForm();
  dialogVisible.value = true;
}
function openEdit(variable: StateVariable) {
  if (!ensureWritable()) return;
  dialogMode.value = "edit";
  editingId.value = variable.id;
  Object.assign(form, {
    variableKey: variable.variableKey,
    displayName: variable.displayName,
    valueType: variable.valueType,
    persistenceScope: variable.persistenceScope || "SESSION",
    initialValue: variable.initialValue,
    description: variable.description || "",
  });
  dialogVisible.value = true;
}
function onTypeChange(type: VariableType) {
  form.initialValue =
    type === "BOOLEAN" ? "false" : type === "INTEGER" ? "0" : "";
}
function validateInteger(value: string) {
  if (!/^-?(0|[1-9][0-9]*)$/.test(value))
    return "INTEGER 必须是无空格、无小数、无非法前导零的十进制整数";
  try {
    const number = BigInt(value);
    if (number < -(2n ** 63n) || number > 2n ** 63n - 1n)
      return "INTEGER 超出 64 位有符号整数范围";
  } catch {
    return "INTEGER 必须是十进制整数";
  }
  return "";
}
function input(): VariableInput {
  return {
    variableKey: form.variableKey.trim(),
    displayName: form.displayName.trim(),
    valueType: form.valueType,
    persistenceScope: form.persistenceScope,
    initialValue: form.initialValue,
    description: form.description.trim() || null,
  };
}
function validate(payload: VariableInput) {
  if (!payload.variableKey) return "请输入变量标识";
  if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(payload.variableKey))
    return "变量标识必须以字母开头，并且只能包含字母、数字和下划线";
  if (!payload.displayName) return "请输入显示名称";
  if (
    variables.value.some(
      (item) =>
        item.variableKey === payload.variableKey && item.id !== editingId.value,
    )
  )
    return "项目内变量标识已存在";
  if (
    payload.valueType === "BOOLEAN" &&
    !["true", "false"].includes(payload.initialValue)
  )
    return "BOOLEAN 只接受小写 true 或 false";
  return payload.valueType === "INTEGER"
    ? validateInteger(payload.initialValue)
    : "";
}
async function save() {
  if (!ensureWritable() || saving.value) return;
  const payload = input();
  const validation = validate(payload);
  if (validation) return void ElMessage.warning(validation);
  saving.value = true;
  mutationNotice.value = "";
  try {
    if (dialogMode.value === "create")
      await createVariable(projectId.value, payload);
    else await updateVariable(projectId.value, editingId.value!, payload);
    await loadVariables();
    dialogVisible.value = false;
    ElMessage.success(
      dialogMode.value === "create" ? "状态变量已创建" : "状态变量已更新",
    );
  } catch (error) {
    const message = apiErrorMessage(error, "状态变量保存失败");
    if (message.includes("会话") || message.includes("运行"))
      mutationNotice.value = message;
    ElMessage.error(message);
  } finally {
    saving.value = false;
  }
}
async function remove(variable: StateVariable) {
  if (!ensureWritable() || deletingId.value !== null) return;
  try {
    await ElMessageBox.confirm(
      `确定删除状态变量“${variable.displayName}”吗？被规则引用时服务器会拒绝删除。`,
      "删除状态变量",
      { type: "warning" },
    );
  } catch {
    return;
  }
  deletingId.value = variable.id;
  mutationNotice.value = "";
  try {
    await deleteVariable(projectId.value, variable.id);
    await loadVariables();
    ElMessage.success("状态变量已删除");
  } catch (error) {
    const message = apiErrorMessage(error, "状态变量删除失败");
    if (message.includes("会话") || message.includes("运行"))
      mutationNotice.value = message;
    ElMessage.error(message);
  } finally {
    deletingId.value = null;
  }
}

onMounted(loadVariables);
</script>

<template>
  <section class="variable-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">STATE VARIABLES</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/stacked-layers.png"
            alt=""
            aria-hidden="true"
          />
          <h1>状态变量</h1>
        </div>
        <p class="muted">定义剧情模拟的初始状态，供选择条件和效果引用。</p>
      </div>
      <el-button
        v-if="canEdit"
        type="primary"
        size="large"
        :disabled="loading || Boolean(loadError)"
        @click="openCreate"
        >新建变量</el-button
      >
    </div>
    <el-alert
      v-if="!canEdit"
      :title="readOnlyReason"
      type="warning"
      show-icon
      :closable="false"
      class="notice"
    />
    <el-alert
      v-if="mutationNotice"
      :title="mutationNotice"
      description="请先在模拟测试页面终止自己的运行会话；若是其他测试者的会话，请联系对方处理。"
      type="warning"
      show-icon
      :closable="false"
      class="notice"
    />
    <div class="summary">
      <div>
        <strong>{{ variables.length }}</strong
        ><span>变量总数 / 200</span>
      </div>
      <div>
        <strong>{{ counts.BOOLEAN }}</strong
        ><span>BOOLEAN</span>
      </div>
      <div>
        <strong>{{ counts.INTEGER }}</strong
        ><span>INTEGER</span>
      </div>
      <div>
        <strong>{{ counts.STRING }}</strong
        ><span>STRING</span>
      </div>
    </div>
    <div class="filters">
      <el-select v-model="filterType"
        ><el-option label="全部类型" value="ALL" /><el-option
          label="布尔值（BOOLEAN）"
          value="BOOLEAN" /><el-option
          label="整数（INTEGER）"
          value="INTEGER" /><el-option
          label="文本（STRING）"
          value="STRING" /></el-select
      ><el-input
        v-model="keyword"
        clearable
        placeholder="搜索标识、名称或说明"
      /><span>显示 {{ filteredVariables.length }} 条</span>
    </div>
    <el-result
      v-if="loadError"
      icon="error"
      title="状态变量加载失败"
      :sub-title="loadError"
      ><template #extra
        ><el-button type="primary" @click="loadVariables"
          >重新加载</el-button
        ></template
      ></el-result
    >
    <el-table
      v-else
      v-loading="loading"
      :data="filteredVariables"
      class="table"
    >
      <el-table-column prop="displayName" label="显示名称" min-width="150" />
      <el-table-column prop="variableKey" label="变量标识" min-width="170" />
      <el-table-column label="保存范围" width="120"><template #default="{row}">
        <el-tag :type="row.persistenceScope==='PROFILE'?'warning':'info'">{{row.persistenceScope==='PROFILE'?'跨局保存':'仅本局'}}</el-tag>
      </template></el-table-column>
      <el-table-column label="类型" width="125"
        ><template #default="{ row }"
          ><el-tag :type="tagTypes[row.valueType as VariableType]">{{
            typeLabels[row.valueType as VariableType]
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="初始值" min-width="150"
        ><template #default="{ row }"
          ><code>{{
            row.initialValue === "" ? "（空字符串）" : row.initialValue
          }}</code></template
        ></el-table-column
      >
      <el-table-column label="说明" min-width="220"
        ><template #default="{ row }">{{
          row.description || "尚未填写"
        }}</template></el-table-column
      >
      <el-table-column label="操作" width="140" align="right"
        ><template #default="{ row }"
          ><template v-if="canEdit"
            ><el-button link type="primary" @click="openEdit(row)"
              >编辑</el-button
            ><el-button
              link
              type="danger"
              :loading="deletingId === row.id"
              @click="remove(row)"
              >删除</el-button
            ></template
          ><span v-else>只读</span></template
        ></el-table-column
      >
      <template #empty
        ><el-empty
          :description="
            variables.length ? '没有符合筛选条件的变量' : '当前项目暂无状态变量'
          "
      /></template>
    </el-table>
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建状态变量' : '编辑状态变量'"
      width="560px"
      ><el-form label-position="top"
        ><el-form-item label="变量标识" required
          ><el-input
            v-model="form.variableKey"
            maxlength="64"
            placeholder="例如 trust 或 hasKey" /></el-form-item
        ><el-form-item label="显示名称" required
          ><el-input v-model="form.displayName" maxlength="100" /></el-form-item
        ><el-form-item label="变量类型" required
          ><el-select
            v-model="form.valueType"
            class="wide"
            @change="onTypeChange"
            ><el-option label="布尔值（BOOLEAN）" value="BOOLEAN" /><el-option
              label="整数（INTEGER）"
              value="INTEGER" /><el-option
              label="文本（STRING）"
              value="STRING" /></el-select></el-form-item
        ><el-form-item label="保存范围" required>
          <el-select v-model="form.persistenceScope"><el-option value="SESSION" label="仅本局：重开恢复初始值" /><el-option value="PROFILE" label="跨局：每次成功选择后保存" /></el-select>
          <small>通关解锁优先使用“历次已通关结局”；跨局变量不会在终止/重开时清空，清档另行确认。</small>
        </el-form-item><el-form-item label="初始值" required
          ><el-select
            v-if="form.valueType === 'BOOLEAN'"
            v-model="form.initialValue"
            class="wide"
            ><el-option label="false" value="false" /><el-option
              label="true"
              value="true" /></el-select
          ><el-input
            v-else
            v-model="form.initialValue"
            :type="form.valueType === 'STRING' ? 'textarea' : 'text'"
            maxlength="500" /></el-form-item
        ><el-form-item label="说明"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>

<style scoped>
.variable-page {
  max-width: 1180px;
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
  margin-top: 5px;
  color: #7c8781;
  font-size: 12px;
}
.filters {
  display: grid;
  grid-template-columns: 200px minmax(260px, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #e1e5e0;
  border-radius: 10px;
  background: #fff;
}
.filters > span {
  color: #7c8781;
  font-size: 12px;
}
.table {
  margin-top: 14px;
  border-radius: 10px;
}
.wide {
  width: 100%;
}
code {
  color: #81553c;
}
@media (max-width: 760px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }
  .summary {
    grid-template-columns: 1fr 1fr;
  }
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
