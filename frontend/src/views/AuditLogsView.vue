<script setup>
// 阶段三静态原型：登录日志与授权变更日志（LOG-01 ~ 03）。只能新增，不提供修改和删除。
import { ref } from 'vue'
import { auth } from '../stores/auth'
import { hasPermission } from '../policy-adapter'
import LaterStageBanner from '../components/LaterStageBanner.vue'

const tab = ref('login')
const loginLogs = [
  { time: '2026-12-01 08:58:12', account: 'SG000013', ip: '10.0.0.21', result: '成功' },
  { time: '2026-12-01 08:57:40', account: 'SG000013', ip: '10.0.0.21', result: '失败（日志内记录：密码错误）' },
  { time: '2026-12-01 08:30:05', account: 'admin', ip: '10.0.0.2', result: '成功' }
]
const grantLogs = [
  { time: '2026-12-01 09:10:22', operator: 'admin', target: 'SG000013', action: '分配角色', detail: '+ 台账维护员' },
  { time: '2026-12-01 09:12:47', operator: 'admin', target: '角色 普通员工', action: '修改授权', detail: '+ oa:f010:create（全部）' },
  { time: '2026-12-01 09:20:03', operator: 'DEMO000100', target: 'DEMO000021', action: '撤销角色', detail: '- 普通员工' }
]
</script>

<template>
  <div>
    <div class="page-heading"><div><h2>日志审计</h2><p>按时间、操作人筛选和导出；日志只增不改。</p></div>
      <el-tooltip content="阶段三实现；需要 rbac:log:export"><span><el-button :disabled="true">导出{{ hasPermission(auth, 'rbac:log:export') ? '' : '（无权限）' }}</el-button></span></el-tooltip>
    </div>
    <LaterStageBanner :stage="3" requirements="LOG-01 ~ LOG-03" />
    <el-card>
      <div class="toolbar"><el-date-picker type="daterange" disabled start-placeholder="开始日期" end-placeholder="结束日期" /><el-input placeholder="操作人" disabled /></div>
      <el-tabs v-model="tab">
        <el-tab-pane label="登录日志" name="login">
          <el-table :data="loginLogs" size="small"><el-table-column prop="time" label="时间" width="170" /><el-table-column prop="account" label="账号" /><el-table-column prop="ip" label="IP" /><el-table-column prop="result" label="结果" min-width="200" /></el-table>
        </el-tab-pane>
        <el-tab-pane label="授权变更日志" name="grant">
          <el-table :data="grantLogs" size="small"><el-table-column prop="time" label="时间" width="170" /><el-table-column prop="operator" label="操作人" /><el-table-column prop="target" label="对象" /><el-table-column prop="action" label="操作" /><el-table-column prop="detail" label="内容" min-width="200" /></el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
