<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { apiErrorMessage } from "@/api/errors";
import { login, register } from "@/api/auth";

const router = useRouter();
const mode = ref<"login" | "register">("login");
const loading = ref(false);
const form = reactive({ username: "", password: "", displayName: "" });
const isLogin = computed(() => mode.value === "login");

function switchMode(nextMode: "login" | "register") {
  mode.value = nextMode;
  form.password = "";
  if (nextMode === "login") form.displayName = "";
}

async function submit() {
  const username = form.username.trim();
  const displayName = form.displayName.trim();
  if (!username || !form.password || (!isLogin.value && !displayName)) {
    ElMessage.warning("请填写完整信息");
    return;
  }
  if (!isLogin.value && !/^[A-Za-z0-9_]{4,32}$/.test(username)) {
    ElMessage.warning("用户名须为 4 至 32 位字母、数字或下划线");
    return;
  }
  if (
    !isLogin.value &&
    (form.password.length < 8 || form.password.length > 72)
  ) {
    ElMessage.warning("密码须为 8 至 72 个字符");
    return;
  }

  loading.value = true;
  try {
    const result = isLogin.value
      ? await login(username, form.password)
      : await register(username, form.password, displayName);
    localStorage.setItem("narrative_token", result.token);
    localStorage.setItem("narrative_user", JSON.stringify(result));
    await router.push("/projects");
  } catch (error) {
    ElMessage.error(
      apiErrorMessage(
        error,
        isLogin.value ? "登录失败，请检查用户名和密码" : "注册失败，请稍后重试",
      ),
    );
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="story-panel">
      <div class="brand-line">
        <span class="brand-mark">NS</span>
        <div>
          <strong>NARRATIVE STUDIO</strong>
          <small>互动叙事创作与验证平台</small>
        </div>
      </div>

      <div class="hero-copy">
        <p class="section-label">WRITE · CONNECT · VERIFY</p>
        <h1>
          <span>让每一条</span>
          <span class="title-highlight">
            <img
              class="title-sparkle"
              src="../assets/icons/sparkle.png"
              alt=""
              aria-hidden="true"
            />
            故事线
          </span>
          <span>都有迹可循</span>
        </h1>
        <p class="hero-description">
          <span>把灵感整理成清晰的角色 节点与选择</span>
          <span>在模拟中看见每一条故事路径的结果</span>
        </p>
        <div class="feature-list" aria-label="核心能力">
          <span
            ><img
              src="../assets/icons/quill-writing.png"
              alt=""
            />灵感落笔</span
          >
          <span
            ><img src="../assets/icons/branch-flow.png" alt="" />分支生长</span
          >
          <span
            ><img src="../assets/icons/shield-check.png" alt="" />逻辑验证</span
          >
        </div>
      </div>

      <footer class="story-footer">
        <span>COURSE EDITION · 2026</span>
        <span class="footer-rule"></span>
        <span>STRUCTURED STORYTELLING</span>
      </footer>
    </section>

    <section class="form-panel">
      <div class="form-wrap">
        <div class="mobile-brand">
          <span class="brand-mark">NS</span>
          <strong>NARRATIVE STUDIO</strong>
        </div>

        <div class="form-heading">
          <p>{{ isLogin ? "欢迎回来" : "加入叙事工坊" }}</p>
          <h2>{{ isLogin ? "继续你的故事" : "创建创作者账户" }}</h2>
          <span>
            {{
              isLogin ? "登录后进入项目工作台" : "只需一分钟，即可开始构建剧情"
            }}
          </span>
        </div>

        <div class="mode-switch" role="tablist" aria-label="登录或注册">
          <button
            :class="{ active: isLogin }"
            type="button"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            :class="{ active: !isLogin }"
            type="button"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <el-form
          class="login-form"
          label-position="top"
          @submit.prevent="submit"
        >
          <el-form-item v-if="!isLogin" label="显示名称">
            <el-input
              v-model="form.displayName"
              maxlength="40"
              autocomplete="name"
              placeholder="例如：剧情策划"
              size="large"
            />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input
              v-model="form.username"
              maxlength="32"
              autocomplete="username"
              placeholder="请输入用户名"
              size="large"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              :autocomplete="isLogin ? 'current-password' : 'new-password'"
              :placeholder="isLogin ? '请输入密码' : '至少 8 个字符'"
              maxlength="72"
              size="large"
            />
          </el-form-item>

          <div v-if="isLogin" class="form-options">
            <span>使用已注册的项目账户登录</span>
            <button type="button" @click="router.push('/account-recovery')">
              忘记密码？
            </button>
          </div>

          <el-button
            class="submit-button"
            type="primary"
            size="large"
            native-type="submit"
            :loading="loading"
          >
            {{ isLogin ? "进入工作台" : "创建账户" }}
            <span v-if="!loading" class="button-arrow">→</span>
          </el-button>
        </el-form>

        <p class="switch-tip">
          {{ isLogin ? "还没有账户？" : "已经拥有账户？" }}
          <button
            type="button"
            @click="switchMode(isLogin ? 'register' : 'login')"
          >
            {{ isLogin ? "免费注册" : "返回登录" }}
          </button>
        </p>

        <button
          v-if="!isLogin"
          class="recovery-link"
          type="button"
          @click="router.push('/account-recovery')"
        >
          确认邮箱或找回密码
        </button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  --ink: #15241f;
  --forest: #17332b;
  --forest-light: #244a3d;
  --paper: #f6f4ee;
  --copper: #c87645;
  --line: #d9ded8;
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(500px, 0.82fr);
  min-height: 100vh;
  color: var(--ink);
  background: var(--paper);
}

.story-panel {
  position: relative;
  display: flex;
  min-height: 100vh;
  padding: clamp(40px, 5vw, 76px) clamp(48px, 7vw, 112px);
  overflow: hidden;
  color: #f5f1e8;
  background:
    linear-gradient(90deg, #0d241fe8 0%, #102b25dc 54%, #102b25b8 100%),
    linear-gradient(180deg, #071b17b8 0%, transparent 42%, #0b211de0 100%),
    url("../assets/backgrounds/narrative-network.png") 36% center / cover
      no-repeat;
  flex-direction: column;
  justify-content: space-between;
}

.story-panel::before,
.story-panel::after {
  display: none;
}

.brand-line,
.mobile-brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  display: grid;
  width: 42px;
  height: 42px;
  border: 1px solid #d58a5e;
  border-radius: 50%;
  color: #efae7e;
  font-family: Georgia, serif;
  font-size: 14px;
  letter-spacing: 0.06em;
  place-items: center;
}

.brand-line strong,
.brand-line small {
  display: block;
}

.brand-line strong {
  color: #efae7e;
  font-size: 13px;
  letter-spacing: 0.24em;
}

.brand-line small {
  margin-top: 5px;
  color: #aabbb4;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.hero-copy {
  position: relative;
  z-index: 1;
  max-width: 760px;
  margin: 8vh 0;
}

.section-label {
  margin: 0 0 24px;
  color: #d89062;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.26em;
}

.hero-copy h1 {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  margin: 0;
  font-family: "Songti SC", "STSong", Georgia, serif;
  font-size: clamp(52px, 4.7vw, 78px);
  font-weight: 500;
  letter-spacing: -0.035em;
  line-height: 1.08;
}

.hero-copy h1 > span {
  display: block;
  white-space: nowrap;
}

.title-highlight {
  padding-left: 18px;
}

.title-sparkle {
  width: 0.56em;
  height: 0.56em;
  margin-right: 8px;
  object-fit: contain;
  filter: brightness(0) saturate(100%) invert(75%) sepia(58%) saturate(937%)
    hue-rotate(329deg) brightness(103%) contrast(101%);
}

.hero-description {
  max-width: 610px;
  margin: 32px 0 0;
  color: #bbc9c3;
  font-size: clamp(15px, 1.25vw, 18px);
  line-height: 1.9;
}

.hero-description span {
  display: block;
}

.feature-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 36px;
}

.feature-list span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px 9px 11px;
  border: 1px solid #efb18452;
  border-radius: 999px;
  color: #f5eee7;
  background: #ffffff12;
  font-size: 12px;
  backdrop-filter: blur(8px);
}

