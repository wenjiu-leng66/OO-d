-- LOCAL H2 DIALECT ONLY. Mechanically adapted from database/migrations/V001__create_core_tables.sql. Production executes that authoritative migration.
-- =============================================================
-- V001 RBAC0 核心表: 10 张表 + 视图 v_user_permission
-- 主责: 成员 1。字段说明见 docs/database/data-dictionary.md。
-- 要求 MySQL 8.0.16+ (CHECK 约束生效), 小组候选 8.4 LTS。
-- 在已选好的空库中执行; 本文件不建库、不切换库、不删表。回滚见 database/rollback/U001__create_core_tables.sql。
--
-- RBAC0: 用户 -(多对多)- 角色 -(多对多)- 权限
-- 权限 = 功能点 x 操作 (查看/新增/编辑/审批/导出/删除), 权限码 系统码:功能点编号:动作, 如 hr:f046:edit
-- 功能点三级结构: 软件系统 > 功能模块 > 功能点 (来自《应用软件功能权限清单》)
-- =============================================================

-- -------------------------------------------------------------
-- 1. 组织架构
-- 自引用外键保证上级存在，但单根、无环、深度一致由导入器和应用校验，不由外键自动证明。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dept (
    id              INT          NOT NULL AUTO_INCREMENT ,
    name            VARCHAR(64)  NOT NULL ,
    short_name      VARCHAR(32)  NOT NULL ,
    parent_id       INT          NULL     ,
    dept_type       VARCHAR(16)  NOT NULL ,
    level           TINYINT      NOT NULL ,
    manager_name    VARCHAR(32)  NULL     ,
    manager_user_id INT          NULL     ,
    sort_no         INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (name),
    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id) REFERENCES sys_dept (id),
    CONSTRAINT ck_dept_type CHECK (dept_type IN ('HQ', 'HQ_DEPT', 'BRANCH', 'BRANCH_DEPT', 'PROJECT'))
);

-- -------------------------------------------------------------
-- 2. 用户
-- password_hash 带算法前缀, 如 {pbkdf2@SpringSecurity_v5_8}..., 由 Spring Security 的
-- DelegatingPasswordEncoder 识别; 当前新密码和导入密码统一 PBKDF2，列宽兼容其他带前缀格式。
-- 手机号 NULL 可出现多次，非空值受唯一键约束；空字符串应在应用/导入层归一为 NULL。
-- role_version 是用户角色集合的乐观锁版本，不代表账号资料版本或会话凭据。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id              INT          NOT NULL AUTO_INCREMENT ,
    username        VARCHAR(32)  NOT NULL ,
    password_hash   VARCHAR(255) NOT NULL ,
    must_change_password TINYINT NOT NULL DEFAULT 1 ,
    real_name       VARCHAR(32)  NOT NULL ,
    dept_id         INT          NULL     ,
    position        VARCHAR(32)  NULL     ,
    gender          CHAR(1)      NULL     ,
    phone           VARCHAR(20)  NULL     ,
    email           VARCHAR(64)  NULL,
    status          TINYINT      NOT NULL DEFAULT 1 ,
    role_version    INT          NOT NULL DEFAULT 1 ,
    last_login_at   DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (username),
    UNIQUE (phone),
    CONSTRAINT fk_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept (id),
    CONSTRAINT ck_user_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_user_must_change CHECK (must_change_password IN (0, 1)),
    CONSTRAINT ck_user_gender CHECK (gender IN ('M', 'F'))
);

-- 部门与用户互相引用: 两张表都建好后再补这条外键
ALTER TABLE sys_dept
    ADD CONSTRAINT IF NOT EXISTS fk_dept_manager FOREIGN KEY (manager_user_id) REFERENCES sys_user (id) ON DELETE SET NULL;

-- -------------------------------------------------------------
-- 3. 角色 (RBAC0: 平铺角色, 无继承; RBAC1 再加层级关系)
-- permission_version 与一次整组角色授权替换绑定；管理员/内置角色保护由服务层执行。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
    id              INT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)  NOT NULL ,
    name            VARCHAR(32)  NOT NULL ,
    description     VARCHAR(255) NULL,
    sort_no         INT          NOT NULL DEFAULT 0,
    status          TINYINT      NOT NULL DEFAULT 1 ,
    builtin         TINYINT      NOT NULL DEFAULT 0 ,
    permission_version INT       NOT NULL DEFAULT 1 ,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (code),
    CONSTRAINT ck_role_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_role_builtin CHECK (builtin IN (0, 1))
);

