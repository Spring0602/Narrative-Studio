<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import UnlockRuleEditor from "./UnlockRuleEditor.vue";
import { getStoryGraph, type StoryNodeSummary } from "@/api/storyGraph";
import type { UnlockRule } from "@/api/rules";
import {
  getChoiceRules,
  listVariables,
  replaceChoiceRules,
  type ConditionInput,
  type EffectInput,
  type RulesInput,
  type StateVariable,
  type VariableType,
} from "@/api/rules";

interface ConditionRow extends ConditionInput {
  clientId: number;
}
interface EffectRow extends EffectInput {
  clientId: number;
}
interface RuleSummary {
  choiceId: number;
  conditionCount: number;
  effectCount: number;
}

const props = defineProps<{
  modelValue: boolean;
  projectId: number;
  nodeId: number | null;
  choiceId: number | null;
  choiceText: string;
  readonly: boolean;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  saved: [summary: RuleSummary];
  loaded: [summary: RuleSummary];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value),
});
const variables = ref<StateVariable[]>([]);
const conditions = ref<ConditionRow[]>([]);
const effects = ref<EffectRow[]>([]);
const unlockRule = ref<UnlockRule | null>(null);
const storyNodes = ref<StoryNodeSummary[]>([]);
const loading = ref(false);
const saving = ref(false);
const loadError = ref("");
let nextClientId = 1;

const integerConditionOperators: ConditionInput["operator"][] = [
  "EQ",
  "NE",
  "GT",
  "GTE",
  "LT",
  "LTE",
];
const simpleConditionOperators: ConditionInput["operator"][] = ["EQ", "NE"];
const integerEffectOperations: EffectInput["operation"][] = [
  "SET",
  "ADD",
  "SUBTRACT",
];
const operatorLabels: Record<ConditionInput["operator"], string> = {
  EQ: "等于（EQ）",
  NE: "不等于（NE）",
  GT: "大于（GT）",
  GTE: "大于等于（GTE）",
  LT: "小于（LT）",
  LTE: "小于等于（LTE）",
};
const operationLabels: Record<EffectInput["operation"], string> = {
  SET: "设置为（SET）",
  ADD: "增加（ADD）",
  SUBTRACT: "减少（SUBTRACT）",
};

function variableFor(variableId: number) {
  return variables.value.find((variable) => variable.id === variableId);
}
function defaultValueFor(type: VariableType) {
  return type === "BOOLEAN" ? "false" : type === "INTEGER" ? "0" : "";
}
function conditionOperators(variableId: number) {
  return variableFor(variableId)?.valueType === "INTEGER"
    ? integerConditionOperators
    : simpleConditionOperators;
}
function effectOperations(variableId: number) {
  return variableFor(variableId)?.valueType === "INTEGER"
    ? integerEffectOperations
    : (["SET"] as EffectInput["operation"][]);
}
function createConditionRow(input?: ConditionInput): ConditionRow {
  const variable = variables.value[0];
  return {
    clientId: nextClientId++,
    variableId: input?.variableId ?? variable?.id ?? 0,
    operator: input?.operator ?? "EQ",
    expectedValue:
      input?.expectedValue ?? defaultValueFor(variable?.valueType ?? "STRING"),
    conditionGroup: input?.conditionGroup ?? 0,
  };
}
function createEffectRow(input?: EffectInput): EffectRow {
  const variable = variables.value[0];
  return {
    clientId: nextClientId++,
    variableId: input?.variableId ?? variable?.id ?? 0,
    operation: input?.operation ?? "SET",
    operandValue:
      input?.operandValue ?? defaultValueFor(variable?.valueType ?? "STRING"),
  };
}

async function loadRules() {
  if (!visible.value || props.choiceId === null || props.nodeId === null)
    return;
  loading.value = true;
  loadError.value = "";
  try {
    const [variableList, rules, graph] = await Promise.all([
      listVariables(props.projectId),
      getChoiceRules(props.projectId, props.nodeId, props.choiceId),
      getStoryGraph(props.projectId),
    ]);
    variables.value = variableList;
    storyNodes.value = graph.nodes;
    unlockRule.value = rules.unlockRule ?? null;
    conditions.value = rules.conditions.map(createConditionRow);
    effects.value = rules.effects.map(createEffectRow);
    emit("loaded", {
      choiceId: rules.choiceId,
      conditionCount: rules.conditions.length,
      effectCount: rules.effects.length,
    });
  } catch (error) {
    loadError.value = apiErrorMessage(error, "规则加载失败，请重试。");
  } finally {
    loading.value = false;
  }
}

watch([() => props.modelValue, () => props.choiceId], ([isVisible]) => {
  if (isVisible) void loadRules();
});

