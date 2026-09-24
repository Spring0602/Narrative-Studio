<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref} from "vue";
import {ElMessage,ElMessageBox} from "element-plus";
import {useRouter} from "vue-router";
import {apiErrorMessage} from "@/api/errors";
import {getSaveState,getSaveDetail,saveVersion,saveAutomatic,restoreCopy,type SaveState,type SaveDetail,type SaveSummary} from "@/api/projectSaves";
import {collectDrafts,draftBucket,draftStorageError,stageDraftRecovery,discardDrafts} from "@/composables/editorDrafts";
import {createAutosaveClock} from "@/utils/autosaveClock";
const props=defineProps<{projectId:number;canEdit:boolean;previewMode?:boolean}>();
const emit=defineEmits<{navigate:[module:string]}>();
const router=useRouter();
const state=ref<SaveState>(),detail=ref<SaveDetail>(),busy=ref(false),error=ref(""),status=ref("正在读取保存状态");
const visible=ref(false),localVisible=ref(false),paused=ref(false),label=ref(""),restoreName=ref("");
const bucket=draftBucket(props.projectId);
const localCount=computed(()=>Object.keys(bucket.value).length);
const localJson=computed(()=>JSON.stringify(bucket.value,null,2));
let revision=0,baseline="",alive=true;
async function initialize() {
  if(props.previewMode){status.value="本地演示模式不保存历史";return;}
  try {
    state.value=await getSaveState(props.projectId);
    if(!alive)return;
    revision=state.value.automatic?.revision??0;baseline=state.value.currentHash;
    paused.value=!!state.value.automatic||localCount.value>0;
    status.value=paused.value?"发现恢复草稿，请先检查再启用自动保存":"自动保存已启用：每10分钟检查变化";
  }catch(e){error.value=apiErrorMessage(e,"保存历史加载失败，请确认数据库已升级至22表");}
}
async function refreshList(){state.value=await getSaveState(props.projectId);}
async function manual() {
  if(busy.value||!props.canEdit||props.previewMode)return;
  busy.value=true;error.value="";
  try {
    await saveVersion(props.projectId,collectDrafts(props.projectId),label.value);
    await refreshList();ElMessage.success("版本已保存，项目保留最近5次手动记录");label.value="";
  }catch(e){error.value=apiErrorMessage(e,"保存结果未知；网络异常时先查看历史，避免重复保存挤掉旧记录");}
  finally{busy.value=false;}
}
async function automatic() {
  if(!alive||busy.value||!props.canEdit||paused.value||props.previewMode||!state.value)return;
  busy.value=true;
  try {
    const result=await saveAutomatic(props.projectId,collectDrafts(props.projectId),revision,baseline);
    if(!alive)return;
    if(result.saved){revision=result.saved.revision;state.value.automatic=result.saved;}
    status.value=result.changed?"自动草稿已更新 · "+new Date().toLocaleTimeString():"内容无变化，已跳过本次自动保存";
  }catch(e){error.value=apiErrorMessage(e,"自动保存失败，请检查网络并手动保存");paused.value=true;status.value="自动保存已暂停，请检查恢复记录后重新启用";}
  finally{busy.value=false;}
}
const tick=createAutosaveClock(automatic);
let timer:ReturnType<typeof setInterval>|undefined;
const catchUp=()=>{if(document.visibilityState==="visible")void tick();};
async function resume() {
  try{await ElMessageBox.confirm("下一次有变化时会覆盖本人现有自动草稿，但不会影响5次手动版本。请先检查或恢复需要保留的内容。","启用自动保存",{type:"warning"});}catch{return;}
  try{await refreshList();revision=state.value!.automatic?.revision??0;baseline=state.value!.currentHash;paused.value=false;error.value="";status.value="自动保存已启用：每10分钟检查变化";}
  catch(e){error.value=apiErrorMessage(e,"无法重新启用自动保存");}
}
async function inspect(save:SaveSummary) {
  if(busy.value)return;busy.value=true;error.value="";
  try{detail.value=await getSaveDetail(props.projectId,save.id);restoreName.value=(detail.value.snapshot.project.name+" · 恢复副本").slice(0,100);}
  catch(e){error.value=apiErrorMessage(e,"历史记录读取失败，请刷新列表");}
  finally{busy.value=false;}
}
function recoverLocal() {
  stageDraftRecovery(props.projectId,collectDrafts(props.projectId));
  const first=Object.values(bucket.value)[0];if(first)emit("navigate",first.module);
  localVisible.value=false;ElMessage.info("已恢复到编辑表单，请核对后再点击表单保存；未覆盖正式内容");
}
async function discardLocal() {
  try{await ElMessageBox.confirm("清除本机未提交表单草稿，不会删除服务器历史。确认不再需要这些文字？","清除本机草稿",{type:"warning"});}catch{return;}
  discardDrafts(props.projectId);localVisible.value=false;
}
async function restore() {
  const selected=detail.value;
  if(!selected||busy.value||!restoreName.value.trim())return;
  try{await ElMessageBox.confirm("将恢复为新项目副本，保留原项目、原成员、发布、模拟与跨局进度不变；副本仅包含创作内容及本人可继续编辑的草稿。","确认恢复",{type:"warning"});}catch{return;}
  if(detail.value!==selected)return;
  busy.value=true;error.value="";
  try {
    const result=await restoreCopy(props.projectId,selected.summary,restoreName.value.trim());
    stageDraftRecovery(result.project.id,result.drafts);
    const first=Object.values(result.drafts)[0];
    await router.push({path:"/projects/"+result.project.id,query:{module:first?.module??"story"}});
    ElMessage.success("已恢复为项目副本，原项目完整保留");
  }catch(e){error.value=apiErrorMessage(e,"未收到恢复结果；请先检查项目列表再重试");}
  finally{busy.value=false;}
}
onMounted(()=>{void initialize();timer=setInterval(()=>void tick(),10000);document.addEventListener("visibilitychange",catchUp);window.addEventListener("focus",catchUp);});
onBeforeUnmount(()=>{alive=false;if(timer)clearInterval(timer);document.removeEventListener("visibilitychange",catchUp);window.removeEventListener("focus",catchUp);});
</script>
<template>
  <section class="save-bar">
    <div class="save-actions">
      <el-input v-model="label" placeholder="版本备注（可选）" maxlength="100" style="max-width:240px" :disabled="busy||!canEdit"/>
      <el-button type="primary" :disabled="busy||!canEdit||previewMode" @click="manual">保存版本</el-button>
      <el-button :disabled="busy||previewMode" @click="visible=true; refreshList().catch(e=>error=apiErrorMessage(e,'读取失败'))">历史记录（最近5次）</el-button>
      <el-button v-if="localCount" :disabled="busy" @click="localVisible=true">本机未提交草稿（{{localCount}}）</el-button>
      <el-button v-if="paused&&canEdit" :disabled="busy" @click="resume">检查后启用自动保存</el-button>
    </div>
    <p>{{status}}。自动草稿独立保留1份，手动记录全项目共享；表单“保存”不会新增历史版本。</p>
    <el-alert v-if="error||draftStorageError" :title="error||draftStorageError" type="warning" :closable="false"/>
    <el-dialog v-model="visible" title="项目保存历史" width="min(1000px,95vw)" :close-on-click-modal="false">
      <p>保存的是创作数据及保存者当时的未提交表单。恢复生成副本，不回退成员、模拟、发布和玩家进度。第6次手动保存会淘汰最早一份。</p>
      <el-table :data="state?.manual??[]" max-height="260">
        <el-table-column prop="label" label="手动版本"/><el-table-column prop="savedBy" label="保存者ID" width="110"/>
        <el-table-column prop="savedAt" label="保存时间" width="200"/><el-table-column label="操作" width="100"><template #default="{row}"><el-button link :disabled="busy" @click="inspect(row)">预览恢复</el-button></template></el-table-column>
      </el-table>
      <p v-if="state?.automatic">本人自动草稿 · {{state.automatic.savedAt}} <el-button :disabled="busy" @click="inspect(state.automatic)">预览恢复</el-button></p>
      <section v-if="detail">
        <h3>{{detail.summary.label}} · 修订 {{detail.summary.revision}}</h3>
        <div class="counts"><span v-for="(rows,table) in detail.snapshot.tables" :key="table">{{table}}：{{rows.length}}</span></div>
        <el-collapse><el-collapse-item title="完整快照与未提交表单（只读）"><pre>{{JSON.stringify(detail.snapshot,null,2)}}</pre></el-collapse-item></el-collapse>
        <el-input v-model="restoreName" maxlength="100" placeholder="恢复后的新项目名称" :disabled="busy"/>
        <el-button type="warning" :disabled="busy||!canEdit" @click="restore">恢复为新项目（保留当前项目）</el-button>
      </section>
    </el-dialog>
    <el-dialog v-model="localVisible" title="本机未提交表单恢复" width="min(850px,95vw)">
      <p>本机草稿用于意外退出恢复，不计入服务器手动版本。不会自动覆盖数据库；清理浏览器数据会删除它。</p>
      <pre>{{localJson}}</pre>
      <template #footer><el-button :disabled="!canEdit" @click="discardLocal">确认后清除本机草稿</el-button><el-button type="primary" :disabled="!canEdit" @click="recoverLocal">恢复到编辑表单</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.save-bar{padding:14px 20px;background:#f4f8f5;border-bottom:1px solid #dce5df}.save-actions{display:flex;gap:8px;flex-wrap:wrap}.save-bar p{font-size:12px;color:#53685e}
.counts{display:flex;gap:14px;flex-wrap:wrap;font-size:12px}pre{white-space:pre-wrap;overflow:auto;max-height:360px;font-size:12px;word-break:break-word}
</style>