-- -------------------------------------------------------------
-- 4. 资源: 软件系统 > 功能模块 > 功能点
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_system (
    id              INT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(16)  NOT NULL ,
    name            VARCHAR(64)  NOT NULL ,
    sort_no         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (code),
    UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS app_module (
    id              INT          NOT NULL AUTO_INCREMENT,
    system_id       INT          NOT NULL,
    name            VARCHAR(64)  NOT NULL ,
    sort_no         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (system_id, name),
    CONSTRAINT fk_module_system FOREIGN KEY (system_id) REFERENCES app_system (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS app_function (
    id              INT          NOT NULL AUTO_INCREMENT ,
    module_id       INT          NOT NULL,
    code            VARCHAR(8)   NOT NULL ,
    name            VARCHAR(64)  NOT NULL ,
    description     VARCHAR(255) NULL     ,
    sort_no         INT          NOT NULL DEFAULT 0 ,
    PRIMARY KEY (id),
    UNIQUE (code),
    UNIQUE (module_id, name),
    CONSTRAINT fk_function_module FOREIGN KEY (module_id) REFERENCES app_module (id) ON DELETE CASCADE
);

-- -------------------------------------------------------------
-- 5. 操作 & 权限
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_action (
    id              TINYINT      NOT NULL,
    code            VARCHAR(16)  NOT NULL ,
    name            VARCHAR(16)  NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id              INT          NOT NULL AUTO_INCREMENT,
    function_id     INT          NOT NULL,
    action_id       TINYINT      NOT NULL,
    code            VARCHAR(64)  NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (function_id, action_id),
    UNIQUE (code),
    CONSTRAINT fk_perm_function FOREIGN KEY (function_id) REFERENCES app_function (id) ON DELETE CASCADE,
    CONSTRAINT fk_perm_action   FOREIGN KEY (action_id)   REFERENCES sys_action (id)
);

-- -------------------------------------------------------------
-- 6. 关联表
-- 联合主键禁止重复授角/授权；删除用户或角色时关联记录级联清理，避免孤立关系。
-- data_scope 属于“角色-权限”关系，同一动作在不同角色上可以具有不同范围。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id         INT          NOT NULL,
    role_id         INT          NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id         INT          NOT NULL,
    permission_id   INT          NOT NULL,
    data_scope      VARCHAR(8)   NOT NULL DEFAULT 'ALL' ,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id)       REFERENCES sys_role (id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE CASCADE,
    CONSTRAINT ck_rp_scope CHECK (data_scope IN ('ALL', 'DEPT', 'SELF'))
);

-- -------------------------------------------------------------
-- 7. 视图: 用户清单权限的有效范围汇总，便于数据库核对
--    只算启用的用户和启用的角色; 同一权限被多个角色授予时, 取最大范围 (ALL > DEPT > SELF)
--    GROUP BY 保证范围只在同一用户/同一权限码内合并，不把某动作的 ALL 扩大给另一个动作。
--    应用层还须处理首次改密、后台派生权限和具体目标范围；此视图不是完整业务鉴权入口。
-- -------------------------------------------------------------
CREATE OR REPLACE VIEW v_user_permission AS
SELECT u.id                                   AS user_id,
       u.username,
       p.id                                   AS permission_id,
       p.code                                 AS permission_code,
       s.name                                 AS system_name,
       m.name                                 AS module_name,
       f.id                                   AS function_id,
       f.name                                 AS function_name,
       a.code                                 AS action_code,
       a.name                                 AS action_name,
       CASE MAX(CASE rp.data_scope WHEN 'ALL' THEN 3 WHEN 'DEPT' THEN 2 ELSE 1 END) WHEN 3 THEN 'ALL' WHEN 2 THEN 'DEPT' ELSE 'SELF' END AS data_scope
FROM sys_user u
         JOIN sys_user_role ur       ON ur.user_id = u.id
         JOIN sys_role r             ON r.id = ur.role_id AND r.status = 1
         JOIN sys_role_permission rp ON rp.role_id = r.id
         JOIN sys_permission p       ON p.id = rp.permission_id
         JOIN sys_action a           ON a.id = p.action_id
         JOIN app_function f         ON f.id = p.function_id
         JOIN app_module m           ON m.id = f.module_id
         JOIN app_system s           ON s.id = m.system_id
WHERE u.status = 1
GROUP BY u.id, u.username, p.id, p.code, s.name, m.name, f.id, f.name, a.code, a.name;