.feature-list img {
  width: 32px;
  height: 32px;
  object-fit: contain;
  filter: brightness(0) saturate(100%) invert(75%) sepia(58%) saturate(937%)
    hue-rotate(329deg) brightness(103%) contrast(101%);
}

.story-footer {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 15px;
  color: #83978f;
  font-size: 9px;
  letter-spacing: 0.16em;
}

.footer-rule {
  width: 40px;
  height: 1px;
  background: #71877e;
}

.form-panel {
  display: grid;
  min-height: 100vh;
  padding: 48px clamp(44px, 5vw, 88px);
  background: radial-gradient(circle at 100% 0, #d8c9b52b, transparent 35%),
    var(--paper);
  place-items: center;
}

.form-wrap {
  width: min(430px, 100%);
}

.mobile-brand {
  display: none;
}

.form-heading > p {
  margin: 0 0 10px;
  color: var(--copper);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.form-heading h2 {
  margin: 0;
  color: var(--ink);
  font-family: "Songti SC", "STSong", Georgia, serif;
  font-size: 40px;
  font-weight: 600;
  letter-spacing: -0.04em;
}

.form-heading > span {
  display: block;
  margin-top: 10px;
  color: #77827c;
  font-size: 14px;
}

.mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 5px;
  margin-top: 32px;
  padding: 5px;
  border: 1px solid #dde1dc;
  border-radius: 12px;
  background: #ecece6;
}

.mode-switch button {
  padding: 10px;
  border: 0;
  border-radius: 8px;
  color: #7d8781;
  background: transparent;
  cursor: pointer;
  transition: 0.2s ease;
}

.mode-switch button.active {
  color: var(--ink);
  background: #fff;
  box-shadow: 0 3px 12px #24352f12;
  font-weight: 700;
}

.login-form {
  margin-top: 26px;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-form-item__label) {
  padding-bottom: 8px;
  color: #3c4b45;
  font-size: 13px;
  font-weight: 600;
}

:deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 9px;
  background: #fffefa;
  box-shadow: 0 0 0 1px var(--line) inset;
  transition: 0.2s ease;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #9cac9f inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--forest-light) inset,
    0 0 0 3px #315a4b12;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -3px 0 20px;
  color: #87908b;
  font-size: 12px;
}

