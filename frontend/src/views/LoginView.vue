<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '@/api/auth'

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const form = reactive({ username: '', password: '', displayName: '' })

async function submit() {
  if (!form.username || !form.password || (mode.value === 'register' && !form.displayName)) {
    ElMessage.warning('请填写完整信息')
    return
  }
  loading.value = true
  try {
    const result = mode.value === 'login'
      ? await login(form.username, form.password)
      : await register(form.username, form.password, form.displayName)
    localStorage.setItem('narrative_token', result.token)
    localStorage.setItem('narrative_user', JSON.stringify(result))
    await router.push('/projects')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.error?.message || '操作失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="brand-panel">
      <p class="eyebrow">NARRATIVE STUDIO</p>
      <h1>让复杂剧情<br />变得可计算、可验证。</h1>
      <p>组织角色、节点与状态，模拟每一次选择造成的后果。</p>
    </section>
    <section class="auth-card">
      <h2>{{ mode === 'login' ? '欢迎回来' : '创建创作者账户' }}</h2>
      <p class="muted">课程版 v0.1 · 基础框架</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item v-if="mode === 'register'" label="显示名称">
          <el-input v-model="form.displayName" maxlength="40" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button class="full" type="primary" :loading="loading" @click="submit">
          {{ mode === 'login' ? '登录' : '注册并进入' }}
        </el-button>
      </el-form>
      <button class="text-button" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '没有账户？立即注册' : '已有账户？返回登录' }}
      </button>
    </section>
  </main>
</template>
