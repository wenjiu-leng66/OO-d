<script setup>
// 资源目录（CAT-01 ~ 03，只读，由导入维护）：系统 > 模块 > 功能点 > 操作，关键词、系统、模块筛选和分页。
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api/mock'
import { systems, modules, actions, adminMapping, adminPermissions } from '../api/fixtures/catalog'

const tab = ref('catalog')
const loading = ref(false); const failed = ref(false)
const systemCode = ref(''); const moduleName = ref(''); const keyword = ref(''); const page = ref(1); const size = 12
const result = ref({ items: [], total: 0, stats: null })
const moduleOptions = computed(() => modules.filter(item => !systemCode.value || item.systemCode === systemCode.value))
const mappingRows = computed(() => Object.entries(adminMapping).flatMap(([id, item]) =>
  Object.entries(item.actions).map(([action, codes]) => ({ id, action: actions.find(a => a.code === action).label, sysCode: `sys:${id}:${action}`, codes, page: item.page, stage: item.stage }))))
async function load() {
  loading.value = true; failed.value = false
  try { result.value = await api.listCatalog({ systemCode: systemCode.value, module: moduleName.value, keyword: keyword.value, page: page.value, pageSize: size }) }
  catch { failed.value = true } finally { loading.value = false }
}
function search() { page.value = 1; load() }
watch(systemCode, () => { moduleName.value = ''; search() })
watch(moduleName, search)
onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading"><div><h2>资源目录</h2><p>只读，由《应用软件功能权限清单》导入维护。权限码格式 <code>系统码:fNNN:动作</code>，不随显示名称变化。</p></div></div>
    <div v-if="result.stats" class="stat-row">
      <el-card><b>{{ result.stats.systems }}</b><span>个系统</span></el-card>
      <el-card><b>{{ result.stats.modules }}</b><span>个模块</span></el-card>
      <el-card><b>{{ result.stats.functions }}</b><span>个功能点</span></el-card>
      <el-card><b>{{ result.stats.permissions }}</b><span>条权限（× 6 种操作）</span></el-card>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="功能点目录" name="catalog">
        <el-card class="filter-card"><div class="toolbar">
          <el-select v-model="systemCode" placeholder="全部系统" clearable filterable><el-option v-for="item in systems" :key="item.code" :label="`${item.name}（${item.code}）`" :value="item.code" /></el-select>
          <el-select v-model="moduleName" placeholder="全部模块" clearable filterable><el-option v-for="item in moduleOptions" :key="item.systemCode + item.name" :label="item.name" :value="item.name" /></el-select>
          <el-input v-model="keyword" placeholder="功能点名称、编号或权限码" clearable @keyup.enter="search" @clear="search" />
          <el-button type="primary" @click="search">查询</el-button>
        </div></el-card>
        <el-card class="table-card">
          <el-skeleton v-if="loading" :rows="6" animated />
          <el-result v-else-if="failed" icon="error" title="目录加载失败"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
          <el-empty v-else-if="!result.items.length" description="暂无匹配功能点" />
          <template v-else>
            <el-table :data="result.items" stripe size="small">
              <el-table-column prop="id" label="编号" width="70" />
              <el-table-column label="系统" min-width="150"><template #default="{ row }">{{ row.systemName }} <code class="muted">{{ row.systemCode }}</code></template></el-table-column>
              <el-table-column prop="module" label="模块" min-width="130" />
              <el-table-column label="功能点" min-width="150"><template #default="{ row }">{{ row.name }} <el-tag v-if="row.admin" size="small" type="warning" effect="plain">映射 rbac:*</el-tag></template></el-table-column>
              <el-table-column label="6 个操作的权限码" min-width="300"><template #default="{ row }"><div class="code-list"><code v-for="code in row.codes" :key="code">{{ code }}</code></div></template></el-table-column>
            </el-table>
            <div class="pagination"><el-pagination v-model:current-page="page" :page-size="size" :total="result.total" layout="total, prev, pager, next" @current-change="load" /></div>
          </template>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="管理后台映射（CAT-03）" name="mapping">
        <el-card>
          <el-alert class="block-gap" type="info" :closable="false" show-icon title="清单第 18 个系统“系统管理后台”中已实现的模块，授权映射为 rbac:* 权限；其余功能点做占位页。本表是原型提案，接口设计阶段写入契约。" />
          <el-table :data="mappingRows" size="small" stripe>
            <el-table-column label="清单权限码" min-width="150"><template #default="{ row }"><code>{{ row.sysCode }}</code></template></el-table-column>
            <el-table-column prop="action" label="操作" width="70" />
            <el-table-column label="映射为" min-width="260"><template #default="{ row }"><code v-for="code in row.codes" :key="code" class="tag-gap">{{ code }}</code></template></el-table-column>
            <el-table-column label="页面" min-width="160"><template #default="{ row }">{{ row.page }}{{ row.stage ? '（阶段三）' : '' }}</template></el-table-column>
          </el-table>
          <h4>rbac:* 权限说明</h4>
          <el-table :data="adminPermissions" size="small">
            <el-table-column label="权限码" min-width="180"><template #default="{ row }"><code>{{ row.code }}</code></template></el-table-column>
            <el-table-column prop="module" label="模块" min-width="120" />
            <el-table-column prop="name" label="说明" min-width="200" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
