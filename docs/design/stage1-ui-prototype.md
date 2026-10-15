# 第一阶段界面原型

状态：2026-10-15，成员 1 协助补齐，供第一阶段（10/27—10/29）验收“完整的界面原型”。前端主责仍为成员 2、成员 3。

需求来源：需求规格说明书 `docs/requirements/req-spec.md` 第 3 章、第 6.1 节页面清单、第 9 节“对现有原型的影响”（该文件在 PR #14 合并前位于 `docs/rbac-requirements` 分支）。本原型在成员 2 的 Vue 应用和成员 3 的权限判断模块上补齐，没有另起一套。

技术栈沿用 Vue 3 + JavaScript + Element Plus。老师板书的 TS + Tailwind 仍在确认（Q-14），确认前不迁移。

## 1. 运行与演示

```powershell
cd frontend
npm install
npm run dev
```

打开终端显示的地址（通常为 `http://localhost:5173`）。登录页下方列出演示身份，点击即可填入账号；登录后右上角“切换演示身份”可直接换人，不必退出。演示密码均为 `123456`，只用于本原型。

| 身份 | 账号 | 看点 |
| --- | --- | --- |
| 超级管理员 | `admin` | 18 个系统全部菜单；管理后台全部可用；超级管理员角色的授权只读 |
| 公司领导 | `SG000001` | 管理后台能打开但只读，新增、编辑、删除置灰（CAT-03） |
| 部门经理 | `SG000011` | 综合办公室。员工台账查看为全部、编辑限本部门 |
| 项目经理 | `SG000281` | 道路工程分公司第一项目经理部。台账编辑只限本项目部 |
| 普通员工 | `SG000013` | 没有系统管理后台；考勤管理只有查看、编辑且为“本人”；台账只看到自己 |
| 部门管理员 | `DEMO000100` | 道路工程分公司及下级的用户管理；只能分配业务角色 |
| 首次登录 | `DEMO000101` | 登录后只能进入修改密码页（LOGIN-05） |
| 无角色账号 | `DEMO000103` | 可以登录，菜单为空，提示“暂无权限，请联系管理员” |
| 停用账号 | `DEMO000102` | 登录提示与密码错误完全相同（LOGIN-01） |

账号名沿用需求规格验收标准中的账号，姓名一律为“演示××”，电话为 `1380000xxxx` / `139000000xx` 形式的占位号码，邮箱使用保留域名 `example.com`。组织为 16 个单元的合成子集，覆盖 5 种类型。

数据只保存在页面内存中：刷新页面恢复初始数据，但会话会按 `sessionStorage` 中记住的账号编号恢复（不保存密码）。

## 2. 页面清单

| 页面 | 路由 | 需求 | 访问要求 | 阶段 |
| --- | --- | --- | --- | --- |
| 登录 | `/login` | LOGIN-01 ~ 03 | 公开 | 一 |
| 修改密码（含首次登录强制修改） | `/password` | LOGIN-04、05 | 登录即可 | 一 |
| 门户首页 | `/portal` | PORTAL-01 | 登录即可 | 一 |
| 功能点占位页 | `/portal/f/:fid` | PORTAL-02 | 该功能点的 `read` | 一 |
| 员工台账（真实业务） | `/ledger` | PORTAL-04 ~ 06、UC-08 | `hr:f046:read` | 一 |
| 我的权限 | `/me/permissions` | PORTAL-03 | 登录即可 | 一 |
| 管理概览 | `/admin` | — | `rbac:user:read` | 一 |
| 用户管理（含分配角色） | `/admin/users` | USER-01 ~ 07、GRANT-01、03 | `rbac:user:read` | 一 |
| 组织架构（只读） | `/admin/departments` | DEPT-01 ~ 02 | `rbac:department:read` | 一 |
| 角色管理 | `/admin/roles` | ROLE-01 ~ 04 | `rbac:role:read` | 一 |
| 角色授权 | `/admin/grants` | GRANT-02、UC-04 | `rbac:role:read` + `rbac:permission:read`，保存需 `rbac:role:grant` | 一 |
| 资源目录（只读） | `/admin/catalog` | CAT-01 ~ 03 | `rbac:permission:read` | 一 |
| 角色继承 | `/admin/hierarchy`，角色管理“继承关系”页签 | ROLE-05 ~ 09 | `rbac:role:read` | 二，静态 |
| 职责约束 | `/admin/constraints`，角色管理“约束”页签 | ROLE-10 ~ 15 | `rbac:role:read` | 三，静态 |
| 激活角色 | `/session/roles` | LOGIN-07 | 登录即可 | 三，静态 |
| 日志审计 | `/admin/logs` | LOG-01 ~ 03 | `rbac:log:read` | 三，静态 |
| 403 / 404 | `/403`、未匹配地址 | AUTHZ-01 | 登录即可 | 一 |

