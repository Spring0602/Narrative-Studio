<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { getEndingCoverage, type EndingCoverage } from "@/api/extensions";
import { apiErrorMessage } from "@/api/errors";

const props = defineProps<{
  projectId: number;
  releaseId?: number;
  refreshKey?: string;
}>();

const coverage = ref<EndingCoverage | null>(null);
const loading = ref(false);
const error = ref("");
const percentage = computed(() =>
  Math.min(100, Math.max(0, Number(coverage.value?.coveragePercent ?? 0))),
);

async function loadCoverage() {
  loading.value = true;
  error.value = "";
  try {
    coverage.value = await getEndingCoverage(props.projectId, props.releaseId);
  } catch (cause) {
    error.value = apiErrorMessage(cause, "结局覆盖率加载失败");
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.projectId, props.releaseId, props.refreshKey] as const,
  () => void loadCoverage(),
  { immediate: true },
);
</script>

<template>
  <section class="coverage-panel" v-loading="loading">
    <div class="coverage-heading">
      <div>
        <p class="coverage-label">ENDING COVERAGE</p>
        <h2>结局覆盖率</h2>
      </div>
      <el-button link :disabled="loading" @click="loadCoverage">刷新</el-button>
    </div>

    <el-alert
      v-if="error"
      type="error"
      :title="error"
      :closable="false"
      show-icon
    />
    <template v-else-if="coverage">
      <div class="coverage-overview">
        <el-progress
          type="dashboard"
          :percentage="percentage"
          :width="116"
          :stroke-width="10"
          color="#55786a"
        />
        <div class="coverage-summary">
          <strong>
            已触达 {{ coverage.reachedEndings }} / {{ coverage.totalEndings }} 个结局
          </strong>
          <span>
            共 {{ coverage.totalSessions }} 次模拟，其中完成
            {{ coverage.completedSessions }} 次
          </span>
          <div class="session-tags">
            <el-tag type="success">完成 {{ coverage.completedSessions }}</el-tag>
            <el-tag type="warning">进行中 {{ coverage.runningSessions }}</el-tag>
            <el-tag type="info">已终止 {{ coverage.abortedSessions }}</el-tag>
          </div>
        </div>
      </div>

      <el-table
        v-if="coverage.endings.length"
        :data="coverage.endings"
        size="small"
        max-height="260"
      >
        <el-table-column prop="title" label="结局" min-width="180" />
        <el-table-column prop="nodeKey" label="节点标识" min-width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.completions > 0 ? 'success' : 'info'" size="small">
              {{ row.completions > 0 ? "已触达" : "未触达" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="completions" label="完成次数" width="100" />
      </el-table>
      <el-empty v-else description="当前剧情图还没有结局节点" :image-size="64" />
    </template>
  </section>
</template>

<style scoped>
.coverage-panel {
  margin: 18px 0;
  padding: 20px;
  border: 1px solid #dfe5e1;
  border-radius: 14px;
  background: #fff;
}

.coverage-heading,
.coverage-overview,
.session-tags {
  display: flex;
  align-items: center;
}

.coverage-heading {
  justify-content: space-between;
  margin-bottom: 16px;
}

.coverage-heading h2 {
  margin: 2px 0 0;
  font-size: 20px;
}

.coverage-label {
  margin: 0;
  color: #a96034;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.coverage-overview {
  gap: 22px;
  margin-bottom: 16px;
}

.coverage-summary {
  display: grid;
  gap: 9px;
  color: #69736e;
}

.coverage-summary strong {
  color: #1d2b26;
  font-size: 18px;
}

.session-tags {
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 700px) {
  .coverage-overview {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