function addCondition() {
  if (props.readonly) return;
  if (!variables.value.length)
    return void ElMessage.warning("请先创建状态变量");
  if (conditions.value.length >= 100)
    return void ElMessage.warning("每个选择最多 100 条条件");
  conditions.value.push(createConditionRow());
}
function addEffect() {
  if (props.readonly) return;
  if (!variables.value.length)
    return void ElMessage.warning("请先创建状态变量");
  if (effects.value.length >= 100)
    return void ElMessage.warning("每个选择最多 100 条效果");
  effects.value.push(createEffectRow());
}
function removeCondition(clientId: number) {
  if (!props.readonly)
    conditions.value = conditions.value.filter(
      (row) => row.clientId !== clientId,
    );
}
function removeEffect(clientId: number) {
  if (!props.readonly)
    effects.value = effects.value.filter((row) => row.clientId !== clientId);
}
function onConditionVariableChange(row: ConditionRow) {
  const variable = variableFor(row.variableId);
  if (!variable) return;
  if (!conditionOperators(row.variableId).includes(row.operator))
    row.operator = "EQ";
  row.expectedValue = defaultValueFor(variable.valueType);
}
function onEffectVariableChange(row: EffectRow) {
  const variable = variableFor(row.variableId);
  if (!variable) return;
  if (!effectOperations(row.variableId).includes(row.operation))
    row.operation = "SET";
  row.operandValue = defaultValueFor(variable.valueType);
}
function moveEffect(index: number, direction: -1 | 1) {
  if (props.readonly) return;
  const targetIndex = index + direction;
  if (targetIndex < 0 || targetIndex >= effects.value.length) return;
  const next = [...effects.value];
  [next[index], next[targetIndex]] = [next[targetIndex]!, next[index]!];
  effects.value = next;
}
function validateTypedValue(type: VariableType, value: string) {
  if (value.length > 500) return "值不能超过 500 个字符";
  if (type === "BOOLEAN" && value !== "true" && value !== "false")
    return "BOOLEAN 只接受小写 true 或 false";
  if (type === "INTEGER") {
    if (!/^-?(0|[1-9][0-9]*)$/.test(value))
      return "INTEGER 必须是无空格、无小数、无非法前导零的十进制整数";
    try {
      const parsed = BigInt(value);
      if (parsed < -(2n ** 63n) || parsed > 2n ** 63n - 1n)
        return "INTEGER 超出 64 位有符号整数范围";
    } catch {
      return "INTEGER 必须是十进制整数";
    }
  }
  return "";
}
function validateRules() {
  for (const [index, condition] of conditions.value.entries()) {
    const variable = variableFor(condition.variableId);
    if (!variable) return `第 ${index + 1} 条条件引用的变量不存在`;
    if (!conditionOperators(condition.variableId).includes(condition.operator))
      return `第 ${index + 1} 条条件的操作符与变量类型不匹配`;
    if (
      !Number.isInteger(condition.conditionGroup) ||
      condition.conditionGroup < 0 ||
      condition.conditionGroup > 100
    )
      return `第 ${index + 1} 条条件的组号必须是 0 至 100 的整数`;
    const error = validateTypedValue(
      variable.valueType,
      condition.expectedValue,
    );
    if (error) return `第 ${index + 1} 条条件：${error}`;
  }
  for (const [index, effect] of effects.value.entries()) {
    const variable = variableFor(effect.variableId);
    if (!variable) return `第 ${index + 1} 条效果引用的变量不存在`;
    if (!effectOperations(effect.variableId).includes(effect.operation))
      return `第 ${index + 1} 条效果的操作符与变量类型不匹配`;
    const error = validateTypedValue(variable.valueType, effect.operandValue);
    if (error) return `第 ${index + 1} 条效果：${error}`;
  }
  return "";
}
function rulesInput(): RulesInput {
  return {
    unlockRule: unlockRule.value,
    conditions: conditions.value.map(
      ({ clientId: _clientId, ...condition }) => condition,
    ),
    effects: effects.value.map(({ clientId: _clientId, ...effect }) => effect),
  };
}
async function saveRules() {
  if (
    props.readonly ||
    saving.value ||
    props.choiceId === null ||
    props.nodeId === null
  )
    return;
  const validationMessage = validateRules();
  if (validationMessage) return void ElMessage.warning(validationMessage);
  saving.value = true;
  try {
    const rules = await replaceChoiceRules(
      props.projectId,
      props.nodeId,
      props.choiceId,
      rulesInput(),
    );
    emit("saved", {
      choiceId: rules.choiceId,
      conditionCount: rules.conditions.length,
      effectCount: rules.effects.length,
    });
    ElMessage.success("选择规则已保存");
    visible.value = false;
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "规则保存失败"));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="readonly ? '查看选择规则' : '编辑选择规则'"
    width="920px"
    top="5vh"
    :close-on-click-modal="!saving"
  >
    <div class="rule-context">
      <div>
        <span>当前选择</span
        ><strong>{{ choiceText || `选择 #${choiceId}` }}</strong>
      </div>
      <div>
        <span>接口资源</span
        ><code>节点 #{{ nodeId }} / 选择 #{{ choiceId }}</code>
      </div>
    </div>
    <el-alert
      v-if="readonly"
      title="当前为只读模式"
      description="TESTER 或归档项目可以查看规则，但不能修改。"
      type="warning"
      show-icon
      :closable="false"
      class="rule-alert"
    />
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="rule-alert"
    >
      <template #default
        ><el-button size="small" @click="loadRules"
          >重新加载</el-button
        ></template
      >
    </el-alert>
    <div v-loading="loading" class="rule-editor-body">
      <template v-if="!loadError && !loading">
        <section class="rule-section">
          <h3>跨路线与复杂解锁条件</h3>
          <p>与下方基础变量条件同时满足才可选。通关/访问记录属于当前玩家和当前版本，重开保留；最多8层128项。</p>
          <el-button v-if="!unlockRule && !readonly" @click="unlockRule={type:'ALL',children:[{type:'ENDING',nodeKey:storyNodes.find(n=>n.nodeType==='ENDING')?.nodeKey??''}]}">添加解锁规则</el-button>
          <UnlockRuleEditor v-if="unlockRule" v-model="unlockRule" :nodes="storyNodes" :variables="variables" :readonly="readonly" />
          <el-button v-if="unlockRule && !readonly" type="danger" link @click="unlockRule=null">移除附加解锁规则</el-button>
          <p v-if="!unlockRule">未配置附加解锁规则。</p>
        </section>
        <section class="rule-section">
          <div class="section-heading">
            <div>
              <h3>选择条件</h3>
              <p>同组 AND、不同组 OR；没有条件表示始终可选。</p>
            </div>
            <el-button
              type="primary"
              plain
              :disabled="readonly || conditions.length >= 100"
              @click="addCondition"
              >添加条件</el-button
            >
          </div>
          <el-empty
            v-if="!conditions.length"
            description="当前没有条件"
            :image-size="58"
          />
          <div v-else class="condition-list">
            <article v-for="(row, index) in conditions" :key="row.clientId">
              <div class="row-number">{{ index + 1 }}</div>
              <label
                ><span>条件组</span
                ><el-input-number
                  v-model="row.conditionGroup"
                  :min="0"
                  :max="100"
                  step-strictly
                  :disabled="readonly"
              /></label>
              <label
                ><span>状态变量</span
                ><el-select
                  v-model="row.variableId"
                  :disabled="readonly"
                  @change="onConditionVariableChange(row)"
                  ><el-option
                    v-for="variable in variables"
                    :key="variable.id"
                    :label="`${variable.displayName}（${variable.valueType}）`"
                    :value="variable.id" /></el-select
              ></label>
              <label
                ><span>比较操作</span
                ><el-select v-model="row.operator" :disabled="readonly"
                  ><el-option
                    v-for="operator in conditionOperators(row.variableId)"
                    :key="operator"
                    :label="operatorLabels[operator]"
                    :value="operator" /></el-select
              ></label>
              <label
                ><span>期望值</span
                ><el-select
                  v-if="variableFor(row.variableId)?.valueType === 'BOOLEAN'"
                  v-model="row.expectedValue"
                  :disabled="readonly"
                  ><el-option label="false" value="false" /><el-option
                    label="true"
                    value="true" /></el-select
                ><el-input
                  v-else
                  v-model="row.expectedValue"
                  maxlength="500"
                  :disabled="readonly"
              /></label>
              <el-button
                type="danger"
                link
                :disabled="readonly"
                @click="removeCondition(row.clientId)"
                >删除</el-button
              >
            </article>
          </div>
        </section>
        <section class="rule-section effect-section">
          <div class="section-heading">
            <div>
              <h3>状态效果</h3>
              <p>按列表顺序执行；没有效果表示状态不变化。</p>
            </div>
            <el-button
              type="primary"
              plain
              :disabled="readonly || effects.length >= 100"
              @click="addEffect"
              >添加效果</el-button
            >
          </div>
          <el-empty
            v-if="!effects.length"
            description="当前没有效果"
            :image-size="58"
          />
          <div v-else class="effect-list">
            <article v-for="(row, index) in effects" :key="row.clientId">
              <div class="row-number">{{ index + 1 }}</div>
              <label
                ><span>状态变量</span
                ><el-select
                  v-model="row.variableId"
                  :disabled="readonly"
                  @change="onEffectVariableChange(row)"
                  ><el-option
                    v-for="variable in variables"
                    :key="variable.id"
                    :label="`${variable.displayName}（${variable.valueType}）`"
                    :value="variable.id" /></el-select
              ></label>
              <label
                ><span>执行操作</span
                ><el-select v-model="row.operation" :disabled="readonly"
                  ><el-option
                    v-for="operation in effectOperations(row.variableId)"
                    :key="operation"
                    :label="operationLabels[operation]"
                    :value="operation" /></el-select
              ></label>
              <label
                ><span>操作值</span
                ><el-select
                  v-if="variableFor(row.variableId)?.valueType === 'BOOLEAN'"
                  v-model="row.operandValue"
                  :disabled="readonly"
                  ><el-option label="false" value="false" /><el-option
                    label="true"
                    value="true" /></el-select
                ><el-input
                  v-else
                  v-model="row.operandValue"
                  maxlength="500"
                  :disabled="readonly"
              /></label>
              <div class="effect-actions">
                <el-button
                  link
                  :disabled="readonly || index === 0"
                  @click="moveEffect(index, -1)"
                  >上移</el-button
                ><el-button
                  link
                  :disabled="readonly || index === effects.length - 1"
                  @click="moveEffect(index, 1)"
                  >下移</el-button
                ><el-button
                  type="danger"
                  link
                  :disabled="readonly"
                  @click="removeEffect(row.clientId)"
                  >删除</el-button
                >
              </div>
            </article>
          </div>
        </section>
      </template>
    </div>
    <template #footer
      ><div class="dialog-footer">
        <span
          >{{ conditions.length }} 条条件 · {{ effects.length }} 条效果</span
        >
        <div>
          <el-button :disabled="saving" @click="visible = false">{{
            readonly ? "关闭" : "取消"
          }}</el-button
          ><el-button
            v-if="!readonly"
            type="primary"
            :loading="saving"
            :disabled="Boolean(loadError) || loading"
            @click="saveRules"
            >整体保存规则</el-button
          >
        </div>
      </div></template
    >
  </el-dialog>
