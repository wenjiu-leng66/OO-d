# 前端

主责：成员 2、成员 3。一套 Vue 3 + Element Plus + Vue Router + Vite 应用，成员 2 搭建公共结构、登录、用户与部门，成员 3 提供角色权限原型和权限判断模块。第一阶段的完整界面原型在此基础上补齐，说明见 [第一阶段界面原型](../docs/design/stage1-ui-prototype.md)。

当前前端使用合成数据和模拟后端（`src/api/mock.js`），尚未连接真实后端或数据库。

## 第一阶段界面原型

### 本地运行

在 `frontend/` 目录执行：

```powershell
npm install
npm run dev
```

访问终端输出的地址，通常为 `http://localhost:5173`。依赖版本由 `package-lock.json` 锁定。

### 演示身份

登录页下方可直接选择演示身份，登录后右上角“切换演示身份”可随时换人。演示密码均为 `123456`，只用于本原型。

| 身份 | 账号 | 说明 |
| --- | --- | --- |
| 超级管理员 | `admin` | 全部功能，管理全部组织 |
| 公司领导 | `SG000001` | 门户按授权使用；管理后台只读 |
| 部门经理 | `SG000011` | 综合办公室；查看为全部，管理类操作限本部门 |
| 项目经理 | `SG000281` | 道路工程分公司第一项目经理部 |
| 普通员工 | `SG000013` | 综合办公室；人力资源系统部分功能点限本人 |
| 部门管理员 | `DEMO000100` | 管理道路工程分公司及下级的用户，只能分配业务角色 |
| 首次登录 | `DEMO000101` | 登录后必须先修改密码 |
| 无角色账号 | `DEMO000103` | 可以登录，菜单为空 |
| 停用账号 | `DEMO000102` | 不能登录，提示与密码错误相同 |

所有姓名、电话、邮箱都是合成数据。不要在原型中输入真实员工信息或真实密码。数据只保存在页面内存中，刷新后恢复初始数据；会话按 `sessionStorage` 中的账号编号恢复，不保存密码。

### 页面

| 区域 | 页面 |
| --- | --- |
| 门户 | 首页（按“查看”权限生成 系统 > 模块 > 功能点 菜单）、功能点占位页（6 个操作按钮与数据范围）、员工台账（真实业务）、我的权限、修改密码 |
| 管理后台 | 管理概览、用户管理（含分配角色）、组织架构、角色管理、角色授权、资源目录 |
| 后续阶段（静态原型） | 角色继承（阶段二）、职责约束、激活角色、日志审计（阶段三） |
| 公共 | 登录、403、404 |

路由、需求编号、权限码格式、`rbac:*` 映射和对成员 2、3 文件的修改见 [第一阶段界面原型](../docs/design/stage1-ui-prototype.md)。

### 状态模拟

登录后在列表页地址后加 `?demo=loading`、`?demo=empty` 或 `?demo=error`，可分别查看加载中、空数据、请求失败状态，例如 `/admin/users?demo=error`。用户管理的“分配角色”和角色授权页提供“模拟另一管理员先保存”（得到 409 冲突），角色授权页提供“模拟保存中途失败”（授权保持不变）。

### 测试

```powershell
node --test tests/frontend/*.test.cjs   # 仓库根目录，无需安装依赖
npm run build                           # frontend 目录
```

资源目录由成员 1 的种子文件生成：`node scripts/gen-catalog.mjs [种子目录]`（默认 `../database/seeds`）。

## 成员 3 的第一阶段交付

- [打开交互原型](prototypes/member3-rbac0.html)：角色授权、资源目录、用户角色分配、导航和按钮权限演示。下载仓库后双击 HTML 即可使用，不调用真实后端。
- [原型操作说明](prototypes/README.md)：演示入口、状态模拟与范围。
- [设计与成员衔接](../docs/design/member3-rbac0-design.md)：页面规则、权限码及后续阶段边界。
- [接口使用方提案](../api/proposals/member3-rbac0-consumer-needs.md)：供成员 4、6 统一接口约定，不替代权威 OpenAPI。
- [成员 3 解释文档](../docs/guides/member3-rbac0-explained.md)：逐步解释完成内容与如何演示。

