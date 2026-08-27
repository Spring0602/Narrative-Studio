<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()
const modules = ['项目概览', '世界观', '角色档案', '剧情节点', '状态变量', '模拟测试', '问题检测']
</script>

<template>
  <div class="workspace">
    <aside>
      <button class="back" @click="router.push('/projects')">← 返回项目</button>
      <div class="workspace-title">项目 #{{ route.params.id }}</div>
      <nav><button v-for="(item, index) in modules" :key="item" :class="{ active: index === 0 }">{{ item }}<small v-if="index > 0">待开发</small></button></nav>
    </aside>
    <main>
      <p class="eyebrow">WORKSPACE</p><h1>项目工作台</h1>
      <div class="notice"><strong>基础框架已经接通。</strong><p>项目 CRUD、鉴权、数据库表与前端路由已经就位。接下来按 docs/阶段任务计划.md 依次实现世界观、角色、节点、状态与测试模块。</p></div>
      <div class="module-preview"><article v-for="item in modules.slice(1)" :key="item"><span>○</span><h3>{{ item }}</h3><p>已预留模块入口与数据库结构。</p></article></div>
    </main>
  </div>
</template>
