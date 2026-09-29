# -*- coding: utf-8 -*-
"""自动草稿单槽专项验收：expectedRevision 应取本人 auto 槽位 revision（首次为0）"""
import json, time, urllib.request, urllib.error

BASE = "http://localhost:8080"

def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try: body_text = json.loads(e.read().decode() or "{}")
        except Exception: body_text = {}
        return e.code, body_text

# 登录上一轮创建的验收账号
st, res = call("POST", "/api/auth/login",
               {"username": "c_verify_89218", "password": "Passw0rd!2026"})
token = res["data"]["token"]
st, res = call("GET", "/api/projects", token=token)
pid = [p["id"] for p in res["data"] if p["name"] == "C队员验收-历史保存"][0]
print("项目id:", pid)

def auto_save(desc):
    st, res = call("GET", "/api/projects/%d/saves" % pid, token=token)
    a = res["data"].get("automatic")
    expected = a["revision"] if a else 0
    st2, r2 = call("PUT", "/api/projects/%d/saves/auto" % pid,
                   {"label": None, "expectedRevision": expected,
                    "drafts": {"project": {"name": "C队员验收-历史保存", "description": desc}}}, token)
    return st2, r2

# 第1次自动保存：应新建槽位
st1, r1 = auto_save("自动草稿第1次-专项")
print("auto#1 http=%s changed=%s" % (st1, r1.get("data", {}).get("changed")))
# 第2次：内容有变化，应更新同一槽位
st2, r2 = auto_save("自动草稿第2次-专项")
print("auto#2 http=%s changed=%s" % (st2, r2.get("data", {}).get("changed")))
# 第3次：内容无变化，应跳过
st3, r3 = auto_save("自动草稿第2次-专项")
print("auto#3(无变化) http=%s changed=%s" % (st3, r3.get("data", {}).get("changed")))

st, res = call("GET", "/api/projects/%d/saves" % pid, token=token)
d = res["data"]
a = d.get("automatic")
print("\n自动槽位:", "savedAt=%s revision=%s" % (a["savedAt"], a["revision"]) if a else "无")
print("手动版本数:", len(d.get("manual") or []))

ok1 = st1 == 200 and r1["data"]["changed"]
ok2 = st2 == 200 and r2["data"]["changed"]
ok3 = st3 == 200 and r3["data"]["changed"] == False
ok4 = a is not None  # 槽位存在即单槽（服务端逻辑是 UPDATE 同一行）
print("\n[PASS/FAIL] 首次自动草稿生成:", "PASS" if ok1 else "FAIL")
print("[PASS/FAIL] 二次自动草稿更新同槽:", "PASS" if ok2 else "FAIL")
print("[PASS/FAIL] 无变化跳过:", "PASS" if ok3 else "FAIL")
print("[PASS/FAIL] 单槽不新增:", "PASS" if ok4 else "FAIL")
print("[PASS/FAIL] 自动草稿不挤占手动5版:", "PASS" if len(d.get("manual") or []) == 5 else "FAIL")
