import {ref,watch,onMounted,onBeforeUnmount,effectScope,type Ref} from "vue";
import type {Drafts,EditorDraft} from "@/api/projectSaves";
const buckets=new Map<string,Ref<Drafts>>();
const recovering=new Map<string,Drafts>();
export const draftStorageError=ref("");
const recoveryTick=ref(0);
function key(project:number) {
  let user=0;
  try{user=Number(JSON.parse(localStorage.getItem("narrative_user")||"{}").id)||0;}catch{}
  return "narrative-editor-drafts-v1:"+user+":"+project;
}
export function draftBucket(project:number):Ref<Drafts> {
  const id=key(project);let bucket=buckets.get(id);
  if(!bucket) {
    let initial:Drafts={};
    let lastStored:string|null=null;
    try{lastStored=localStorage.getItem(id);const data=JSON.parse(lastStored||"{}");if(data&&typeof data==="object"&&!Array.isArray(data))initial=data;}catch{draftStorageError.value="本机恢复草稿读取失败，请勿清理浏览器数据，先保存版本。";}
    bucket=ref(initial);buckets.set(id,bucket);
    // Persistence outlives a module/toolbar, so navigating away cannot stop it.
    effectScope(true).run(()=>watch(bucket!,()=>{
      try {
        if(localStorage.getItem(id)!==lastStored)throw new Error("其他窗口已更新本机草稿");
        const text=JSON.stringify(bucket!.value);
        if(text.length>500000)throw new Error("本机草稿过大");
        localStorage.setItem(id,text);lastStored=text;draftStorageError.value="";
      }catch{draftStorageError.value="本机草稿写入暂停（其他窗口已更新、容量或浏览器限制），请立即保存版本，勿依赖退出恢复。";}
    },{deep:true,flush:"sync"}));
  }
  return bucket;
}
export function collectDrafts(project:number):Drafts {return JSON.parse(JSON.stringify(draftBucket(project).value));}
export function stageDraftRecovery(project:number,drafts:Drafts) {
  draftBucket(project).value=JSON.parse(JSON.stringify(drafts));
  recovering.set(key(project),JSON.parse(JSON.stringify(drafts)));recoveryTick.value++;
}
export function discardDrafts(project:number) {draftBucket(project).value={};recovering.delete(key(project));}
export function useEditorDraft(options:{
  project:()=>number;slot:string;module:string;label:string;entityTable:string;
  active:()=>boolean;enabled:()=>boolean;entityId:()=>number|null;retainWhenInactive?:()=>boolean;
  read:()=>Record<string,any>;restore:(draft:EditorDraft)=>void;
}) {
  let baseline="",applying=false;
  const bucket=()=>draftBucket(options.project());
  function recover() {
    if(!options.enabled())return;
    const drafts=recovering.get(key(options.project()));
    const entry=drafts?.[options.slot];
    if(!entry)return;
    delete drafts![options.slot];
    applying=true;options.restore(entry);applying=false;
    // Recovered text is deliberately still pending until the user submits or cancels its form.
    baseline="";bucket().value[options.slot]=entry;
  }
  watch(()=>options.active(),active=>{
    if(applying)return;
    if(active)baseline=JSON.stringify(options.read());
    else if(options.enabled()&&!options.retainWhenInactive?.())delete bucket().value[options.slot];
  },{flush:"sync"});
  watch(options.read,()=>{
    if(applying||!options.enabled()||!options.active())return;
    const values=JSON.parse(JSON.stringify(options.read()));
    if(JSON.stringify(values)===baseline){delete bucket().value[options.slot];return;}
    bucket().value[options.slot]={module:options.module,label:options.label,entityTable:options.entityTable,entityId:options.entityId(),values};
  },{deep:true,flush:"sync"});
  watch(recoveryTick,recover);
  watch(options.enabled,enabled=>{if(enabled)recover();});
  onMounted(recover);
  // Keep a pending form when changing modules or leaving the application.
  onBeforeUnmount(()=>{if(options.active()&&options.enabled()&&JSON.stringify(options.read())!==baseline)
    bucket().value[options.slot]={module:options.module,label:options.label,entityTable:options.entityTable,entityId:options.entityId(),values:JSON.parse(JSON.stringify(options.read()))};});
  return {markSaved:()=>{baseline=JSON.stringify(options.read());delete bucket().value[options.slot];}};
}
