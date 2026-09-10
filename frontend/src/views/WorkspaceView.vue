<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MemberManagementView from '@/views/MemberManagementView.vue'

const route = useRoute()
const router = useRouter()
const activeModule = ref('overview')
const modules = [
  { key: 'overview', label: '项目概览', ready: true },
  { key: 'members', label: '成员管理', ready: false },
  { key: 'world', label: '世界观', ready: false },
  { key: 'characters', label: '角色档案', ready: false },
  { key: 'story', label: '剧情节点', ready: false },
  { key: 'variables', label: '状态变量', ready: false },
  { key: 'playtest', label: '模拟测试', ready: false },
  { key: 'issues', label: '问题检测', ready: false },
]
</script>

<template>
  <div class="workspace">
    <aside>
      <button class="back" @click="router.push('/projects')">← 返回项目</button>
      <div class="workspace-title">项目 #{{ route.params.id }}</div>
      <nav>
        <button
          v-for="item in modules"
          :key="item.key"
          :class="{ active: activeModule === item.key }"
          @click="activeModule = item.key"
        >
          {{ item.label }}
          <small v-if="!item.ready">{{ item.key === 'members' ? '待联调' : '待开发' }}</small>
        </button>
      </nav>
    </aside>
    <main>
      <MemberManagementView v-if="activeModule === 'members'" />

      <template v-else-if="activeModule === 'overview'">
        <p class="eyebrow">WORKSPACE</p>
        <h1>项目工作台</h1>
        <div class="notice">
          <strong>基础框架已经接通。</strong>
          <p>项目 CRUD、鉴权、数据库表与前端路由已经就位。接下来按 docs/阶段任务计划.md 依次实现世界观、角色、节点、状态与测试模块。</p>
        </div>
        <div class="module-preview">
          <article v-for="item in modules.filter((item) => !item.ready)" :key="item.key">
            <span>○</span>
            <h3>{{ item.label }}</h3>
            <p>已预留模块入口与数据库结构。</p>
          </article>
        </div>
      </template>

      <section v-else class="module-placeholder">
        <p class="eyebrow">COMING SOON</p>
        <h1>{{ modules.find((item) => item.key === activeModule)?.label }}</h1>
        <p class="muted">该模块入口已经预留，将按阶段任务计划继续开发。</p>
      </section>
    </main>
  </div>
</template>
