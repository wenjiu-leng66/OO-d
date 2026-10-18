<script setup>
// 修改密码（LOGIN-04）；首次登录或被重置密码后只能进入本页（LOGIN-05）。
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auth, changePassword } from '../stores/auth'

const router = useRouter()
const formRef = ref(); const saving = ref(false); const error = ref('')
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 8, max: 64, message: '新密码须为 8–64 位', trigger: 'blur' }],
  confirm: [{ validator: (_rule, value, callback) => value === form.newPassword ? callback() : callback(new Error('两次输入的新密码不一致')), trigger: 'blur' }]
}
async function submit() {
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true; error.value = ''
  try {
    await changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码已修改，下次请使用新密码登录')
    router.push('/portal')
  } catch (e) { error.value = e.message } finally { saving.value = false }
}
</script>

<template>
  <div class="narrow-page">
    <div class="page-heading"><div><h2>修改密码</h2><p>修改后旧密码立即失效。</p></div></div>
    <el-alert v-if="auth.mustChangePassword" class="block-gap" type="warning" :closable="false" show-icon
      title="首次登录或密码已被管理员重置：修改密码之前不能使用其他功能。" />
    <el-card>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent="submit">
        <el-form-item label="原密码" prop="oldPassword"><el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" placeholder="8–64 位，不能与原密码相同" /></el-form-item>
        <el-form-item label="确认新密码" prop="confirm"><el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-alert v-if="error" class="block-gap" :title="error" type="error" :closable="false" show-icon />
        <el-form-item><el-button type="primary" native-type="submit" :loading="saving">保存新密码</el-button></el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
