<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import {
  changePassword,
  confirmEmailVerification,
  getAccount,
  requestEmailVerification,
  type AccountProfile,
} from "@/api/account";

const router = useRouter();
const profile = ref<AccountProfile | null>(null);
const loading = ref(true);
const loadError = ref("");
const saving = ref(false);
const emailForm = reactive({ email: "", password: "" });
const tokenForm = reactive({ token: "" });
const passwordForm = reactive({
  currentPassword: "",
  newPassword: "",
  confirmPassword: "",
});
function formatTime(value?: string | null) {
  return value
    ? new Date(value).toLocaleString("zh-CN", { hour12: false })
    : "尚未验证";
}
async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    profile.value = await getAccount();
    emailForm.email = profile.value.email || "";
  } catch (error) {
    loadError.value = apiErrorMessage(
      error,
      "账户资料加载失败，请检查后端服务后重试。",
    );
  } finally {
    loading.value = false;
  }
}
async function requestEmail() {
  if (!emailForm.email.trim() || !emailForm.password)
    return void ElMessage.warning("请填写邮箱和当前密码");
  saving.value = true;
  try {
    const message = await requestEmailVerification(
      emailForm.email.trim(),
      emailForm.password,
    );
    emailForm.password = "";
    ElMessage.success(message);
  } catch (error) {
    ElMessage.error(
      apiErrorMessage(error, "申请验证邮件失败；SMTP 未配置时后端会返回 503"),
    );
  } finally {
    saving.value = false;
  }
}
async function confirmEmail() {
  if (!/^[A-Za-z0-9_-]{43}$/.test(tokenForm.token.trim()))
    return void ElMessage.warning("邮件令牌应为 43 位 Base64URL 字符串");
  saving.value = true;
  try {
    await confirmEmailVerification(tokenForm.token.trim());
    ElMessage.success("邮箱验证成功，请重新登录");
    logout();
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "邮箱令牌无效、已过期或已经使用"));
  } finally {
    saving.value = false;
  }
}
async function savePassword() {
  if (!passwordForm.currentPassword)
    return void ElMessage.warning("请输入当前密码");
  if (
    passwordForm.newPassword.length < 8 ||
    passwordForm.newPassword.length > 72
  )
    return void ElMessage.warning("新密码必须为 8 至 72 个字符");
  if (new TextEncoder().encode(passwordForm.newPassword).length > 72)
    return void ElMessage.warning("新密码的 UTF-8 编码不能超过 72 字节");
  if (passwordForm.newPassword !== passwordForm.confirmPassword)
    return void ElMessage.warning("两次输入的新密码不一致");
  try {
    await ElMessageBox.confirm(
      "修改密码后当前登录令牌会立即失效，需要重新登录。",
      "修改密码",
      { type: "warning" },
    );
  } catch {
    return;
  }
  saving.value = true;
  try {
    await changePassword(
      passwordForm.currentPassword,
      passwordForm.newPassword,
    );
    ElMessage.success("密码已修改，请重新登录");
    logout();
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "密码修改失败"));
  } finally {
    saving.value = false;
  }
}
function logout() {
  localStorage.removeItem("narrative_token");
  void router.replace("/login");
}
onMounted(load);
</script>

<template>
  <section class="account-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">ACCOUNT</p>
        <div class="heading-title-row">
          <img
            class="page-heading-icon"
            src="../assets/icons/shield-check.png"
            alt=""
            aria-hidden="true"
          />
          <h1>账户设置</h1>
        </div>
        <p class="muted">查看安全资料、验证邮箱并修改密码。</p>
      </div>
    </div>
    <el-result
      v-if="loadError"
      icon="error"
      title="账户加载失败"
      :sub-title="loadError"
      ><template #extra
        ><el-button type="primary" @click="load">重新加载</el-button></template
      ></el-result
    >
    <div v-else v-loading="loading" class="cards">
      <section class="card">
        <h2>账户资料</h2>
        <dl v-if="profile">
          <div>
            <dt>用户名</dt>
            <dd>{{ profile.username }}</dd>
          </div>
          <div>
            <dt>显示名称</dt>
            <dd>{{ profile.displayName }}</dd>
          </div>
          <div>
            <dt>系统角色</dt>
            <dd>{{ profile.role }}</dd>
          </div>
          <div>
            <dt>账户状态</dt>
            <dd>{{ profile.status }}</dd>
          </div>
          <div>
            <dt>邮箱</dt>
            <dd>{{ profile.email || "尚未绑定" }}</dd>
          </div>
          <div>
            <dt>验证时间</dt>
            <dd>{{ formatTime(profile.emailVerifiedAt) }}</dd>
          </div>
        </dl>
      </section>
      <section class="card">
        <h2>绑定或更换邮箱</h2>
        <p>后端发送一次性令牌；如果 SMTP 未配置，会明确提示服务不可用。</p>
        <el-form label-position="top"
          ><el-form-item label="邮箱"
            ><el-input
              v-model="emailForm.email"
              maxlength="254" /></el-form-item
          ><el-form-item label="当前密码"
            ><el-input
              v-model="emailForm.password"
              type="password"
              show-password
              maxlength="72" /></el-form-item
          ><el-button type="primary" :loading="saving" @click="requestEmail"
            >申请验证邮件</el-button
          ><el-divider /><el-form-item label="邮件中的 43 位令牌"
            ><el-input v-model="tokenForm.token" maxlength="43" /></el-form-item
          ><el-button :loading="saving" @click="confirmEmail"
            >确认邮箱令牌</el-button
          ></el-form
        >
      </section>
      <section class="card">
        <h2>修改密码</h2>
        <el-alert
          title="修改成功后旧 JWT 失效，页面会返回登录页。"
          type="info"
          :closable="false"
        /><el-form label-position="top"
          ><el-form-item label="当前密码"
            ><el-input
              v-model="passwordForm.currentPassword"
              type="password"
              show-password
              maxlength="72" /></el-form-item
          ><el-form-item label="新密码"
            ><el-input
              v-model="passwordForm.newPassword"
              type="password"
              show-password
              maxlength="72" /></el-form-item
          ><el-form-item label="再次输入新密码"
            ><el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              show-password
              maxlength="72" /></el-form-item
          ><el-button type="primary" :loading="saving" @click="savePassword"
            >修改密码</el-button
          ></el-form
        >
      </section>
    </div>
  </section>
</template>

<style scoped>
.account-page {
  max-width: 1100px;
}
.page-heading h1 {
  margin: 6px 0 8px;
  font-size: 42px;
}
.eyebrow {
  color: #b2663d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}
.muted {
  color: #78847e;
}
.cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 24px;
}
.card {
  padding: 24px;
  border: 1px solid #dfe3de;
  border-radius: 14px;
  background: #fff;
}
.card:last-child {
  grid-column: 1/-1;
}
.card h2 {
  margin-top: 0;
}
.card > p {
  color: #75817a;
}
.card dl div {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #edf0ed;
}
.card dt {
  color: #7d8882;
}
.card dd {
  margin: 0;
}
@media (max-width: 760px) {
  .cards {
    grid-template-columns: 1fr;
  }
  .card:last-child {
    grid-column: auto;
  }
}
</style>
