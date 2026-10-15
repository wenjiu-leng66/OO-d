<script setup>
// 后续阶段页面的统一提示：静态原型，数据为合成样例，按钮不保存。
defineProps({ stage: { type: Number, required: true }, requirements: { type: String, default: '' } })
</script>

<template>
  <el-alert class="later-stage" type="warning" :closable="false" show-icon>
    <template #title>后续阶段 · 阶段{{ stage === 2 ? '二（RBAC1 角色层级）' : '三（约束、会话与日志）' }} · 静态原型</template>
    本页只展示界面结构，数据为合成样例，操作不会保存。{{ requirements ? `对应需求：${requirements}。` : '' }}
  </el-alert>
</template>
