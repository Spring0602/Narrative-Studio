<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { EditableMemberRole, MemberSummary } from '@/api/members'

// TODO(B): 后端联调后删除 mock 数据，改用 listMembers() 获取真实成员
const members = ref<MemberSummary[]>([
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
])

const dialogVisible = ref(false)
const form = reactive<{ username: string; memberRole: EditableMemberRole }>({
  username: '',
  memberRole: 'EDITOR',
})
let nextMemberId = 3

function openAddDialog() {
  form.username = ''
  form.memberRole = 'EDITOR'
  dialogVisible.value = true
}

function addMockMember() {
  const username = form.username.trim()
  if (!username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (members.value.some((member) => member.username === username)) {
    ElMessage.warning('该用户已在成员列表中')
    return
  }

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
}

function updateMockMemberRole(member: MemberSummary) {
  if (member.memberRole === 'OWNER') {
    ElMessage.warning('不能修改项目创建者的角色')
    return
  }

  ElMessage.success(
    `${member.username} 的角色已修改为 ${member.memberRole}（mock 数据）`,
  )
}

async function removeMockMember(member: MemberSummary) {
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

    members.value = members.value.filter((item) => item.id !== member.id)
    ElMessage.success('成员已移除（mock 数据）')
  } catch {
    // 用户取消或关闭确认窗口时，不执行移除操作。
  }
}
</script>

<template>
  <section class="member-page">
    <div class="member-heading">
      <div>
        <p class="eyebrow">MEMBERS</p>
        <h1>项目成员</h1>
        <p class="muted">查看和管理参与当前剧情项目的用户。</p>
      </div>
      <el-button type="primary" size="large" @click="openAddDialog">添加成员</el-button>
    </div>

    <el-table :data="members" class="member-table">
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="displayName" label="显示名称" />
      <el-table-column label="项目角色" width="220">
        <template #default="{ row }">
          <el-tag v-if="row.memberRole === 'OWNER'" type="warning">
            OWNER
          </el-tag>

          <el-select
            v-else
            v-model="row.memberRole"
            size="small"
            class="role-select"
            @change="updateMockMemberRole(row)"
          >
            <el-option label="编辑者（EDITOR）" value="EDITOR" />
            <el-option label="测试者（TESTER）" value="TESTER" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="joinedAt" label="加入时间" />
      <el-table-column label="操作" width="120" align="right">
        <template #default="{ row }">
          <span v-if="row.memberRole === 'OWNER'" class="protected-text">
            不可移除
          </span>

          <el-button
            v-else
            type="danger"
            link
            @click="removeMockMember(row)"
          >
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="添加项目成员" width="460px">
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input
            v-model="form.username"
            maxlength="32"
            placeholder="请输入已注册用户的用户名"
            @keyup.enter="addMockMember"
          />
        </el-form-item>
        <el-form-item label="项目角色" required>
          <el-select v-model="form.memberRole" class="full-width">
            <el-option label="编辑者（EDITOR）" value="EDITOR" />
            <el-option label="测试者（TESTER）" value="TESTER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addMockMember">确定添加</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.member-page {
  max-width: 1000px;
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
  margin-top: 30px;
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

@media (max-width: 640px) {
  .member-heading {
    align-items: start;
    flex-direction: column;
  }
}
</style>
