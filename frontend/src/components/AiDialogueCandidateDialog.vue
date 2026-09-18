<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import { generateDialogue } from "@/api/extensions";
import { listCharacters, type CharacterSummary } from "@/api/characters";

const props = defineProps<{
  modelValue: boolean;
  projectId: number;
  nodeId: number;
  nodeTitle: string;
}>();
const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  apply: [text: string, mode: "append" | "replace"];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value),
});
const characters = ref<CharacterSummary[]>([]);
const characterId = ref<number | null>(null);
const direction = ref("");
const consent = ref(false);
const candidate = ref("");
const loadingCharacters = ref(false);
const generating = ref(false);
const error = ref("");

async function loadCharacters() {
  loadingCharacters.value = true;
  error.value = "";
  try {
    characters.value = (await listCharacters(props.projectId)).filter(
      (character) => character.status === "ACTIVE",
    );
    if (!characters.value.some((character) => character.id === characterId.value)) {
      characterId.value = characters.value[0]?.id ?? null;
    }
  } catch (cause) {
    error.value = apiErrorMessage(cause, "角色列表加载失败");
  } finally {
    loadingCharacters.value = false;
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    direction.value = "";
    consent.value = false;
    candidate.value = "";
    error.value = "";
    void loadCharacters();
  },
);

async function generate() {
  if (!characterId.value) return void ElMessage.warning("请选择角色");
  if (!direction.value.trim()) return void ElMessage.warning("请输入台词方向");
  if (!consent.value)
    return void ElMessage.warning("请先确认允许发送所选剧情与角色资料");

  generating.value = true;
  error.value = "";
  candidate.value = "";
  try {
    const result = await generateDialogue(props.projectId, {
      nodeId: props.nodeId,
      characterId: characterId.value,
      direction: direction.value.trim(),
      consent: true,
    });
    candidate.value = result.text;
  } catch (cause) {
    error.value = apiErrorMessage(
      cause,
      "台词候选生成失败，请确认后端已经配置并启用 AI 服务",
    );
  } finally {
    generating.value = false;
  }
}

function apply(mode: "append" | "replace") {
  if (!candidate.value.trim()) return;
  emit("apply", candidate.value.trim(), mode);
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="AI 台词候选"
    width="680px"
    :close-on-click-modal="!generating"
    :close-on-press-escape="!generating"
    :show-close="!generating"
  >
    <el-alert
      type="warning"
      show-icon
      :closable="false"
      title="候选内容不会自动保存"
    >
      仅会向项目配置的 AI 服务发送“{{ nodeTitle }}”节点和所选角色的相关资料；请人工核对后再插入正文。
    </el-alert>
    <el-form label-position="top" v-loading="loadingCharacters" class="candidate-form">
      <el-form-item label="角色" required>
        <el-select v-model="characterId" filterable placeholder="选择说话角色">
          <el-option
            v-for="character in characters"
            :key="character.id"
            :label="character.name"
            :value="character.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="台词方向" required>
        <el-input
          v-model="direction"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="例如：语气克制，暗示角色已经发现线索，但不要直接说出真相。"
        />
      </el-form-item>
      <el-checkbox v-model="consent">
        我确认允许向已配置的 AI 服务发送所选剧情与角色资料
      </el-checkbox>
      <el-button
        type="primary"
        :loading="generating"
        :disabled="loadingCharacters || !characters.length"
        @click="generate"
      >生成候选</el-button>
    </el-form>
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <el-empty
      v-if="!candidate && !generating"
      description="生成结果会显示在这里"
      :image-size="70"
    />
    <el-input
      v-else
      v-model="candidate"
      type="textarea"
      :rows="8"
      maxlength="10000"
      show-word-limit
      aria-label="AI 台词候选结果"
    />
    <template #footer>
      <el-button :disabled="generating" @click="visible = false">关闭</el-button>
      <el-button :disabled="!candidate.trim() || generating" @click="apply('replace')">
        替换正文
      </el-button>
      <el-button
        type="primary"
        :disabled="!candidate.trim() || generating"
        @click="apply('append')"
      >追加到正文</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.candidate-form {
  display: grid;
  gap: 12px;
  margin: 18px 0;
}
.candidate-form .el-select {
  width: 100%;
}
</style>