</template>

<style scoped>
.rule-context {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #e1e5e2;
  border-radius: 9px;
  background: #f7f8f6;
}
.rule-context span,
.rule-context strong,
.rule-context code {
  display: block;
}
.rule-context span {
  margin-bottom: 5px;
  color: #8a938e;
  font-size: 12px;
}
.rule-context code {
  color: #85563c;
}
.rule-alert {
  margin-top: 14px;
}
.rule-editor-body {
  min-height: 260px;
  max-height: 64vh;
  margin-top: 16px;
  overflow: auto;
}
.rule-section {
  padding: 18px;
  border: 1px solid #e0e4df;
  border-radius: 10px;
}
.effect-section {
  margin-top: 16px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}
.section-heading h3 {
  margin: 0;
  color: #24352d;
}
.section-heading p {
  margin: 6px 0 0;
  color: #7b857f;
  font-size: 12px;
}
.condition-list,
.effect-list {
  margin-top: 16px;
}
.condition-list article,
.effect-list article {
  display: grid;
  align-items: end;
  gap: 10px;
  padding: 13px;
  border: 1px solid #e6e9e6;
  border-radius: 8px;
  background: #fafbf9;
}
.condition-list article {
  grid-template-columns: 30px 100px minmax(150px, 1.2fr) minmax(135px, 1fr) minmax(
      135px,
      1fr
    ) 42px;
}
.effect-list article {
  grid-template-columns: 30px minmax(170px, 1.2fr) minmax(140px, 1fr) minmax(
      140px,
      1fr
    ) 140px;
}
.condition-list article + article,
.effect-list article + article {
  margin-top: 9px;
}
.row-number {
  display: grid;
  width: 26px;
  height: 26px;
  margin-bottom: 4px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #55786a;
  font-size: 12px;
}
label > span {
  display: block;
  margin-bottom: 5px;
  color: #7e8882;
  font-size: 11px;
}
label .el-select,
label .el-input,
label .el-input-number {
  width: 100%;
}
.effect-actions {
  display: flex;
  justify-content: flex-end;
}
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dialog-footer > span {
  color: #7c8680;
  font-size: 12px;
}
@media (max-width: 900px) {
  .condition-list article,
  .effect-list article {
    grid-template-columns: 30px 1fr;
  }
  .condition-list article label,
  .effect-list article label,
  .effect-actions {
    grid-column: 2;
  }
}
@media (max-width: 640px) {
  .rule-context {
    grid-template-columns: 1fr;
  }
  .section-heading,
  .dialog-footer {
    flex-direction: column;
  }
}
</style>