后续阶段页面都带“后续阶段 · 静态原型”提示，数据为样例，按钮不保存。成员 2 早期的 `/dashboard`、`/users`、`/departments`、`/roles` 保留为跳转。

## 3. 权限与数据范围

- 权限码：清单功能点为 `系统码:fNNN:动作`（如 `hr:f046:edit`），系统短码和功能点编号来自 PR #13 的 `database/seeds/system_codes.csv`、`function_codes.csv`。前端目录由 `frontend/scripts/gen-catalog.mjs` 生成到 `frontend/src/api/fixtures/catalog-data.js`，共 18 个系统、115 个模块、359 个功能点、2154 条权限。
- 动作：`read / create / edit / approve / export / delete`，界面显示为 查看、新增、编辑、审批、导出、删除。
- 数据范围：`ALL / DEPT / SELF`（全部 / 本部门 / 本人）。本部门 = 用户所在部门及其全部下级部门。多个角色授予同一权限码时取最大范围，不同权限码的范围互不影响（GRANT-04）。
- 管理后台：清单“系统管理后台”中已实现模块的授权映射为 `rbac:*`（CAT-03），范围随原授权。映射表是原型提案，接口设计阶段写入契约：

| 清单功能点 | 映射 |
| --- | --- |
| f339 用户管理 | read → `rbac:user:read`；create → `rbac:user:create`；edit → `rbac:user:update` + `rbac:user:assign-role`；delete → `rbac:user:delete` |
| f340 角色管理 | read → `rbac:role:read`；create → `rbac:role:create`；edit → `rbac:role:update`；delete → `rbac:role:delete` |
| f341 权限配置 | read → `rbac:permission:read`；edit → `rbac:role:grant` |
| f342 组织架构管理 | read → `rbac:department:read` |
| f348、f349 登录 / 操作日志 | read → `rbac:log:read`；export → `rbac:log:export`（阶段三） |

`rbac:user:read`、`rbac:role:read`、`rbac:role:grant` 等沿用成员 3 设计和成员 6 API 草案中已有的码；`rbac:user:delete`、`rbac:role:create/update/delete`、`rbac:log:*` 为新增。

演示授权按规则合成，不是清单原值，只保证需求规格验收标准涉及的格子一致：部门经理对员工台账“查看 = 全部、编辑 = 本部门”；普通员工对员工台账、考勤管理“查看、编辑 = 本人”，对薪酬核算、福利管理、考核结果应用“查看 = 本人”；公司领导对系统管理后台只有查看，日志可导出。部门管理员在 PR #13 中没有授权，原型提议为 f339 查看/新增/编辑/删除 = 本部门、f340 查看 = 全部、f342 查看 = 本部门，待接口设计确认。

## 4. 模拟后端的规则

页面统一调用 `frontend/src/api/mock.js`。它按 认证 → 权限码 → 数据范围 → 目标对象 的顺序检查，返回带 `status`、`code` 的错误，便于以后换成真实 HTTP 客户端：

- 401：未登录、账号被停用后的下一次请求。
- 403：缺少权限码；目标在范围外（与目标不存在返回相同结果，不返回目标的任何信息）；首次登录未改密码。
- 409：版本冲突（用户角色、角色授权整组替换）、账号或角色保护规则、编码或工号重复。
- 400：参数错误，如员工台账提交电话、邮箱以外的字段，角色授权缺少同一功能点的“查看”。

