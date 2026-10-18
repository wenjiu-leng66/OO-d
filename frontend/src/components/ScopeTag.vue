<script setup>
// 数据范围标签：全部 / 本部门 / 本人；没有权限时显示“无权限”。
import { computed } from 'vue'
import { scopeLabel } from '../api/fixtures/catalog'

const props = defineProps({ scope: { type: String, default: null }, size: { type: String, default: 'small' } })
const type = computed(() => ({ ALL: 'success', DEPT: 'warning', SELF: 'primary' })[props.scope] || 'info')
</script>

<template>
  <el-tag :type="type" :size="size" effect="plain" disable-transitions>{{ scope ? scopeLabel(scope) : '无权限' }}</el-tag>
</template>
