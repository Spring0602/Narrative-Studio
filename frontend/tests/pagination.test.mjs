import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import ts from "typescript";
const source = await readFile(new URL("../src/api/pagination.ts", import.meta.url), "utf8");
const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { loadAllPages } = await import("data:text/javascript;base64," + Buffer.from(output).toString("base64"));
test("loads records beyond page one", async () => {
  const calls = [];
  const result = await loadAllPages(async (page, size) => {
    calls.push([page, size]);
    return { page, size, total: 205, pages: 3, items: Array.from({ length: page === 3 ? 5 : 100 }, (_, i) => (page - 1) * 100 + i) };
  });
  assert.equal(result.items.length, 205); assert.equal(result.items.at(-1), 204);
  assert.deepEqual(calls, [[1,100],[2,100],[3,100]]);
});
test("empty list stays empty", async () => {
  const fullSteps = await loadAllPages(async page => ({
    page, size:100, total:10001, pages:101,
    items:Array.from({length:page===101?1:100},(_,i)=>(page-1)*100+i),
  }), 10001);
  assert.equal(fullSteps.items.length, 10001); // 10,000 choices plus initial step zero.
  assert.equal((await loadAllPages(async () => ({ page:1,size:100,total:0,pages:0,items:[] }))).items.length, 0);
});
test("oversized history fails explicitly instead of truncating", async () => {
  await assert.rejects(loadAllPages(async () => ({ page:1,size:100,total:10001,pages:101,items:[] })), /10000/);
});
test("concurrent count changes require refresh", async () => {
  await assert.rejects(loadAllPages(async page => ({ page,size:100,total:page===1?101:102,pages:2,items:[] })), /刷新/);
});
