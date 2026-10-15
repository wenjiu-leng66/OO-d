<script setup>
// 阶段三静态原型：激活角色（LOGIN-07、UC-07、AUTHZ-05）。阶段一登录时激活全部已分配且启用的角色。
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { auth } from '../stores/auth'
import LaterStageBanner from '../components/LaterStageBanner.vue'

const active = ref(auth.roles.filter(role => role.enabled).map(role => role.id))
const notSaved = () => ElMessage.info('阶段三实现：后端检查所选角色都已分配、彼此不动态互斥后更新会话')
</script>

<template>
  <div class="narrow-page">
    <div class="page-heading"><div><h2>激活角色</h2><p>选择本次会话中生效的角色；鉴权只计算激活的角色。</p></div></div>
    <LaterStageBanner :stage="3" requirements="LOGIN-07、ROLE-11、AUTHZ-05" />
    <el-card>
      <template #header><b>我的角色（当前：阶段一默认全部激活）</b></template>
      <el-checkbox-group v-model="active" class="role-check-grid">
        <el-checkbox v-for="role in auth.roles" :key="role.id" :value="role.id" :disabled="!role.enabled" border>{{ role.name }}{{ role.enabled ? '' : '（已停用）' }}</el-checkbox>
        <el-checkbox value="demo-submit" disabled border>申请提交（示例）</el-checkbox>
        <el-checkbox value="demo-approve" disabled border>申请审批（示例）</el-checkbox>
      </el-checkbox-group>
      <el-empty v-if="!auth.roles.length" description="未分配角色" :image-size="60" />
      <el-alert class="top-gap" type="warning" :closable="false" show-icon title="示例：“申请提交”与“申请审批”动态互斥，同时勾选时提示“只能激活其中一个”，激活集合保持不变。" />
      <div class="top-gap"><el-button type="primary" @click="notSaved">应用到本次会话</el-button></div>
    </el-card>
  </div>
</template>
