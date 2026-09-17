<script setup lang="ts">
import { computed,reactive,ref,watch } from "vue";
import { ElMessage } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import { inspectExcel,previewExcel,importExcel,type ExcelSheet,type ExcelOptions,type ExcelPreview } from "@/api/excelImport";
import { guessColumns,guessHeaderRow,guessBranches } from "@/utils/excelHeaders";
import ExcelColumnMapping from "./ExcelColumnMapping.vue";
import ExcelGraphPreview from "./ExcelGraphPreview.vue";
const props=defineProps<{modelValue:boolean;projectId?:number;existingNodes?:boolean}>();
const emit=defineEmits<{"update:modelValue":[value:boolean];imported:[id:number]}>();
const visible=computed({get:()=>props.modelValue,set:v=>{if(!busy.value)emit("update:modelValue",v);}});
const busy=ref(false),file=ref<File>(),sheets=ref<ExcelSheet[]>([]),error=ref(""),preview=ref<ExcelPreview>();
const acknowledged=ref(false);
const name=ref(""),nodeSheet=ref(""),headerRow=ref(1),choiceSheet=ref(""),choiceHeaderRow=ref(1);
const columns=ref<Record<string,number>>({}),choiceColumns=ref<Record<string,number>>({});
const mode=ref<"wide"|"edges">("wide");
const branches=ref<{targetColumn?:number;textColumn?:number}[]>([]);
const types=reactive<{value:string;kind:"NORMAL"|"ENDING"}[]>([]);
const startNodeKey=ref(""),preserveExtra=ref(true),target=ref<"current"|"new">("new");
const nodeFields=[{key:"nodeKey",label:"节点编号（必填）"},{key:"title",label:"节点标题（必填）"},{key:"content",label:"剧情正文"},{key:"nodeType",label:"节点类型"},{key:"scene",label:"场景 / 章节"},{key:"isStart",label:"是否起点"}];
const edgeFields=[{key:"sourceNodeKey",label:"源节点编号（必填）"},{key:"targetNodeKey",label:"目标节点编号（必填）"},{key:"choiceText",label:"选项文本"}];
const selectedSheet=computed(()=>sheets.value.find(s=>s.name===nodeSheet.value));
const headers=computed(()=>selectedSheet.value?.sampleRows[headerRow.value-1]??[]);
const edgeHeaders=computed(()=>sheets.value.find(s=>s.name===choiceSheet.value)?.sampleRows[choiceHeaderRow.value-1]??[]);
const aiPrompt=`请将我的完整互动剧情整理为Excel，保留所有剧情节点，不补写或猜测缺失剧情。
采用两个工作表：节点表每行一个节点，字段包含节点编号、节点标题、剧情正文、节点类型、场景、是否起点；连线表每行一个选项，字段包含源节点编号、目标节点编号、选项文本。
节点编号采用唯一且稳定的英文字母/数字/下划线/短横线（最多64位），所有跳转必须精确引用编号；不要用标题代替编号。节点类型为普通或结局，全图只有一个普通节点作为起点。结局不含出边；循环、汇合和多条路线均通过连线表达。
可按游戏类型增加角色、路线、章节、前置说明等列。正文含补充字段总计不超过10000字，标题不超过120字，选项文本不超过500字。每次最多500节点、1000连线。
表头放在第一行，不合并单元格，不用公式、宏或嵌入对象。编号按文本保存，避免前导零丢失。条件与效果只写说明，不能假设导入时自动成为执行规则。未明确的跳转请指出并让我确认后再输出，不要猜测。`;
let generation=0;
function invalidate() {generation++;preview.value=undefined;acknowledged.value=false;error.value="";}
watch([name,nodeSheet,headerRow,choiceSheet,choiceHeaderRow,columns,choiceColumns,branches,types,startNodeKey,preserveExtra,mode,target],invalidate,{deep:true,flush:"sync"});
watch(()=>props.modelValue,open=>{
  generation++;preview.value=undefined;acknowledged.value=false;error.value="";
  if(open)target.value=props.projectId!==undefined&&!props.existingNodes?"current":"new";
},{immediate:true,flush:"sync"});
watch(()=>props.projectId,()=>{invalidate();target.value="new";});
watch(nodeSheet,()=>{
  const s=sheets.value.find(s=>s.name===nodeSheet.value);
  headerRow.value=guessHeaderRow(s?.sampleRows??[],nodeFields.map(f=>f.key));
  guessNode();
},{flush:"sync"});
watch(headerRow,guessNode,{flush:"sync"});
watch(choiceSheet,()=>{
  const s=sheets.value.find(s=>s.name===choiceSheet.value);
  choiceHeaderRow.value=guessHeaderRow(s?.sampleRows??[],edgeFields.map(f=>f.key));
  guessEdges();
},{flush:"sync"});
watch(choiceHeaderRow,guessEdges,{flush:"sync"});
function guessNode() {
  columns.value=guessColumns(headers.value,nodeFields.map(f=>f.key));
  branches.value=guessBranches(headers.value,Object.values(columns.value));
}
function guessEdges() {choiceColumns.value=guessColumns(edgeHeaders.value,edgeFields.map(f=>f.key));}
async function chooseFile(event:Event) {
  const input=event.target as HTMLInputElement;
  const selected=input.files?.[0];input.value="";
  if(!selected)return;
  invalidate();file.value=undefined;sheets.value=[];nodeSheet.value="";choiceSheet.value="";types.splice(0);
  if(!/\.(xlsx|xls)$/i.test(selected.name)||selected.size>5*1024*1024) {error.value="请选择不超过5MB的.xlsx或.xls文件";return;}
  busy.value=true;
  const gen=generation;
  try {
    const result=await inspectExcel(selected);
    if(gen!==generation)return;
    file.value=selected;sheets.value=result.sheets;name.value=selected.name.replace(/\.[^.]+$/,"").slice(0,100);
    nodeSheet.value=result.sheets.find(s=>!s.hidden)?.name??result.sheets[0]?.name??"";
    choiceSheet.value=result.sheets.find(s=>s.name!==nodeSheet.value&&/连线|关系|分支|edge|choice/i.test(s.name))?.name??"";
    mode.value=choiceSheet.value?"edges":"wide";
  } catch(e){if(gen===generation)error.value=apiErrorMessage(e,"Excel读取失败");}
  finally{busy.value=false;}
}
function options():ExcelOptions {
  if(!file.value)throw new Error("请先选择Excel文件");
  if(!name.value.trim())throw new Error("请输入导入名称");
  if(columns.value.nodeKey===undefined||columns.value.title===undefined)throw new Error("请映射节点编号和标题");
  if(mode.value==="edges"&&(!choiceSheet.value||choiceColumns.value.sourceNodeKey===undefined||choiceColumns.value.targetNodeKey===undefined))
    throw new Error("请选择连线表并映射源节点和目标节点");
  if(mode.value==="wide"&&branches.value.some(b=>typeof b.targetColumn!=="number"))throw new Error("请为每组分支选择目标列，或删除空分支组");
  const typeMappings:Record<string,"NORMAL"|"ENDING">=Object.create(null);
  for(const row of types) {
    const key=row.value.trim();
    if(!key||Object.prototype.hasOwnProperty.call(typeMappings,key))throw new Error("自定义节点类型不能为空或重复");
    typeMappings[key]=row.kind;
  }
  return {name:name.value.trim(),mapping:{
    nodeSheet:nodeSheet.value,headerRow:headerRow.value,columns:columns.value,
    choiceSheet:mode.value==="edges"?choiceSheet.value:undefined,choiceHeaderRow:choiceHeaderRow.value,
    choiceColumns:mode.value==="edges"?choiceColumns.value:{},
    branches:mode.value==="wide"?branches.value.map(b=>({targetColumn:b.targetColumn!,...(typeof b.textColumn==="number"?{textColumn:b.textColumn}:{})})):[],
    typeMappings,startNodeKey:startNodeKey.value.trim()||undefined,preserveExtra:preserveExtra.value,
  }};
}
async function generate() {
  let data:ExcelOptions;
  try{data=options();}catch(e){error.value=(e as Error).message;return;}
  invalidate();const gen=generation;busy.value=true;
  try {const result=await previewExcel(file.value!,data);if(gen===generation)preview.value=result;}
  catch(e){if(gen===generation)error.value=apiErrorMessage(e,"预览失败");}
  finally{busy.value=false;}
}
async function commit() {
  if(!preview.value||!acknowledged.value||busy.value)return;
  let data:ExcelOptions;
  try{data=options();}catch(e){error.value=(e as Error).message;return;}
  const destination=target.value==="current"?props.projectId:undefined;
  data.expectedDigest=preview.value.digest;data.confirm=true;
  busy.value=true;error.value="";
  try {
    const project=await importExcel(file.value!,data,destination);
    emit("update:modelValue",false);emit("imported",project.id);preview.value=undefined;
    ElMessage.success("剧情节点与连线已生成");
  }catch(e){error.value=apiErrorMessage(e,"未收到导入结果。若网络超时，请先刷新项目列表或剧情图确认是否已成功，再决定是否重试。");}
  finally{busy.value=false;}
}
async function copyPrompt() {
  try{await navigator.clipboard.writeText(aiPrompt);ElMessage.success("整理提示词已复制，请粘贴到你选择的AI工具");}
  catch{ElMessage.warning("无法访问剪贴板，请手动复制下方提示词");}
}
</script>
<template>
  <el-dialog v-model="visible" title="Excel 一键生成剧情关系图" width="min(1100px, 95vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" destroy-on-close>
    <el-alert type="info" :closable="false" show-icon title="这个 Excel 用于整理全部剧情节点和跳转关系，推荐先用 AI 整理 Excel，再人工核对后导入。">
      当前未接入 AI 生成 Excel。文件仅发送到本项目后端解析，不发送给 AI；不修改原文件，不覆盖已有剧情。
    </el-alert>
    <el-collapse class="help">
      <el-collapse-item title="表格整理规范 / 可复制的 AI 提示词" name="help">
        <p>支持 .xlsx / .xls；最多5MB、10张表、每表64列、500节点及1000连线。表头可在前30行，常见中英文表头自动识别，其他表头手动映射。请取消数据区合并单元格，并将公式粘贴为值。</p>
        <p>单表：每行一个节点，可添加多组“选项文本＋目标节点编号”列；双表：一张表列出所有节点，另一张每行一条连线。终点也必须列入节点表。编号建议按文本保存，跳转单元格一次只写一个目标编号。</p>
        <el-button @click="copyPrompt">复制 AI 整理提示词</el-button>
        <el-input :model-value="aiPrompt" type="textarea" :rows="7" readonly aria-label="AI整理提示词" />
      </el-collapse-item>
    </el-collapse>
    <div class="upload"><label>选择 Excel 文件 <input type="file" accept=".xlsx,.xls" :disabled="busy" @change="chooseFile"></label><span>{{file?.name}}</span></div>
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" class="error" />
    <el-form v-if="sheets.length" label-position="top" :disabled="busy">
      <div class="grid">
        <el-form-item label="导入名称（新项目时作为项目名）"><el-input v-model="name" maxlength="100" /></el-form-item>
        <el-form-item label="导入位置"><el-select v-model="target">
          <el-option label="新建项目（不影响已有项目）" value="new" />
          <el-option v-if="projectId!==undefined" label="当前项目的空白剧情图" value="current" :disabled="existingNodes" />
        </el-select></el-form-item>
        <el-form-item label="节点工作表"><el-select v-model="nodeSheet"><el-option v-for="sheet in sheets" :key="sheet.name" :label="sheet.name+(sheet.hidden?'（隐藏）':'')" :value="sheet.name" /></el-select></el-form-item>
        <el-form-item label="节点表头行（从1开始）"><el-input-number v-model="headerRow" :min="1" :max="30" :precision="0" /></el-form-item>
      </div>
      <p>匹配不准时请直接修改映射，无需为了固定表头重做表格。</p>
      <ExcelColumnMapping v-model="columns" :headers="headers" :fields="nodeFields" :disabled="busy" />
      <div class="grid">
        <el-form-item label="起点编号（可选，留空使用表中标记；无标记时使用首个节点）"><el-input v-model="startNodeKey" maxlength="64" placeholder="例如 start" /></el-form-item>
        <el-form-item label="游戏专属字段"><el-checkbox v-model="preserveExtra">把未映射节点列保留在节点正文的补充说明中</el-checkbox></el-form-item>
      </div>
      <h3>节点类型映射</h3>
      <p>普通/结局等常见值自动识别。自定义类型可在这里映射，例如“战斗”→普通，“真结局”→结局。不会推测未知类型。</p>
      <div v-for="(row,i) in types" :key="i" class="inline">
        <el-input v-model="row.value" maxlength="120" placeholder="Excel中的类型原文" />
        <el-select v-model="row.kind"><el-option label="普通节点" value="NORMAL"/><el-option label="结局节点" value="ENDING"/></el-select>
        <el-button @click="types.splice(i,1)">删除</el-button>
      </div>
      <el-button @click="types.push({value:'',kind:'NORMAL'})">添加自定义类型</el-button>
      <h3>分支关系来源</h3>
      <el-radio-group v-model="mode"><el-radio value="wide">节点表内的选项 / 目标列</el-radio><el-radio value="edges">独立连线表</el-radio></el-radio-group>
      <template v-if="mode==='wide'">
        <p>每组对应一个选项。目标为空则该节点没有此选项，文本为空默认“继续”；要只导入节点，可不配置分支组。</p>
        <div v-for="(branch,i) in branches" :key="i" class="inline">
          <el-select v-model="branch.targetColumn" placeholder="目标节点列（必填）" filterable><el-option v-for="(h,j) in headers" :key="j" :value="j" :label="(j+1)+' · '+h" :disabled="!h"/></el-select>
          <el-select v-model="branch.textColumn" clearable placeholder="选项文本列（可选）" filterable><el-option v-for="(h,j) in headers" :key="j" :value="j" :label="(j+1)+' · '+h" :disabled="!h"/></el-select>
          <el-button @click="branches.splice(i,1)">删除</el-button>
        </div>
        <el-button :disabled="branches.length>=24" @click="branches.push({})">添加分支列组</el-button>
      </template>
      <template v-else>
        <div class="grid">
          <el-form-item label="连线工作表"><el-select v-model="choiceSheet"><el-option v-for="sheet in sheets.filter(s=>s.name!==nodeSheet)" :key="sheet.name" :label="sheet.name" :value="sheet.name" /></el-select></el-form-item>
          <el-form-item label="连线表头行"><el-input-number v-model="choiceHeaderRow" :min="1" :max="30" :precision="0"/></el-form-item>
        </div>
        <ExcelColumnMapping v-model="choiceColumns" :headers="edgeHeaders" :fields="edgeFields" :disabled="busy"/>
      </template>
      <p class="muted">只导入节点、选项和连线。好感度条件、跨局解锁、效果等说明不会自动成为规则，请导入后在规则编辑器中配置。</p>
      <el-button type="primary" :loading="busy" @click="generate">校验并生成预览</el-button>
    </el-form>
    <section v-if="preview" class="result">
      <h3>预览：{{preview.document.nodes.length}} 个节点 / {{preview.document.choices.length}} 条连线</h3>
      <el-alert v-for="warning in preview.warnings" :key="warning" :title="warning" type="warning" :closable="false" />
      <el-tabs>
        <el-tab-pane label="关系图"><ExcelGraphPreview :key="preview.digest" :document="preview.document" /></el-tab-pane>
        <el-tab-pane label="节点内容"><el-table :data="preview.document.nodes" max-height="400">
          <el-table-column prop="nodeKey" label="编号" width="130"/><el-table-column prop="title" label="标题" width="160"/>
          <el-table-column prop="nodeType" label="类型" width="100"/><el-table-column prop="content" label="正文及补充字段" show-overflow-tooltip/>
        </el-table></el-tab-pane>
        <el-tab-pane label="全部连线"><el-table :data="preview.document.choices" max-height="400">
          <el-table-column prop="sourceNodeKey" label="源节点"/><el-table-column prop="targetNodeKey" label="目标节点"/><el-table-column prop="choiceText" label="选项文本"/>
        </el-table></el-tab-pane>
      </el-tabs>
      <el-checkbox v-model="acknowledged" :disabled="busy">已核对预览和提示，确认生成{{target==='current'?'当前空白剧情图':'新项目'}}（不覆盖已有节点）</el-checkbox>
    </section>
    <template #footer><el-button :disabled="busy" @click="visible=false">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="!preview||!acknowledged||busy" @click="commit">确认一键生成</el-button></template>
  </el-dialog>
</template>
<style scoped>
.help,.upload,.error,.result{margin:16px 0}.upload{display:flex;gap:16px;flex-wrap:wrap}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:0 18px}
.inline{display:flex;gap:12px;margin:10px 0;flex-wrap:wrap}.inline>.el-input,.inline>.el-select{flex:1;min-width:200px}.el-select{width:100%}.inline>.el-select{width:auto}
.result>.el-alert{margin-bottom:8px}.muted{color:#6a7770;font-size:13px}.help .el-textarea{margin-top:10px}
</style>
