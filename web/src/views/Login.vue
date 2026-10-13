<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { post, errorMessage } from '../api';
import { saveSession, notify } from '../state';
import type { Session } from '../types';
const route = useRoute(),
  router = useRouter(),
  register = ref(false),
  username = ref(''),
  password = ref(''),
  confirm = ref(''),
  busy = ref(false),
  error = ref('');
async function submit() {
  error.value = '';
  if (register.value && password.value !== confirm.value) {
    error.value = '两次输入的密码不一致。';
    return;
  }
  busy.value = true;
  try {
    const value = await post<Session>(`/auth/${register.value ? 'register' : 'login'}`, {
      username: username.value.trim(),
      password: password.value,
    });
    saveSession(value);
    notify(register.value ? '注册成功，欢迎加入。' : '登录成功。');
    const next = String(route.query.redirect || '/');
    await router.replace(next.startsWith('/') && !next.startsWith('//') ? next : '/');
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="auth-layout">
    <div class="auth-story">
      <p class="eyebrow">WELCOME TO YOUR STORE</p>
      <h1>
        每一次选购，
        <br />
        都值得被认真对待。
      </h1>
      <p>一个账户，管理你的商品选购与订单进度。</p>
      <div class="auth-story-line">
        <span>01</span>
        发现商品
      </div>
      <div class="auth-story-line">
        <span>02</span>
        确认订单
      </div>
      <div class="auth-story-line">
        <span>03</span>
        跟进交付
      </div>
    </div>
    <section class="auth-panel">
      <p class="eyebrow">你的专属账户</p>
      <h2>{{ register ? '创建新账户' : '欢迎回来' }}</h2>
      <p class="muted">
        {{ register ? '填写以下信息，即可开始选购。' : '登录后查看订单，继续你的选购。' }}
      </p>
      <form @submit.prevent="submit">
        <label for="username">用户名</label>
        <input
          id="username"
          v-model="username"
          name="username"
          autocomplete="username"
          required
          minlength="3"
          maxlength="64"
          pattern="[A-Za-z0-9_.-]+"
          placeholder="字母、数字、下划线或短横线"
        />
        <label for="password">密码</label>
        <input
          id="password"
          v-model="password"
          name="password"
          type="password"
          :autocomplete="register ? 'new-password' : 'current-password'"
          required
          :minlength="register ? 10 : 1"
          maxlength="128"
          :placeholder="register ? '至少 10 个字符' : '输入密码'"
        />
        <template v-if="register">
          <label for="password-confirm">确认密码</label>
          <input
            id="password-confirm"
            v-model="confirm"
            type="password"
            autocomplete="new-password"
            required
            minlength="10"
            maxlength="128"
            placeholder="再次输入密码"
          />
        </template>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <button class="button full" :disabled="busy">
          {{ busy ? '请稍候…' : register ? '注册并登录' : '登录' }}
        </button>
      </form>
      <p class="auth-switch">
        {{ register ? '已有账户？' : '还没有账户？' }}
        <button
          class="text-button"
          :disabled="busy"
          @click="
            register = !register;
            error = '';
          "
        >
          {{ register ? '立即登录' : '注册账户' }}
        </button>
      </p>
    </section>
  </div>
</template>
