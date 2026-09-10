<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  EditableMemberRole,
  MemberRole,
  MemberSummary,
} from '@/api/members'

const route = useRoute()
const isPreviewRoute = computed(() => import.meta.env.DEV && route.path.startsWith('/preview/'))

// TODO(B): 后端联调后删除 mock 数据，改用 listMembers() 获取真实成员
const initialMockMembers: MemberSummary[] = [
  {
    id: 1,
    userId: 1,
    username: 'owner',
    displayName: '项目创建者',
    memberRole: 'OWNER',
    joinedAt: '2026-09-09 10:00',
  },
  {
    id: 2,
    userId: 2,
    username: 'writer01',
    displayName: '剧情编辑',
    memberRole: 'EDITOR',
    joinedAt: '2026-09-09 11:00',
  },
]

function createMockMembers() {
  return initialMockMembers.map((member) => ({ ...member }))
}

const members = ref<MemberSummary[]>(createMockMembers())

// TODO(B): 后端联调后从真实项目数据中读取当前用户角色，并删除开发状态预览工具
const currentUserRole = ref<MemberRole>('OWNER')
const canManageMembers = computed(() => currentUserRole.value === 'OWNER')

const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const operatingMemberId = ref<number | null>(null)
const isOperating = computed(
  () => saving.value || operatingMemberId.value !== null,
)

const dialogVisible = ref(false)
const form = reactive<{ username: string; memberRole: EditableMemberRole }>({
  username: '',
  memberRole: 'EDITOR',
})
let nextMemberId = 3

function wait(milliseconds: number) {
  return new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds))
}

function restoreMockMembers() {
  members.value = createMockMembers()
  nextMemberId = 3
  loading.value = false
  loadError.value = ''
}

function openAddDialog() {
  if (!canManageMembers.value) {
    ElMessage.warning('当前角色没有成员管理权限')
    return
  }
  form.username = ''
  form.memberRole = 'EDITOR'
  dialogVisible.value = true
}

async function addMockMember() {
  if (!canManageMembers.value || saving.value) return

  const username = form.username.trim()
  if (!username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (members.value.some((member) => member.username === username)) {
    ElMessage.warning('该用户已在成员列表中')
    return
  }

  saving.value = true
  try {
    await wait(400)
    members.value.push({
      id: nextMemberId,
      userId: nextMemberId,
      username,
      displayName: '未设置',
      memberRole: form.memberRole,
      joinedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
    })
    nextMemberId += 1
    dialogVisible.value = false
    ElMessage.success('成员已添加（mock 数据）')
  } finally {
    saving.value = false
  }
}

async function updateMockMemberRole(member: MemberSummary) {
  if (!canManageMembers.value || operatingMemberId.value !== null) return
  if (member.memberRole === 'OWNER') {
    ElMessage.warning('不能修改项目创建者的角色')
    return
  }

  operatingMemberId.value = member.id
  try {
    await wait(350)
    ElMessage.success(
      `${member.username} 的角色已修改为 ${member.memberRole}（mock 数据）`,
    )
  } finally {
    operatingMemberId.value = null
  }
}

async function removeMockMember(member: MemberSummary) {
  if (!canManageMembers.value || operatingMemberId.value !== null) return
  if (member.memberRole === 'OWNER') {
    ElMessage.warning('不能移除项目创建者')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要移除成员 ${member.username} 吗？`,
      '移除成员',
      {
        confirmButtonText: '确定移除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  operatingMemberId.value = member.id
  try {
    await wait(400)
    members.value = members.value.filter((item) => item.id !== member.id)
    ElMessage.success('成员已移除（mock 数据）')
  } finally {
    operatingMemberId.value = null
  }
}

async function showLoadingState() {
  loadError.value = ''
  loading.value = true
  await wait(1200)
  loading.value = false
}

function showEmptyState() {
  loading.value = false
  loadError.value = ''
  members.value = []
}

function showErrorState() {
  loading.value = false
  loadError.value = '无法获取项目成员，请检查网络连接后重试。'
}

async function retryMockLoad() {
  loadError.value = ''
  loading.value = true
  await wait(700)
  restoreMockMembers()
  ElMessage.success('成员列表已重新加载（mock 数据）')
}
</script>

<template>
  <el-result
    v-if="!isPreviewRoute"
    icon="info"
    title="成员管理待联调"
    sub-title="成员管理原型仅在开发预览中开放，当前页面不会修改项目成员。"
  />
  <section v-else class="member-page">
    <div class="member-heading">
      <div>
        <p class="eyebrow">MEMBERS</p>
        <h1>项目成员</h1>
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

    <div v-if="isPreviewRoute" class="preview-tools">
      <div>
        <strong>开发状态预览</strong>
        <span>仅用于在后端接入前测试权限和页面状态。</span>
      </div>
      <div class="preview-controls">
        <el-select v-model="currentUserRole" class="preview-role">
          <el-option label="OWNER" value="OWNER" />
          <el-option label="EDITOR" value="EDITOR" />
          <el-option label="TESTER" value="TESTER" />
        </el-select>
        <el-button @click="restoreMockMembers">正常</el-button>
        <el-button @click="showLoadingState">加载中</el-button>
        <el-button @click="showEmptyState">空数据</el-button>
        <el-button type="danger" plain @click="showErrorState">
          加载失败
        </el-button>
      </div>
    </div>

    <el-result
      v-if="loadError"
      icon="error"
      title="成员列表加载失败"
      :sub-title="loadError"
      class="load-result"
    >
      <template #extra>
        <el-button type="primary" :loading="loading" @click="retryMockLoad">
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
            v-model="row.memberRole"
            size="small"
            class="role-select"
            :disabled="isOperating"
            @change="updateMockMemberRole(row)"
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
            {{ row.memberRole === 'OWNER' ? '不可移除' : '无管理权限' }}
          </span>

          <el-button
            v-else
            type="danger"
            link
            :loading="operatingMemberId === row.id"
            :disabled="isOperating && operatingMemberId !== row.id"
            @click="removeMockMember(row)"
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
            @keyup.enter="addMockMember"
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
        <el-button type="primary" :loading="saving" @click="addMockMember">
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
