<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import {
  confirmEmailVerification,
  confirmPasswordReset,
  requestPasswordReset,
} from "@/api/account";

const router = useRouter();
const loading = ref(false);
const form = reactive({
  email: "",
  emailToken: "",
  resetToken: "",
  password: "",
  confirmPassword: "",
});
function validToken(token: string) {
  return /^[A-Za-z0-9_-]{43}$/.test(token.trim());
}
function validPassword(password: string) {
  return (
    password.length >= 8 &&
    password.length <= 72 &&
    new TextEncoder().encode(password).length <= 72
  );
}
async function requestReset() {
  if (!form.email.trim()) return void ElMessage.warning("请输入邮箱");
  loading.value = true;
  try {
    ElMessage.success(await requestPasswordReset(form.email.trim()));
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "密码重置申请失败"));
  } finally {
    loading.value = false;
  }
}
async function confirmReset() {
  if (!validToken(form.resetToken))
    return void ElMessage.warning("请输入邮件中的 43 位重置令牌");
  if (!validPassword(form.password))
    return void ElMessage.warning("新密码须为 8 至 72 个字符且不超过 72 字节");
  if (form.password !== form.confirmPassword)
    return void ElMessage.warning("两次输入的新密码不一致");
  loading.value = true;
  try {
    await confirmPasswordReset(form.resetToken.trim(), form.password);
    ElMessage.success("密码已重置，请使用新密码登录");
    await router.push("/login");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "重置令牌无效、已过期或已经使用"));
  } finally {
    loading.value = false;
  }
}
async function confirmEmail() {
  if (!validToken(form.emailToken))
    return void ElMessage.warning("请输入邮件中的 43 位邮箱令牌");
  loading.value = true;
  try {
    await confirmEmailVerification(form.emailToken.trim());
    ElMessage.success("邮箱验证成功，请重新登录");
    await router.push("/login");
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "邮箱令牌无效、已过期或已经使用"));
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="recovery-page">
    <section class="recovery-card">
      <p class="eyebrow">ACCOUNT RECOVERY</p>
      <div class="heading-title-row">
        <img
          class="page-heading-icon"
          src="../assets/icons/shield-check.png"
          alt=""
          aria-hidden="true"
        />
        <h1>账户验证与找回</h1>
      </div>
      <el-tabs
        ><el-tab-pane label="找回密码"
          ><el-form label-position="top"
            ><el-form-item label="已验证邮箱"
              ><el-input v-model="form.email" maxlength="254" /></el-form-item
            ><el-button type="primary" :loading="loading" @click="requestReset"
              >申请重置邮件</el-button
            ><el-divider /><el-form-item label="43 位重置令牌"
              ><el-input
                v-model="form.resetToken"
                maxlength="43" /></el-form-item
            ><el-form-item label="新密码"
              ><el-input
                v-model="form.password"
                type="password"
                show-password
                maxlength="72" /></el-form-item
            ><el-form-item label="再次输入"
              ><el-input
                v-model="form.confirmPassword"
                type="password"
                show-password
                maxlength="72" /></el-form-item
            ><el-button type="primary" :loading="loading" @click="confirmReset"
              >确认重置密码</el-button
            ></el-form
          ></el-tab-pane
        ><el-tab-pane label="确认邮箱"
          ><el-form label-position="top"
            ><el-form-item label="43 位邮箱验证令牌"
              ><el-input
                v-model="form.emailToken"
                maxlength="43" /></el-form-item
            ><el-button type="primary" :loading="loading" @click="confirmEmail"
              >确认邮箱</el-button
            ></el-form
          ></el-tab-pane
        ></el-tabs
      ><el-button link @click="router.push('/login')">返回登录</el-button>
    </section>
  </main>
</template>

<style scoped>
.recovery-page {
  display: grid;
  min-height: 100vh;
  padding: 30px;
  background: #1f302a;
  place-items: center;
}
.recovery-card {
  width: min(620px, 100%);
  padding: 36px;
  border-radius: 16px;
  background: #fbfbf7;
}
.recovery-card h1 {
  margin: 7px 0 24px;
}
.eyebrow {
  color: #b2663d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}
</style>
