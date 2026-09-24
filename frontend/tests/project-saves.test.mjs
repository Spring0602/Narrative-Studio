import {test,before,after} from "node:test";
import assert from "node:assert/strict";
import {createServer} from "vite";
import {createRenderer,reactive,ref,nextTick} from "vue";
let server,drafts,clock;
const storage=new Map();
globalThis.localStorage={getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)};
before(async()=>{
  server=await createServer({server:{middlewareMode:true,hmr:false},appType:"custom"});
  drafts=await server.ssrLoadModule("/src/composables/editorDrafts.ts");
  clock=await server.ssrLoadModule("/src/utils/autosaveClock.ts");
});
after(async()=>{await server?.close();});
test("ten minute timer skips early ticks, catches up once and prevents overlapping writes",async()=>{
  let now=0,calls=0,finish;
  const tick=clock.createAutosaveClock(()=>{calls++;return new Promise(r=>finish=r);},()=>now);
  now=599999;assert.equal(await tick(),false);
  now=600000;const first=tick();assert.equal(calls,1);
  now=1800000;assert.equal(await tick(),false);
  finish();assert.equal(await first,true);
  const second=tick();assert.equal(calls,2);finish();await second;
  assert.equal(await tick(),false);
});
test("failed timer calls release the running guard but do not immediately retry",async()=>{
  let now=0,calls=0;
  const tick=clock.createAutosaveClock(async()=>{calls++;throw Error("offline");},()=>now);
  now=600000;await assert.rejects(tick(),/offline/);
  assert.equal(await tick(),false);now=1200000;await assert.rejects(tick(),/offline/);assert.equal(calls,2);
});
const renderer=createRenderer({
  createElement:()=>({}),createText:()=>({}),createComment:()=>({}),
  insert(){},remove(){},setText(){},setElementText(){},patchProp(){},
  parentNode:()=>null,nextSibling:()=>null,
});
function editor(project,allowed=true){
  const active=ref(false),enabled=ref(allowed),loading=ref(false),form=reactive({text:"original"});
  let controls;
  const app=renderer.createApp({setup(){
    controls=drafts.useEditorDraft({project:()=>project,slot:"node",module:"story",label:"node",entityTable:"story_node",
      active:()=>active.value,enabled:()=>enabled.value,retainWhenInactive:()=>loading.value,entityId:()=>1,
      read:()=>({...form}),restore:d=>{Object.assign(form,d.values);active.value=true;}});
    return ()=>null;
  }});app.mount({});
  return {app,active,enabled,loading,form,controls};
}
test("changed form persists immediately, undo clears it and explicit close clears it",()=>{
  const e=editor(901);e.active.value=true;e.form.text="edited";
  assert.equal(drafts.collectDrafts(901).node.values.text,"edited");
  e.form.text="original";assert.deepEqual(drafts.collectDrafts(901),{});
  e.form.text="next";e.active.value=false;assert.deepEqual(drafts.collectDrafts(901),{});e.app.unmount();
});
test("unmount preserves pending text and recovery waits for edit permission",async()=>{
  const e=editor(902);e.active.value=true;e.form.text="unsent";e.app.unmount();
  const saved=drafts.collectDrafts(902);assert.equal(saved.node.values.text,"unsent");
  drafts.stageDraftRecovery(903,saved);const restored=editor(903,false);
  assert.equal(restored.form.text,"original");restored.enabled.value=true;await nextTick();
  assert.equal(restored.form.text,"unsent");assert.equal(restored.active.value,true);restored.app.unmount();
});
test("temporary loading does not erase a pending form",()=>{
  const e=editor(904);e.active.value=true;e.form.text="keep";
  e.loading.value=true;e.active.value=false;
  assert.equal(drafts.collectDrafts(904).node.values.text,"keep");e.app.unmount();
});
test("a stale window refuses to overwrite another window's local recovery data",()=>{
  const e=editor(905);e.active.value=true;e.form.text="first";
  const key="narrative-editor-drafts-v1:0:905";storage.set(key,'{"other":"protected"}');
  e.form.text="second";
  assert.equal(storage.get(key),'{"other":"protected"}');
  assert.match(drafts.draftStorageError.value,/其他窗口/);e.app.unmount();
});
test("successful inline save resets the baseline and cached persistence survives remount",()=>{
  const e=editor(906);e.active.value=true;e.form.text="saved";e.controls.markSaved();
  assert.deepEqual(drafts.collectDrafts(906),{});e.app.unmount();
  const again=editor(906);again.active.value=true;again.form.text="new text";
  assert.equal(JSON.parse(storage.get("narrative-editor-drafts-v1:0:906")).node.values.text,"new text");
  again.app.unmount();
});
