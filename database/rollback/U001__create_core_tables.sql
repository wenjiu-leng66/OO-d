-- =============================================================
-- 回滚 V001__create_core_tables.sql: 删除视图 v_user_permission 和 10 张核心表, 数据一并删除。
-- 执行前先备份 (mysqldump), 备份文件不要提交。可重复执行。
--
-- 文件名沿用 Flyway 的撤销迁移格式 (U + 版本号)。小组以后若采用 Flyway, 其社区版不执行撤销迁移,
-- 所以本文件放在 migrations/ 之外, 不会被扫描到; 需要时用 mysql 客户端手工执行。
-- =============================================================

-- 先删依赖视图，再清理关联表和实体；MySQL DDL 会隐式提交，不能用 ROLLBACK 恢复这些表。
DROP VIEW IF EXISTS v_user_permission;

-- sys_dept 与 sys_user 互相引用, 临时关闭外键检查再按依赖倒序删除
SET FOREIGN_KEY_CHECKS = 0;
-- 外键检查只对当前连接临时关闭，处理部门/用户的循环引用；不是数据备份或业务回退机制。
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_action;
DROP TABLE IF EXISTS app_function;
DROP TABLE IF EXISTS app_module;
DROP TABLE IF EXISTS app_system;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_dept;
SET FOREIGN_KEY_CHECKS = 1;

-- 如果 V001 是由 Flyway 执行的, 还要删掉它的执行记录, 否则 Flyway 认为 V001 仍已应用:
-- DELETE FROM flyway_schema_history WHERE script = 'V001__create_core_tables.sql';