每次切换页面都会重新拉取当前用户的权限，所以授权变更、角色停用、账号停用在下一次请求即生效（GRANT-05、USER-04）。

为了在单人演示中复现并发和失败，页面提供两个演示开关：“模拟另一管理员先保存”（服务端版本号 +1，再保存得到 409）和角色授权页的“模拟保存中途失败”（事务回滚，授权与保存前完全相同）。成员 2 的 `?demo=loading|empty|error` 状态模拟仍适用于各列表页。

## 5. 对成员 2、成员 3 文件的修改

| 文件 | 修改 | 原因 |
| --- | --- | --- |
| `src/authorization/rbac0-policy.js`（成员 3） | 新增 `widerScope`、`effectiveGrants`、`permissionScope`、`scopeCovers`，原有函数不变 | 数据范围（全部 / 本部门 / 本人）和多角色取最大范围，集中在一处计算（NFR-MAINT-02） |
| `tests/frontend/rbac0-policy.test.cjs`（成员 3） | 导出清单断言加入新函数 | 浏览器经典脚本暴露的 API 增加 |
| `src/policy-adapter.js`（成员 2） | 导出 `policy`、`permissionScope`；路由支持多个权限和按参数计算权限 | 角色授权页需要两个权限；功能点页的权限码随编号变化 |
| `src/api/mock.js`（成员 2） | 改为合成数据的模拟后端，删除 `accountType`、临时运维账号和“仅管理员可登录” | 需求规格第 9 节：所有启用账号都能登录门户，账号类别得到依据前不做；旧 Mock 的部门负责人用了普通人名，无法确认是否取自真实部门表，统一换成“演示××” |
| `src/stores/auth.js`、`src/router/index.js`、`src/App.vue`（成员 2） | 会话带数据范围和角色；首次登录强制改密码；门户菜单与管理后台菜单；切换演示身份 | LOGIN-05、PORTAL-01 |
| `LoginView`、`DashboardView`、`UsersView`、`DepartmentsView`（成员 2） | 登录改为通用提示和演示身份；管理首页去掉临时账号统计；用户管理按范围分页、编辑、启停用、重置密码、删除、分配角色带版本号；组织架构显示类型、负责人、人数和含下级人员 | USER-01 ~ 07、GRANT-01、03、DEPT-01 ~ 02 |
| `RolesView`（成员 2 预留给成员 3 的占位） | 实现角色列表与继承、约束页签 | ROLE-01 ~ 04 |

成员 3 的静态原型 `frontend/prototypes/member3-rbac0.*` 未改动，仍使用旧的合成权限码，作为交互设计记录保留。

## 6. 已知限制

- 只有模拟后端，没有连接两套 Java 后端，也没有 `/api` 代理；错误码、字段名待成员 6 的 OpenAPI 确定后对齐。已先对齐不依赖定稿的三处：超级管理员角色编码改为表结构的 `ADMIN`；分页参数和响应字段改为草案的 `page / pageSize`；分配角色时集合含重复项返回 400，不再静默去重（草案测试用例 API-013）。其余差异见 PR #16 的 `docs/requirements/api-gap-review.md`。
- 组织和员工是 16 个单元、39 人的合成子集，不是 111 个组织单元、10,000 人；授权是规则合成，不是清单原值。
- 演示开关（切换身份、模拟并发、模拟失败）只存在于原型，正式系统没有。
- 只做桌面浏览器；构建产物超过 500 kB 的提示来自 Element Plus 全量引入，未做按需加载。

## 7. 验证

```powershell
node --test tests/frontend/*.test.cjs   # 仓库根目录
npm run build                           # frontend 目录
```

`tests/frontend/mock-api.test.cjs` 按需求规格验收标准逐条检查模拟后端（登录、改密、菜单、占位页、员工台账范围、用户与角色管理、版本冲突、授权保存）；`tests/frontend/rbac0-scope.test.cjs` 检查范围合并与目标对象判断。
