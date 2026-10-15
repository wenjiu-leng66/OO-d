<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auth, login } from '../stores/auth'
import { api, DEMO_PASSWORD } from '../api/mock'

const form = reactive({ account: 'admin', password: DEMO_PASSWORD }); const loading = ref(false); const error = ref('')
const router = useRouter(); const route = useRoute()
const identities = api.demo.identities
async function submit() {
  loading.value = true; error.value = ''
  try {
    await login(form.account, form.password)
    ElMessage.success('登录成功')
    router.push(auth.mustChangePassword ? '/password' : route.query.redirect || '/portal')
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function pick(item) { form.account = item.username; form.password = DEMO_PASSWORD; error.value = '' }
</script>

<template>
  <div class="login-page">
    <div class="login-backdrop"><span class="orb orb-one" /><span class="orb orb-two" /><span class="grid-lines" /></div>
    <div class="login-layout">
      <section class="login-intro">
        <div class="intro-mark">市</div>
        <p class="eyebrow">MUNICIPAL ACCESS PLATFORM</p>
        <h1>市政统一<br><strong>权限管理平台</strong></h1>
        <p class="intro-copy">一次登录进入门户。菜单、按钮和数据范围都按你的角色授权显示，后端对每次操作再做检查。</p>
        <div class="intro-points"><span>● 所有启用账号都能登录门户</span><span>● 菜单按“查看”权限生成</span><span>● 数据范围：全部 / 本部门 / 本人</span></div>
      </section>
      <el-card class="login-card" shadow="always">
        <div class="login-card-heading"><span class="card-kicker">欢迎回来</span><h2>登录</h2><p>使用工号或手机号登录</p></div>
        <el-form :model="form" @submit.prevent="submit" size="large">
          <el-form-item label="登录账号"><el-input v-model="form.account" autocomplete="username" placeholder="请输入工号或手机号"><template #prefix>◉</template></el-input></el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码"><template #prefix>◆</template></el-input></el-form-item>
          <el-alert v-if="error" class="login-error" :title="error" type="error" :closable="false" show-icon />
          <el-button type="primary" native-type="submit" :loading="loading" class="full-width login-button">登录 <span class="arrow">→</span></el-button>
        </el-form>
        <div class="identity-picker">
          <div class="identity-picker-title"><span>演示身份</span><small>合成数据 · 密码均为 <code>{{ DEMO_PASSWORD }}</code></small></div>
          <button v-for="item in identities" :key="item.username" type="button" :class="['identity-chip', { active: form.account === item.username }]" :title="item.note" @click="pick(item)">
            <b>{{ item.label }}</b><code>{{ item.username }}</code>
          </button>
        </div>
        <p class="login-footer">账号不存在、密码错误、账号停用时提示相同 · 停用账号可试 DEMO000102</p>
      </el-card>
    </div>
  </div>
</template>
