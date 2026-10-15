<script setup>
// 阶段二静态原型：角色继承（ROLE-05 ~ 09）。senior 继承 junior 的全部权限，允许多个上级，禁止成环。
import { ElMessage } from 'element-plus'
import LaterStageBanner from '../components/LaterStageBanner.vue'

defineProps({ embedded: { type: Boolean, default: false } })
const edges = [
  { senior: '公司领导', junior: '部门经理', note: '示例：领导拥有经理的全部权限' },
  { senior: '部门经理', junior: '基础办公', note: '自建公共能力角色' },
  { senior: '项目经理', junior: '基础办公', note: '多个上级共享同一下级（DAG）' },
  { senior: '普通员工', junior: '基础办公', note: '' }
]
const effective = [
  { code: 'oa:f007:read', scope: '全部', from: '直接授予' },
  { code: 'oa:f010:create', scope: '全部', from: '继承自 基础办公' },
  { code: 'hr:f046:edit', scope: '本部门', from: '继承自 部门经理' }
]
const notSaved = () => ElMessage.info('阶段二实现：后端检查不形成自环或间接环后保存')
</script>

<template>
  <div>
    <div v-if="!embedded" class="page-heading"><div><h2>角色继承</h2><p>上级角色自动拥有下级角色的全部权限。</p></div></div>
    <LaterStageBanner :stage="2" requirements="ROLE-05 ~ ROLE-09、AUTHZ-04、UC-05" />
    <div class="later-grid">
      <el-card>
        <template #header><b>继承关系（示意）</b></template>
        <div class="dag">
          <div class="dag-row"><span class="dag-node">公司领导</span></div>
          <div class="dag-arrow">↓</div>
          <div class="dag-row"><span class="dag-node">部门经理</span><span class="dag-node">项目经理</span><span class="dag-node">普通员工</span></div>
          <div class="dag-arrow">↓ ↓ ↓</div>
          <div class="dag-row"><span class="dag-node junior">基础办公（自建公共能力角色）</span></div>
        </div>
        <el-table :data="edges" size="small" class="top-gap">
          <el-table-column prop="senior" label="上级（senior）" />
          <el-table-column prop="junior" label="下级（junior）" />
          <el-table-column prop="note" label="说明" min-width="180" />
          <el-table-column label="操作" width="80"><template #default><el-button link type="danger" disabled>移除</el-button></template></el-table-column>
        </el-table>
        <div class="toolbar top-gap">
          <el-select placeholder="上级角色" disabled /><el-select placeholder="下级角色" disabled />
          <el-button type="primary" @click="notSaved">添加继承</el-button>
        </div>
        <el-alert type="error" :closable="false" show-icon title="示例：已有 A 继承 B、B 继承 C 时，设置“C 继承 A”会形成环，被拒绝，原有关系保持不变（ROLE-06）。" />
      </el-card>
      <el-card>
        <template #header><b>角色有效权限与来源（ROLE-07）</b></template>
        <el-table :data="effective" size="small">
          <el-table-column label="权限码"><template #default="{ row }"><code>{{ row.code }}</code></template></el-table-column>
          <el-table-column prop="scope" label="范围" width="80" />
          <el-table-column prop="from" label="来源" />
        </el-table>
        <p class="muted small">停用的角色不贡献权限，经过它的继承路径也断开（ROLE-08）。ROLE-09 的迁移在数据库侧完成，迁移前后有效权限快照一致。</p>
      </el-card>
    </div>
  </div>
</template>
