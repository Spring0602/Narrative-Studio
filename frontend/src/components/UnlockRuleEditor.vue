<script setup lang="ts">
import { computed } from "vue";
import type { UnlockRule, StateVariable } from "@/api/rules";
interface NodeOption { nodeKey:string; title:string; nodeType:string }
const props=withDefaults(defineProps<{modelValue:UnlockRule;nodes:NodeOption[];variables:StateVariable[];readonly?:boolean;depth?:number}>(),{depth:0});
const emit=defineEmits<{"update:modelValue":[value:UnlockRule]}>();
const composite=computed(()=>["ALL","ANY","NOT","AT_LEAST"].includes(props.modelValue.type));
const selectedVariable=computed(()=>props.variables.find(v=>v.variableKey===props.modelValue.variableKey));
const operators=computed(()=>selectedVariable.value?.valueType==="INTEGER"?["EQ","NE","GT","GTE","LT","LTE"]:["EQ","NE"]);
function patch(value:Partial<UnlockRule>) {emit("update:modelValue",{...props.modelValue,...value});}
function leaf():UnlockRule {return {type:"ENDING",nodeKey:props.nodes.find(n=>n.nodeType==="ENDING")?.nodeKey??""};}
function changeType(type:UnlockRule["type"]) {
  if(["ALL","ANY","NOT","AT_LEAST"].includes(type)) emit("update:modelValue",{type,children:[leaf()],...(type==="AT_LEAST"?{count:1}:{})});
  else if(type==="VARIABLE") emit("update:modelValue",{type,variableKey:props.variables[0]?.variableKey??"",operator:"EQ",value:props.variables[0]?.initialValue??""});
  else emit("update:modelValue",{type,nodeKey:(type==="ENDING"?props.nodes.find(n=>n.nodeType==="ENDING"):props.nodes[0])?.nodeKey??""});
}
function updateChild(index:number,value:UnlockRule) {const children=[...(props.modelValue.children??[])];children[index]=value;patch({children});}
function add() {patch({children:[...(props.modelValue.children??[]),leaf()]});}
function remove(index:number) {const children=(props.modelValue.children??[]).filter((_,i)=>i!==index);patch({children,count:Math.min(props.modelValue.count??1,children.length)});}
</script>
<template>
  <div class="unlock-rule">
    <div class="rule-line">
      <el-select :model-value="modelValue.type" :disabled="readonly" aria-label="逻辑类型" @change="changeType">
        <el-option v-for="option in [{value:'ALL',label:'全部满足 AND'},{value:'ANY',label:'任一满足 OR'},{value:'NOT',label:'取反 NOT'},{value:'AT_LEAST',label:'至少满足 N 项'},{value:'ENDING',label:'历次已通关结局'},{value:'VISITED',label:'历次已访问节点'},{value:'VARIABLE',label:'变量比较'}]"
          :key="option.value" :value="option.value" :label="option.label" :disabled="depth>=7 && ['ALL','ANY','NOT','AT_LEAST'].includes(option.value)" />
      </el-select>
      <el-input-number v-if="modelValue.type==='AT_LEAST'" :model-value="modelValue.count??1" :min="1" :max="modelValue.children?.length??1" :disabled="readonly" aria-label="至少满足数量" @update:model-value="patch({count:$event??1})" />
      <el-select v-if="['ENDING','VISITED'].includes(modelValue.type)" :model-value="modelValue.nodeKey" :disabled="readonly" filterable aria-label="前置剧情节点" @change="patch({nodeKey:$event})">
        <el-option v-for="node in nodes.filter(n=>modelValue.type!=='ENDING'||n.nodeType==='ENDING')" :key="node.nodeKey" :value="node.nodeKey" :label="node.title+' ['+node.nodeKey+']'" />
      </el-select>
      <template v-if="modelValue.type==='VARIABLE'">
        <el-select :model-value="modelValue.variableKey" :disabled="readonly" aria-label="变量" @change="patch({variableKey:$event,operator:'EQ',value:variables.find(v=>v.variableKey===$event)?.initialValue??''})">
          <el-option v-for="variable in variables" :key="variable.id" :value="variable.variableKey" :label="variable.displayName+'（'+(variable.persistenceScope==='PROFILE'?'跨局':'本局')+'）'" />
        </el-select>
        <el-select :model-value="modelValue.operator" :disabled="readonly" aria-label="比较操作" @change="patch({operator:$event})"><el-option v-for="op in operators" :key="op" :value="op" /></el-select>
        <el-input :model-value="modelValue.value" :disabled="readonly" maxlength="500" aria-label="期望值" placeholder="布尔值填 true / false" @update:model-value="patch({value:$event})" />
      </template>
    </div>
    <template v-if="composite">
      <div v-for="(child,index) in modelValue.children" :key="index" class="child-rule">
        <UnlockRuleEditor :model-value="child" :nodes="nodes" :variables="variables" :readonly="readonly" :depth="depth+1" @update:model-value="updateChild(index,$event)" />
        <el-button v-if="!readonly && (modelValue.children?.length??0)>1" type="danger" link @click="remove(index)">移除此条件</el-button>
      </div>
      <el-button v-if="!readonly && modelValue.type!=='NOT'" :disabled="(modelValue.children?.length??0)>=32" @click="add">添加子条件</el-button>
    </template>
  </div>
</template>
<style scoped>
.unlock-rule{border:1px solid #d7e2db;border-radius:8px;padding:12px;background:#fafcf9;min-width:0}
.rule-line{display:flex;gap:8px;flex-wrap:wrap}.rule-line>.el-select,.rule-line>.el-input{width:210px;max-width:100%}
.child-rule{margin:12px 0 12px 14px}.child-rule .unlock-rule{background:white}
</style>
