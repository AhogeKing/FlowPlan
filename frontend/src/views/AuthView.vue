<template>
  <main class="auth-page">
    <section class="auth-shell">
      <section class="auth-visual">
        <img src="/venarida.png" alt="FlowPlan">
        <div>
          <div class="auth-brand">FlowPlan</div>
          <p>整理项目、生成计划、温柔地推进今天。</p>
        </div>
      </section>

      <section class="auth-card">
        <h1>{{ isRegistering ? '注册' : '登录' }}</h1>
        <p class="muted">{{ isRegistering ? '创建你的 FlowPlan 计划空间' : '进入你的 FlowPlan 今日计划空间' }}</p>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label class="field">
          <span>用户名</span>
          <input
            id="username"
            v-model.trim="form.username"
            name="username"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
          >
        </label>

        <label v-if="isRegistering" class="field">
          <span>邮箱</span>
          <input
            id="email"
            v-model.trim="form.email"
            name="email"
            type="email"
            autocomplete="email"
            placeholder="可选"
          >
        </label>

        <label class="field">
          <span>密码</span>
          <input
            id="password"
            v-model="form.password"
            name="password"
            type="password"
            :autocomplete="isRegistering ? 'new-password' : 'current-password'"
            placeholder="请输入密码"
          >
        </label>

        <p v-if="message" :class="['message', messageType]">{{ message }}</p>

        <div class="auth-actions">
          <button class="button" type="submit" :disabled="loading">
            {{ submitText }}
          </button>
          <button class="button secondary" type="button" @click="toggleMode">
            {{ isRegistering ? '去登录' : '注册账号' }}
          </button>
        </div>
      </form>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { login, register } from "../api/auth";

const emit = defineEmits(["login-success"]);

const form = reactive({
    username: "",
    password: "",
    email: ""
});

const isRegistering = ref(false);
const loading = ref(false);
const message = ref("");
const messageType = ref("error");

const submitText = computed(() => {
    if (loading.value) {
        return isRegistering.value ? "注册中..." : "登录中...";
    }
    return isRegistering.value ? "注册" : "登录";
});

function toggleMode() {
    isRegistering.value = !isRegistering.value;
    message.value = "";
}

async function handleSubmit() {
    message.value = "";

    if (!form.username || !form.password) {
        messageType.value = "error";
        message.value = "用户名和密码不能为空";
        return;
    }

    loading.value = true;

    try {
        if (isRegistering.value) {
            await register(form.username, form.password, form.email);
            messageType.value = "success";
            message.value = "注册成功，请登录";
            isRegistering.value = false;
            form.password = "";
            return;
        }

        const token = await login(form.username, form.password);
        localStorage.setItem("token", token);
        emit("login-success");
    } catch (error) {
        messageType.value = "error";
        message.value = error.message || (isRegistering.value ? "注册失败" : "登录失败");
    } finally {
        loading.value = false;
    }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  color: #1f2933;
  background:
    radial-gradient(circle at 12% 18%, rgba(37, 99, 235, 0.10), transparent 30%),
    radial-gradient(circle at 86% 12%, rgba(234, 88, 12, 0.08), transparent 28%),
    linear-gradient(135deg, #eef4ff 0%, #f8fbff 48%, #fff7ed 100%);
  display: grid;
  place-items: center;
  padding: 32px 16px;
}

.auth-shell {
  width: min(960px, 100%);
  min-height: 560px;
  border: 1px solid #d9e2ec;
  border-radius: 10px;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  background: #ffffff;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.10);
}

.auth-visual {
  border-right: 1px solid #fed7aa;
  padding: 48px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  gap: 22px;
  background: #fff7ed;
}

.auth-visual img {
  width: min(280px, 80%);
  align-self: center;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 14px 36px rgba(124, 45, 18, 0.12);
}

.auth-brand {
  color: #c2410c;
  font-size: 28px;
  line-height: 1.2;
  font-weight: 800;
}

.auth-visual p {
  margin: 8px 0 0;
  color: #7c2d12;
  font-size: 15px;
}

.auth-card {
  padding: 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: #ffffff;
}

h1 {
  margin: 0 0 8px;
  color: #102a43;
  font-size: 30px;
  line-height: 1.2;
}

.muted {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.auth-form {
  margin-top: 20px;
  display: grid;
  gap: 12px;
}

.field {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field span {
  font-size: 14px;
  color: #52606d;
}

.field input {
  width: 100%;
  min-height: 40px;
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 8px 10px;
  font: inherit;
  color: #1f2933;
  background: #ffffff;
}

.field input:focus {
  outline: 2px solid rgba(234, 88, 12, 0.20);
  border-color: #ea580c;
}

.message {
  margin: 0;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.45;
}

.message.error {
  border: 1px solid #f5c2c7;
  color: #9f1239;
  background: #fff1f2;
}

.message.success {
  border: 1px solid #86efac;
  color: #166534;
  background: #f0fdf4;
}

.auth-actions {
  margin-top: 6px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.button {
  min-height: 40px;
  border: 1px solid #2563eb;
  border-radius: 6px;
  padding: 8px 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: #2563eb;
  font: inherit;
  cursor: pointer;
  transition: color 0.15s ease, background-color 0.15s ease, border-color 0.15s ease;
}

.button:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.button.secondary {
  border-color: #bcccdc;
  color: #1f2933;
  background: #ffffff;
}

.button.secondary:hover {
  border-color: #2563eb;
  color: #ffffff;
  background: #2563eb;
}

@media (max-width: 820px) {
  .auth-shell {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .auth-visual {
    border-right: 0;
    border-bottom: 1px solid #fed7aa;
    padding: 28px;
  }

  .auth-visual img {
    width: min(160px, 60%);
  }

  .auth-card {
    padding: 28px;
  }
}

@media (max-width: 460px) {
  .auth-actions {
    grid-template-columns: 1fr;
  }
}
</style>