权限判断核心位于 [rbac0-policy.js](src/authorization/rbac0-policy.js)。在仓库根目录运行 `node --test tests/frontend/rbac0-policy.test.cjs` 验证拒绝、角色并集与路由判定；无需 npm 安装依赖。第一阶段原型在其中加入了数据范围的合并与检查（`effectiveGrants`、`scopeCovers` 等），原有函数行为不变，测试见 `tests/frontend/rbac0-scope.test.cjs`。

原型使用明确标记的合成数据，保存仅影响本次浏览器页面，刷新或重置恢复。正式应用应从后端获取身份及有效权限，不能信任本地角色集合来做服务器授权。

待团队确认后初始化 Vue 3 或已经熟悉的 React；统一 Node.js、包管理器、锁文件和 UI 规范。两位前端在同一个项目中协作，不分别建立两个前端应用。

| 目录 | 用途 |
| --- | --- |
| `src/api/` | 统一 HTTP 客户端与接口调用 |
| `src/components/` | 可复用组件 |
| `src/views/` | 登录、用户、角色、层级、约束等页面 |
| `src/router/` | 页面路由与导航守卫 |
| `src/stores/` | 当前用户、激活角色、界面状态 |
| `src/assets/` | 可提交的静态资源 |
| `src/styles/` | 公共样式和规范 |

遵守 [接口约定](https://github.com/wenjiu-leng66/RBAC-Permission-System/blob/main/docs/standards/api-contract.md)。开发时通过 `/api` 代理连接所选后端。前端隐藏按钮不代替后端授权，切换后端时明确会话隔离。

## 成员 2 当前实现进度

上述初始化说明为项目准备阶段约定。当前成员 2 已在同一 `frontend/` 目录中初始化 Vue 3 + Element Plus + Vue Router + Vite，使用 npm 和 `package-lock.json` 管理依赖。

当前通过 mock API 演示，尚未配置 `/api` 开发代理。正式后端联调时按接口约定接入。

Node.js 版本、公共 UI 规范、公共组件、路由元数据和页面责任边界仍需团队确认。

## 公共前端规范

正式前端采用 Vue 3 和 Element Plus。

页面状态使用以下 Element Plus 组件：

| 场景 | 组件 |
| --- | --- |
| 加载中 | `el-skeleton` |
| 空数据 | `el-empty` |
| 请求失败 | `el-result` 和重试按钮 |
| 表单校验 | `el-form` |
| 列表和分页 | `el-table`、`el-pagination` |
| 操作提示 | `ElMessage` |
| 新增和详情 | `el-dialog`、`el-drawer` |

目录约定：

| 目录或文件 | 用途 |
| --- | --- |
| `src/api/` | API 客户端、模拟接口和适配 |
| `src/authorization/` | RBAC0 权限判断模块 |
| `src/components/` | 可复用组件预留目录 |
| `src/views/` | 登录、用户、部门、角色等页面 |
| `src/router/` | 页面路由和导航守卫 |
| `src/stores/` | 当前用户和会话状态 |
| `src/assets/` | 可提交的静态资源 |
| `src/styles/` | 公共样式 |
| `src/App.vue` | 公共应用布局 |
| `src/main.js` | Vue 应用入口 |
| `prototypes/` | 成员 3 的独立静态原型 |

## 后端接入与当前限制

遵守 [接口约定](https://github.com/wenjiu-leng66/RBAC-Permission-System/blob/main/docs/standards/api-contract.md)。

开发时通过 `/api` 代理连接所选后端。当前 Vite 配置尚未配置正式 `/api` 代理。

前端隐藏菜单、按钮以及路由检查不能替代后端授权。

正式后端需要实现：

- 密码加盐哈希和认证；
- 数据库持久化；
- 员工号和手机号唯一约束；
- 每次请求的权限和部门范围校验；
- 权限取消即时生效；
- 权限校验日志；
- 会话失效和账号停用处理。

## 历史说明

成员 2 早期原型中的账号类别（员工 / 临时运维）、E001 等演示账号、“仅超级管理员和部门管理员可以登录”，以及成员 3 样例 `member3Fixtures`，已按需求规格第 9 节移除：所有启用账号都能登录门户，账号类别在得到依据前不做。成员 3 的静态原型 `prototypes/` 未改动。
