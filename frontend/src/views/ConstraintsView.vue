<script setup>
// 阶段三静态原型：职责约束（ROLE-10 ~ 15、GRANT-06）。样例使用自建能力角色，不声称来自清单。
import { ElMessage } from 'element-plus'
import LaterStageBanner from '../components/LaterStageBanner.vue'

defineProps({ embedded: { type: Boolean, default: false } })
const constraints = [
  { type: '静态互斥（SSD）', roles: '付款经办 ↔ 付款复核', param: '不能分给同一个人', enabled: true },
  { type: '动态互斥（DSD）', roles: '申请提交 ↔ 申请审批', param: '不能在同一会话同时激活', enabled: true },
  { type: '基数', roles: '超级管理员', param: '最多 1 人', enabled: true },
  { type: '基数', roles: '（每个用户）', param: '最多 5 个角色', enabled: false }
]
const notSaved = () => ElMessage.info('阶段三实现：保存前检查现有数据（含继承），已有人违反则拒绝并列出用户')
</script>

<template>
  <div>
    <div v-if="!embedded" class="page-heading"><div><h2>职责约束</h2><p>静态互斥、动态互斥与基数约束。</p></div></div>
    <LaterStageBanner :stage="3" requirements="ROLE-10 ~ ROLE-15、GRANT-06、UC-06" />
    <el-card>
      <div class="toolbar"><el-button type="primary" @click="notSaved">＋ 新增约束</el-button></div>
      <el-table :data="constraints" size="small">
        <el-table-column prop="type" label="类型" width="150" />
        <el-table-column prop="roles" label="角色" min-width="180" />
        <el-table-column prop="param" label="参数" min-width="180" />
        <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120"><template #default><el-button link disabled>编辑</el-button><el-button link disabled>停用</el-button></template></el-table-column>
      </el-table>
      <el-alert class="top-gap" type="error" :closable="false" show-icon title="示例拒绝：给已拥有“付款经办”的用户分配“付款复核”——违反静态互斥约束 #1，冲突角色：付款经办。" />
      <el-alert class="top-gap" type="warning" :closable="false" show-icon title="约束沿继承传递（ROLE-13）：B、C 互斥且 A 继承 B，则 A、C 也互斥。数据库触发器兜底静态互斥和基数（GRANT-06）。" />
    </el-card>
  </div>
</template>
