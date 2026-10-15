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
-- -------------------------------------------------------------
CREATE TABLE sys_dept (
    id              INT          NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    name            VARCHAR(64)  NOT NULL COMMENT '部门全称, 如 道路工程分公司-财务部',
    short_name      VARCHAR(32)  NOT NULL COMMENT '部门简称, 如 财务部',
    parent_id       INT          NULL     COMMENT '上级部门ID, 总公司为NULL',
    dept_type       VARCHAR(16)  NOT NULL COMMENT 'HQ总公司/HQ_DEPT总部部门/BRANCH分公司/BRANCH_DEPT分公司部门/PROJECT项目经理部',
    level           TINYINT      NOT NULL COMMENT '层级: 1总公司 2总部部门/分公司 3分公司下属部门',
    manager_name    VARCHAR(32)  NULL     COMMENT '部门经理姓名(原始数据, 仅供核对)',
    manager_user_id INT          NULL     COMMENT '部门经理用户ID',
    sort_no         INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_name (name),
    KEY idx_dept_parent (parent_id),
    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id) REFERENCES sys_dept (id),
    CONSTRAINT ck_dept_type CHECK (dept_type IN ('HQ', 'HQ_DEPT', 'BRANCH', 'BRANCH_DEPT', 'PROJECT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '部门';

-- -------------------------------------------------------------
-- 2. 用户
-- password_hash 带算法前缀, 如 {pbkdf2@SpringSecurity_v5_8}..., 由 Spring Security 的
-- DelegatingPasswordEncoder 识别; 最终算法与后端统一后再定, 列宽足够容纳 BCrypt 等格式。
-- -------------------------------------------------------------
CREATE TABLE sys_user (
    id              INT          NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username        VARCHAR(32)  NOT NULL COMMENT '工号, 登录标识之一',
    password_hash   VARCHAR(255) NOT NULL COMMENT '带算法前缀的密码哈希, 不存明文',
    must_change_password TINYINT NOT NULL DEFAULT 1 COMMENT '1 下次登录必须修改密码 (导入和重置后), 0 已修改',
    real_name       VARCHAR(32)  NOT NULL COMMENT '姓名',
    dept_id         INT          NULL     COMMENT '所属部门',
    position        VARCHAR(32)  NULL     COMMENT '岗位/职务',
    gender          CHAR(1)      NULL     COMMENT 'M/F',
    phone           VARCHAR(20)  NULL     COMMENT '手机号(按文本存), 登录标识之一, 可为空, 不为空时唯一',
    email           VARCHAR(64)  NULL,
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    role_version    INT          NOT NULL DEFAULT 1 COMMENT '角色集合版本号, 整组替换角色时校验并加 1',
    last_login_at   DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_phone (phone),
    KEY idx_user_dept (dept_id),
    KEY idx_user_real_name (real_name),
    CONSTRAINT fk_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept (id),
    CONSTRAINT ck_user_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_user_must_change CHECK (must_change_password IN (0, 1)),
    CONSTRAINT ck_user_gender CHECK (gender IN ('M', 'F'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户(员工)';

-- 部门与用户互相引用: 两张表都建好后再补这条外键
ALTER TABLE sys_dept
    ADD CONSTRAINT fk_dept_manager FOREIGN KEY (manager_user_id) REFERENCES sys_user (id) ON DELETE SET NULL;

-- -------------------------------------------------------------
-- 3. 角色 (RBAC0: 平铺角色, 无继承; RBAC1 再加层级关系)
-- -------------------------------------------------------------
CREATE TABLE sys_role (
    id              INT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)  NOT NULL COMMENT '角色编码, 如 ADMIN',
    name            VARCHAR(32)  NOT NULL COMMENT '角色名称',
    description     VARCHAR(255) NULL,
    sort_no         INT          NOT NULL DEFAULT 0,
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    builtin         TINYINT      NOT NULL DEFAULT 0 COMMENT '1 内置角色(清单 5 个 + 部门管理员), 不能删除',
    permission_version INT       NOT NULL DEFAULT 1 COMMENT '角色授权版本号, 整组替换授权时校验并加 1',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code),
    CONSTRAINT ck_role_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_role_builtin CHECK (builtin IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

-- -------------------------------------------------------------
-- 4. 资源: 软件系统 > 功能模块 > 功能点
-- -------------------------------------------------------------
CREATE TABLE app_system (
    id              INT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(16)  NOT NULL COMMENT '系统短码, 权限码第一段, 如 hr',
    name            VARCHAR(64)  NOT NULL COMMENT '软件系统名称',
    sort_no         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_code (code),
    UNIQUE KEY uk_system_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '软件系统';

CREATE TABLE app_module (
    id              INT          NOT NULL AUTO_INCREMENT,
    system_id       INT          NOT NULL,
    name            VARCHAR(64)  NOT NULL COMMENT '功能模块名称(不同系统可重名, 如 供应商管理)',
    sort_no         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module (system_id, name),
    CONSTRAINT fk_module_system FOREIGN KEY (system_id) REFERENCES app_system (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '功能模块';

CREATE TABLE app_function (
    id              INT          NOT NULL AUTO_INCREMENT COMMENT '功能点编号, 由编号表固定, 清单行序变化时不变',
    module_id       INT          NOT NULL,
    code            VARCHAR(8)   NOT NULL COMMENT '功能点编号码, 权限码第二段, 如 f046',
    name            VARCHAR(64)  NOT NULL COMMENT '功能点名称',
    description     VARCHAR(255) NULL     COMMENT '功能说明',
    sort_no         INT          NOT NULL DEFAULT 0 COMMENT '清单中的显示顺序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_function_code (code),
    UNIQUE KEY uk_function (module_id, name),
    CONSTRAINT fk_function_module FOREIGN KEY (module_id) REFERENCES app_module (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '功能点';

-- -------------------------------------------------------------
-- 5. 操作 & 权限
-- -------------------------------------------------------------
CREATE TABLE sys_action (
    id              TINYINT      NOT NULL,
    code            VARCHAR(16)  NOT NULL COMMENT 'read/create/edit/approve/export/delete',
    name            VARCHAR(16)  NOT NULL COMMENT '查看/新增/编辑/审批/导出/删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_action_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作类型';

CREATE TABLE sys_permission (
    id              INT          NOT NULL AUTO_INCREMENT,
    function_id     INT          NOT NULL,
    action_id       TINYINT      NOT NULL,
    code            VARCHAR(64)  NOT NULL COMMENT '权限码 系统码:功能点编号:动作, 如 hr:f046:edit',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm (function_id, action_id),
    UNIQUE KEY uk_perm_code (code),
    CONSTRAINT fk_perm_function FOREIGN KEY (function_id) REFERENCES app_function (id) ON DELETE CASCADE,
    CONSTRAINT fk_perm_action   FOREIGN KEY (action_id)   REFERENCES sys_action (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '权限 = 功能点 x 操作';

-- -------------------------------------------------------------
-- 6. 关联表
-- -------------------------------------------------------------
CREATE TABLE sys_user_role (
    user_id         INT          NOT NULL,
    role_id         INT          NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    KEY idx_ur_role (role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户-角色';

CREATE TABLE sys_role_permission (
    role_id         INT          NOT NULL,
    permission_id   INT          NOT NULL,
    data_scope      VARCHAR(8)   NOT NULL DEFAULT 'ALL' COMMENT 'ALL全部数据 / DEPT本部门及全部下级部门 / SELF仅本人数据',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_rp_perm (permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id)       REFERENCES sys_role (id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE CASCADE,
    CONSTRAINT ck_rp_scope CHECK (data_scope IN ('ALL', 'DEPT', 'SELF'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-权限';

-- -------------------------------------------------------------
-- 7. 视图: 用户最终拥有的权限 (后端鉴权直接查这个视图)
--    只算启用的用户和启用的角色; 同一权限被多个角色授予时, 取最大范围 (ALL > DEPT > SELF)
-- -------------------------------------------------------------
CREATE VIEW v_user_permission AS
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
       ELT(MAX(FIELD(rp.data_scope, 'SELF', 'DEPT', 'ALL')), 'SELF', 'DEPT', 'ALL') AS data_scope
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
