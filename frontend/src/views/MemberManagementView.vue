<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import {
  addMember,
  listMembers,
  removeMember,
  updateMemberRole,
  type EditableMemberRole,
  type MemberRole,
  type MemberSummary,
} from "@/api/members";

const props = defineProps<{
  currentUserRole: MemberRole;
  projectStatus: "ACTIVE" | "ARCHIVED";
}>();

const route = useRoute();
const projectId = computed(() => Number(route.params.id));
const members = ref<MemberSummary[]>([]);
const canManageMembers = computed(
  () => props.currentUserRole === "OWNER" && props.projectStatus === "ACTIVE",
);
const readOnlyReason = computed(() =>
  props.projectStatus === "ARCHIVED" ? "项目已归档" : "仅项目创建者可管理成员",
);

const loading = ref(false);
const loadError = ref("");
const saving = ref(false);
const operatingMemberId = ref<number | null>(null);
const isOperating = computed(
  () => saving.value || operatingMemberId.value !== null,
);

const dialogVisible = ref(false);
const form = reactive<{ username: string; memberRole: EditableMemberRole }>({
  username: "",
  memberRole: "EDITOR",
});

async function load() {
  if (!Number.isFinite(projectId.value)) {
    loadError.value = "项目编号无效。";
    return;
  }
  loading.value = true;
  loadError.value = "";
  try {
    members.value = await listMembers(projectId.value);
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "无法获取项目成员，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}

function openAddDialog() {
  if (!canManageMembers.value) {
    ElMessage.warning(readOnlyReason.value);
    return;
  }
  form.username = "";
  form.memberRole = "EDITOR";
  dialogVisible.value = true;
}

async function add() {
  if (!canManageMembers.value || saving.value) return;
  const username = form.username.trim();
  if (!username) return void ElMessage.warning("请输入用户名");

  saving.value = true;
  try {
    const created = await addMember(projectId.value, {
      username,
      memberRole: form.memberRole,
    });
    members.value.push(created);
    dialogVisible.value = false;
    ElMessage.success("成员已添加并保存");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "添加成员失败"));
  } finally {
    saving.value = false;
  }
}

async function changeRole(member: MemberSummary, role: EditableMemberRole) {
  if (!canManageMembers.value || operatingMemberId.value !== null) return;
  operatingMemberId.value = member.id;
  try {
    const updated = await updateMemberRole(projectId.value, member.id, {
      memberRole: role,
    });
    const index = members.value.findIndex((item) => item.id === member.id);
    if (index !== -1) members.value[index] = updated;
    ElMessage.success(`${member.username} 的角色已保存`);
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "修改成员角色失败"));
    await load();
  } finally {
    operatingMemberId.value = null;
  }
}

async function remove(member: MemberSummary) {
  if (!canManageMembers.value || operatingMemberId.value !== null) return;
  if (member.memberRole === "OWNER")
    return void ElMessage.warning("不能移除项目创建者");
  try {
    await ElMessageBox.confirm(
      `确定要移除成员 ${member.username} 吗？`,
      "移除成员",
      {
        confirmButtonText: "确定移除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
  } catch {
    return;
  }

  operatingMemberId.value = member.id;
  try {
    await removeMember(projectId.value, member.id);
    members.value = members.value.filter((item) => item.id !== member.id);
    ElMessage.success("成员已移除");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "移除成员失败"));
  } finally {
    operatingMemberId.value = null;
  }
}

onMounted(load);
</script>

<template>
  <section class="member-page">
    <div class="member-heading">
      <div>
        <p class="eyebrow">MEMBERS</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/profile-card.png"
            alt=""
            aria-hidden="true"
          />
          <h1>项目成员</h1>
        </div>
        <p class="muted">查看和管理参与当前剧情项目的用户。</p>
      </div>
      <el-button
        v-if="canManageMembers"
        type="primary"
        size="large"
        :disabled="loading || Boolean(loadError) || isOperating"
        @click="openAddDialog"
      >
        添加成员
      </el-button>
    </div>

    <el-result
      v-if="loadError"
      icon="error"
      title="成员列表加载失败"
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
      :data="members"
      element-loading-text="正在加载项目成员…"
      class="member-table"
    >
      <el-table-column prop="username" label="用户名" min-width="150" />
      <el-table-column prop="displayName" label="显示名称" min-width="150" />
      <el-table-column label="项目角色" width="220">
        <template #default="{ row }">
          <el-tag
            v-if="row.memberRole === 'OWNER' || !canManageMembers"
            :type="row.memberRole === 'OWNER' ? 'warning' : 'info'"
          >
            {{ row.memberRole }}
          </el-tag>

          <el-select
            v-else
            :model-value="row.memberRole"
            size="small"
            class="role-select"
            :disabled="isOperating"
            @change="changeRole(row, $event)"
          >
            <el-option label="编辑者（EDITOR）" value="EDITOR" />
            <el-option label="测试者（TESTER）" value="TESTER" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="joinedAt" label="加入时间" min-width="180" />
      <el-table-column label="操作" width="130" align="right">
        <template #default="{ row }">
          <span
            v-if="row.memberRole === 'OWNER' || !canManageMembers"
            class="protected-text"
          >
            {{ row.memberRole === "OWNER" ? "不可移除" : "无管理权限" }}
          </span>

          <el-button
            v-else
            type="danger"
            link
            :loading="operatingMemberId === row.id"
            :disabled="isOperating && operatingMemberId !== row.id"
            @click="remove(row)"
          >
            移除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="当前项目暂无成员" />
      </template>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      title="添加项目成员"
      width="460px"
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
    >
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input
            v-model="form.username"
            maxlength="32"
            placeholder="请输入已注册用户的用户名"
            :disabled="saving"
            @keyup.enter="add"
          />
        </el-form-item>
        <el-form-item label="项目角色" required>
          <el-select
            v-model="form.memberRole"
            class="full-width"
            :disabled="saving"
          >
            <el-option label="编辑者（EDITOR）" value="EDITOR" />
            <el-option label="测试者（TESTER）" value="TESTER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="saving" @click="add">
          确定添加
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.member-page {
  max-width: 1100px;
}

.member-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}

.member-page h1 {
  margin: 6px 0 8px;
  font-size: 42px;
}

.member-table {
  margin-top: 24px;
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

.load-result {
  margin-top: 24px;
  background: #fff;
}

.full-width {
  width: 100%;
}

.role-select {
  width: 170px;
}

.protected-text {
  color: #909399;
  font-size: 13px;
}

@media (max-width: 760px) {
  .member-heading,
  .preview-tools {
    align-items: start;
    flex-direction: column;
  }

  .preview-controls {
    justify-content: flex-start;
  }
}
</style>