.form-options button,
.switch-tip button,
.recovery-link {
  padding: 0;
  border: 0;
  color: #3f6d5b;
  background: none;
  cursor: pointer;
}

.submit-button {
  width: 100%;
  min-height: 50px;
  border: 0;
  border-radius: 9px;
  background: linear-gradient(115deg, var(--forest), #2d5849);
  box-shadow: 0 12px 24px #17332b24;
  font-weight: 700;
  letter-spacing: 0.06em;
  transition: 0.2s ease;
}

.submit-button:hover {
  background: linear-gradient(115deg, #204237, #376756);
  box-shadow: 0 14px 28px #17332b30;
  transform: translateY(-1px);
}

.button-arrow {
  margin-left: 10px;
  font-size: 18px;
  font-weight: 400;
}

.switch-tip {
  margin: 24px 0 0;
  color: #7f8983;
  text-align: center;
  font-size: 13px;
}

.switch-tip button {
  margin-left: 5px;
  font-weight: 700;
}

.recovery-link {
  display: block;
  margin: 16px auto 0;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr) minmax(430px, 0.8fr);
  }

  .story-panel {
    padding: 46px 58px;
  }

  .hero-copy h1 {
    font-size: clamp(46px, 5.7vw, 64px);
  }

  .feature-list span:last-child {
    display: none;
  }
}

@media (max-width: 800px) {
  .login-page {
    display: block;
    min-height: 100vh;
  }

  .story-panel {
    display: none;
  }

  .form-panel {
    min-height: 100vh;
    padding: 32px 24px;
  }

  .mobile-brand {
    display: flex;
    margin-bottom: 64px;
  }

  .mobile-brand .brand-mark {
    color: var(--copper);
  }

  .mobile-brand strong {
    font-size: 12px;
    letter-spacing: 0.18em;
  }
}

@media (max-width: 480px) {
  .form-panel {
    align-items: start;
    padding-top: 26px;
  }

  .mobile-brand {
    margin-bottom: 48px;
  }

  .form-heading h2 {
    font-size: 34px;
  }
}
</style>
