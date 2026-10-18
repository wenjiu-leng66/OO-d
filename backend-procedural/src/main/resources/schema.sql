-- RBAC0 面向功能后端表结构（H2/MySQL 兼容，均使用 VARCHAR 主键与字符串 ID）
CREATE TABLE IF NOT EXISTS departments (
    id           VARCHAR(64)  PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    branch       VARCHAR(255) NOT NULL,
    parent_id    VARCHAR(64),
    manager_id   VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(64)  PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    employee_no   VARCHAR(64)  NOT NULL UNIQUE,
    phone         VARCHAR(32)  UNIQUE,
    department_id VARCHAR(64),
    position      VARCHAR(128),
    account_type  VARCHAR(32)  NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_version  INT          NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS roles (
    id                 VARCHAR(64)  PRIMARY KEY,
    code               VARCHAR(128) NOT NULL UNIQUE,
    name               VARCHAR(255) NOT NULL,
    description        VARCHAR(512),
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    permission_version INT          NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS permissions (
    code          VARCHAR(128) PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    system_id     VARCHAR(64),
    system_name   VARCHAR(255),
    module_id     VARCHAR(64),
    module_name   VARCHAR(255),
    resource_code VARCHAR(128),
    resource_name VARCHAR(255),
    action        VARCHAR(32),
    scope_options VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS resources (
    id        VARCHAR(64)  PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    type      VARCHAR(32)  NOT NULL,
    parent_id VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(64)  NOT NULL,
    role_id VARCHAR(64)  NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id         VARCHAR(64)  NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    scope           VARCHAR(32)  NOT NULL DEFAULT 'all',
    PRIMARY KEY (role_id, permission_code)
);
