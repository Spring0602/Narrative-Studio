import {test,before,after} from "node:test";
import assert from "node:assert/strict";
import {createServer} from "vite";
import {createSSRApp,h} from "vue";
import {renderToString} from "@vue/server-renderer";
import ElementPlus,{ID_INJECTION_KEY,ZINDEX_INJECTION_KEY} from "element-plus";
let server,headers,Mapping;
before(async()=>{
  server=await createServer({server:{middlewareMode:true,hmr:false},appType:"custom"});
  headers=await server.ssrLoadModule("/src/utils/excelHeaders.ts");
  Mapping=(await server.ssrLoadModule("/src/components/ExcelColumnMapping.vue")).default;
});
after(async()=>{await server?.close();});
test("recognizes common Chinese and English aliases ignoring spacing",()=>{
  assert.deepEqual(headers.guessColumns(["Node_ID"," 节点标题 ","剧情内容","Node Type"],["nodeKey","title","content","nodeType"]),
    {nodeKey:0,title:1,content:2,nodeType:3});
});
test("ambiguous headers are left for explicit mapping",()=>{
  assert.deepEqual(headers.guessColumns(["ID","节点编号","自定义称呼"],["nodeKey","title"]),{});
});
test("finds headers below a title row",()=>{
  assert.equal(headers.guessHeaderRow([["项目说明"],[],["节点编号","标题"]],["nodeKey","title"]),3);
});
test("recognizes multiple branch pairs and leaves unrelated headers untouched",()=>{
  assert.deepEqual(headers.guessBranches(["id","title","选项1","选项1目标","选项2文本","目标2"],[0,1]),
    [{targetColumn:3,textColumn:2},{targetColumn:5,textColumn:4}]);
});
test("accepts simple next and choice without mistaking node title",()=>{
  assert.deepEqual(headers.guessBranches(["id","title","next","choice"],[0,1]),[{targetColumn:2,textColumn:3}]);
});
test("arbitrary headers stay available in the field mapping UI",async()=>{
  const app=createSSRApp({render:()=>h(Mapping,{modelValue:{nodeKey:0},headers:["事件代号","命运篇章"],
    fields:[{key:"nodeKey",label:"节点编号"},{key:"title",label:"节点标题"}]})});
  app.use(ElementPlus);app.provide(ID_INJECTION_KEY,{prefix:200,current:0});app.provide(ZINDEX_INJECTION_KEY,{current:0});
  const context={};
  const html=(await renderToString(app,context))+Object.values(context.teleports??{}).join("");
  assert.match(html,/事件代号/);assert.match(html,/命运篇章/);assert.match(html,/节点标题/);
});
