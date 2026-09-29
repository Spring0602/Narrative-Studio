# -*- coding: utf-8 -*-
"""Narrative-Studio C队员验收脚本：历史保存 / 自动草稿 / 恢复副本 / 回归"""
import json, time, urllib.request, urllib.error

BASE = "http://localhost:8080"
results = []

def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode() or "{}")

def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("[PASS] " if ok else "[FAIL] ") + name + ("  | " + detail if detail else ""))

# 1. 注册/登录
ts = int(time.time())
uname = "c_verify_%d" % (ts % 100000)
st, res = call("POST", "/api/auth/register",
               {"username": uname, "password": "Passw0rd!2026", "displayName": "C队员验收机器人"})
if st != 200:
    st, res = call("POST", "/api/auth/login", {"username": uname, "password": "Passw0rd!2026"})
check("登录/注册", st == 200 and res.get("data", {}).get("token"),
      "user=%s http=%s" % (uname, st))
token = res["data"]["token"]

# 2. 创建项目
st, res = call("POST", "/api/projects",
               {"name": "C队员验收-历史保存", "description": "验收用项目，可删除"}, token)
check("创建项目", st == 200, "http=%s" % st)
pid = res["data"]["id"]

# 3. 查看保存状态初始值
st, res = call("GET", "/api/projects/%d/saves" % pid, token=token)
state0 = res.get("data", {})
check("保存状态接口(22表功能)", st == 200 and "manual" in state0,
      "manual=%s automatic=%s" % (len(state0.get("manual") or []), bool(state0.get("automatic"))))
rev0 = len(state0.get("manual") or [])  # revision 基线从状态接口拿
cur_hash = state0.get("currentHash")

# 4. 手动保存 6 次，验证最多 5 版
for i in range(1, 7):
    body = {"label": "手动v%d" % i,
            "drafts": {"project": {"name": "C队员验收-历史保存", "description": "第%d次保存" % i}},
            "expectedRevision": i - 1, "baselineHash": cur_hash}
    st, res = call("POST", "/api/projects/%d/saves" % pid, body, token)
    if st == 200:
        cur_hash = (res["data"].get("saved") or {}).get("hash", cur_hash)
    else:
        # 重试：expectedRevision 可能要求取最新 revision
        st2, r2 = call("GET", "/api/projects/%d/saves" % pid, token=token)
        m = r2["data"].get("manual") or []
        body["expectedRevision"] = (m[0]["revision"] if m else 0)
        st, res = call("POST", "/api/projects/%d/saves" % pid, body, token)
        if st == 200:
            cur_hash = (res["data"].get("saved") or {}).get("hash", cur_hash)
    print("  保存第%d次: http=%s" % (i, st))

st, res = call("GET", "/api/projects/%d/saves" % pid, token=token)
manual = res["data"].get("manual") or []
labels = [m["label"] for m in manual]
check("手动保存最多保留5版", len(manual) == 5 and "手动v1" not in labels and "手动v6" in labels,
      "共%d条: %s" % (len(manual), ",".join(labels)))

# 5. 自动草稿：保存两次，验证单槽不新增
for i in range(2):
    m = manual[0]["revision"] if manual else 0
    body = {"label": None,
            "drafts": {"project": {"name": "C队员验收-历史保存", "description": "自动草稿第%d次" % (i + 1)}},
            "expectedRevision": None}
    st2, r2 = call("GET", "/api/projects/%d/saves" % pid, token=token)
    mdata = r2["data"].get("manual") or []
    body["expectedRevision"] = (mdata[0]["revision"] if mdata else 0)
    st, res = call("PUT", "/api/projects/%d/saves/auto" % pid, body, token)
    print("  自动草稿第%d次: http=%s changed=%s" % (i + 1, st, res.get("data", {}).get("changed")))

st, res = call("GET", "/api/projects/%d/saves" % pid, token=token)
d = res["data"]
auto = d.get("automatic")
manual_after = d.get("manual") or []
check("自动草稿单槽(仅1份)", st == 200 and auto is not None,
      "automatic=%s revision=%s" % (auto["savedAt"] if auto else None, auto["revision"] if auto else None))
check("自动草稿不挤占手动5版", len(manual_after) == 5, "手动仍为%d条" % len(manual_after))
st2, r2 = call("PUT", "/api/projects/%d/saves/auto" % pid, body, token)  # 无变化再存一次
check("自动草稿无变化跳过", st2 == 200 and r2.get("data", {}).get("changed") == False,
      "changed=%s" % r2.get("data", {}).get("changed"))

# 6. 恢复副本：从最早保留的手动v2恢复为新项目
target_id = [m["id"] for m in manual_after if m["label"] == "手动v2"][0]
rev_t = [m["revision"] for m in manual_after if m["label"] == "手动v2"][0]
st, res = call("POST", "/api/projects/%d/saves/%d/restore-copy" % (pid, target_id),
               {"confirm": True, "expectedRevision": rev_t, "name": "C队员验收-历史保存 · 恢复副本"}, token)
check("恢复生成新项目副本", st == 200 and res.get("data", {}).get("project"),
      "newProjectId=%s" % (res.get("data", {}).get("project") or {}).get("id"))
new_pid = (res.get("data", {}).get("project") or {}).get("id")

# 7. 原项目不受影响
st, res = call("GET", "/api/projects/%d" % pid, token=token)
orig = res.get("data", {})
st2, r2 = call("GET", "/api/projects/%d/saves" % pid, token=token)
check("原项目完整保留", st == 200 and orig.get("name") == "C队员验收-历史保存" and
      len(r2.get("data", {}).get("manual") or []) == 5,
      "原项目名=%s 手动仍=%s条" % (orig.get("name"), len(r2.get("data", {}).get("manual") or [])))

# 8. 回归：项目列表 / 新副本可见
st, res = call("GET", "/api/projects", token=token)
names = [p["name"] for p in (res.get("data") or [])]
check("项目列表回归", st == 200 and len(names) >= 2, "项目数=%d" % len(names))

print("\n========== 验收汇总 ==========")
passed = sum(1 for _, ok, _ in results if ok)
for n, ok, dt in results:
    print(("PASS " if ok else "FAIL ") + n + ("  | " + dt if dt else ""))
print("通过 %d / %d" % (passed, len(results)))
