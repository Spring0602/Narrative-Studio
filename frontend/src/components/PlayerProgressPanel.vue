<script setup lang="ts">
import { computed,ref,watch } from "vue";
import { ElMessage,ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import { getPlayerProgress,resetPlayerProgress,overridePlayerProgress,type PlayerProgress } from "@/api/progress";
import { getRelease } from "@/api/releases";
import { getStoryGraph } from "@/api/storyGraph";
import { listVariables,type StateVariable } from "@/api/rules";
const props=defineProps<{projectId:number;releaseId?:number;refreshKey?:string;canEdit:boolean;canRun:boolean}>();
const emit=defineEmits<{changed:[]}>();
const data=ref<PlayerProgress|null>(null),error=ref(""),loading=ref(false),busy=ref(false),editing=ref(false);
const nodes=ref<{nodeKey:string;title:string;nodeType:string}[]>([]),variables=ref<StateVariable[]>([]);
const selected=ref<string[]>([]),values=ref<Record<string,string>>({});
const profileVars=computed(()=>variables.value.filter(v=>v.persistenceScope==="PROFILE"));
let generation=0;
async function load() {
  const gen=++generation;loading.value=true;error.value="";
  try {
    const [progress,definitions]=await Promise.all([
      getPlayerProgress(props.projectId,props.releaseId),
      props.releaseId?getRelease(props.projectId,props.releaseId):Promise.all([getStoryGraph(props.projectId),listVariables(props.projectId)]).then(([g,v])=>({nodes:g.nodes,variables:v})),
    ]);
    if(gen!==generation)return;
    data.value=progress;nodes.value=definitions.nodes;variables.value=definitions.variables;
  } catch(e) {if(gen===generation){error.value=apiErrorMessage(e,"跨局进度加载失败");data.value=null;}}
  finally {if(gen===generation)loading.value=false;}
}
watch(()=>[props.projectId,props.releaseId,props.refreshKey],()=>{editing.value=false;void load();},{immediate:true});
function edit() {
  if(!data.value)return;
  selected.value=[...data.value.progress.completedEndings];
  values.value=Object.fromEntries(profileVars.value.map(v=>[v.variableKey,data.value!.progress.values[v.variableKey]??v.initialValue]));
  editing.value=true;
}
async function reset() {
  if(!data.value)return;
  const target={project:props.projectId,release:props.releaseId,revision:data.value.revision,generation};
  try {await ElMessageBox.confirm("仅清空本人当前所选版本的跨局变量、通关和访问记录；历史会话保留。请先终止此版本所有进行中模拟。","确认清档",{type:"warning"});}
  catch{return;}
  if(target.generation!==generation)return void ElMessage.warning("进度或版本已变化，请重新确认");
  busy.value=true;
  try {
    const updated=await resetPlayerProgress(target.project,target.release,target.revision);
    if(target.generation===generation){data.value=updated;emit("changed");}
    ElMessage.success("所选版本的跨局进度已清空");
  }
  catch(e){ElMessage.error(apiErrorMessage(e,"清档失败"));}finally{busy.value=false;}
}
async function save() {
  if(!data.value)return;
  const target={project:props.projectId,release:props.releaseId,revision:data.value.revision,generation};
  try {await ElMessageBox.confirm("这是测试辅助，将替换本人该版本的进度，不会生成真实通关会话或提升结局覆盖统计。","确认设置测试进度",{type:"warning"});}
  catch{return;}
  if(target.generation!==generation)return void ElMessage.warning("进度或版本已变化，请重新确认");
  busy.value=true;
  try {
    const updated=await overridePlayerProgress(target.project,target.release,target.revision,values.value,selected.value);
    if(target.generation===generation){data.value=updated;editing.value=false;emit("changed");}
    ElMessage.success("所选版本的测试进度已设置");
  }
  catch(e){ElMessage.error(apiErrorMessage(e,"设置失败"));}finally{busy.value=false;}
}
</script>
<template>
  <section class="progress-panel" v-loading="loading">
    <div class="heading"><div><h3>我的跨局进度 · {{releaseId?'发布版本 #'+releaseId:'当前编辑版'}}</h3>
      <p>重开保留通关记录与跨局变量；不同用户、编辑版、发布版本各自独立。这里不是历史步骤快照。</p></div>
      <div><el-button :disabled="busy" @click="load">刷新</el-button><el-button v-if="canEdit" :disabled="!data||busy||loading" @click="edit">设置测试进度</el-button>
        <el-button v-if="canRun" type="danger" plain :disabled="!data||busy||loading" @click="reset">清空本人进度</el-button></div></div>
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <template v-if="data">
      <p>已通关 {{data.progress.completedEndings.length}} 个不同结局 · 已访问 {{data.progress.visitedNodes.length}} 个节点</p>
      <el-tag v-for="key in data.progress.completedEndings" :key="key" class="tag">{{nodes.find(n=>n.nodeKey===key)?.title??key}} [{{key}}]</el-tag>
      <p v-if="!data.progress.completedEndings.length">尚未解锁任何结局。首次读取可继承此版本已有的真实通关记录；主动清档后不会自动恢复。</p>
      <div v-for="variable in profileVars" :key="variable.id">{{variable.displayName}}：{{data.progress.values[variable.variableKey]??variable.initialValue}}</div>
    </template>
    <el-dialog v-model="editing" title="设置本人测试进度（不计入真实通关）" width="600px" :close-on-click-modal="!busy">
      <el-form label-position="top">
        <el-form-item label="已通关结局"><el-select v-model="selected" multiple filterable style="width:100%"><el-option v-for="node in nodes.filter(n=>n.nodeType==='ENDING')" :key="node.nodeKey" :value="node.nodeKey" :label="node.title+' ['+node.nodeKey+']'" /></el-select></el-form-item>
        <el-form-item v-for="variable in profileVars" :key="variable.id" :label="variable.displayName+' ('+variable.valueType+')'"><el-input v-model="values[variable.variableKey]" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="editing=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">确认设置</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.progress-panel{border:1px solid #dce5df;border-radius:12px;background:#fff;padding:18px;margin:18px 0}
.heading{display:flex;justify-content:space-between;gap:16px;flex-wrap:wrap}.heading h3{margin:0}.heading p{font-size:12px;color:#697b70;max-width:650px}
.tag{margin:4px}.heading>div:last-child{display:flex;flex-wrap:wrap;gap:6px}
</style>
