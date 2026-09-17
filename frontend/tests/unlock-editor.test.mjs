import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "vite";
import { createSSRApp, h } from "vue";
import { renderToString } from "@vue/server-renderer";
import ElementPlus, { ID_INJECTION_KEY, ZINDEX_INJECTION_KEY } from "element-plus";
let server, Editor;
before(async () => {
  server = await createServer({ server: { middlewareMode:true, hmr:false }, appType:"custom" });
  Editor = (await server.ssrLoadModule("/src/components/UnlockRuleEditor.vue")).default;
});
after(async () => { await server?.close(); });
const nodes = [{nodeKey:"a",title:"Route A",nodeType:"ENDING"},{nodeKey:"b",title:"Route B",nodeType:"ENDING"}];
async function render(modelValue, readonly = false) {
  const app=createSSRApp({render:()=>h(Editor,{modelValue,nodes,variables:[],readonly})});
  app.use(ElementPlus);
  app.provide(ID_INJECTION_KEY,{prefix:100,current:0});
  app.provide(ZINDEX_INJECTION_KEY,{current:0});
  return renderToString(app);
}
test("nested ALL and threshold render children without variables", async () => {
  const html=await render({type:"ALL",children:[{type:"ENDING",nodeKey:"a"},
    {type:"AT_LEAST",count:1,children:[{type:"ENDING",nodeKey:"b"}]}]});
  assert.ok((html.match(/class="unlock-rule"/g)||[]).length >= 4);
  assert.match(html,/至少满足数量/);assert.match(html,/前置剧情节点/);
});
test("readonly rules do not expose add or remove actions", async () => {
  const html=await render({type:"ALL",children:[{type:"ENDING",nodeKey:"a"},{type:"ENDING",nodeKey:"b"}]},true);
  assert.doesNotMatch(html,/添加子条件|移除此条件/);
});
test("NOT has one child and no add-child action", async () => {
  const html=await render({type:"NOT",children:[{type:"ENDING",nodeKey:"a"}]});
  assert.doesNotMatch(html,/添加子条件/);
  assert.equal((html.match(/class="unlock-rule"/g)||[]).length,2);
});
