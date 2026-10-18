-- =============================================================
-- 导入后核对 (只读)。数量应与 import_excel.py 输出的核对报告 data/raw/seed_data_report.txt 一致;
-- 标注"返回空 = 正常"的查询有结果时, 说明数据有问题。规则见 docs/database/import-rules.md。
-- mysql -uroot -p --default-character-set=utf8mb4 <库名> < database/seeds/verify_import.sql
-- =============================================================

-- 1. 各表行数 (对应报告里的部门、系统/模块/功能点、权限、角色授权、用户数)
SELECT 'sys_dept' AS tbl, COUNT(*) AS n FROM sys_dept
UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user
UNION ALL SELECT 'sys_role', COUNT(*) FROM sys_role
UNION ALL SELECT 'sys_user_role', COUNT(*) FROM sys_user_role
UNION ALL SELECT 'app_system', COUNT(*) FROM app_system
UNION ALL SELECT 'app_module', COUNT(*) FROM app_module
UNION ALL SELECT 'app_function', COUNT(*) FROM app_function
UNION ALL SELECT 'sys_action', COUNT(*) FROM sys_action
UNION ALL SELECT 'sys_permission', COUNT(*) FROM sys_permission
UNION ALL SELECT 'sys_role_permission', COUNT(*) FROM sys_role_permission;

-- 2. 每个角色的人数和授权数 (对应报告里的"角色分配", 以及 DEPT、SELF 授权条数)
SELECT r.code,
       (SELECT COUNT(*) FROM sys_user_role ur WHERE ur.role_id = r.id)                                AS users,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id)                          AS grants,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.data_scope = 'DEPT') AS dept_grants,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.data_scope = 'SELF') AS self_grants
FROM sys_role r
ORDER BY r.sort_no;

-- 3. 填了负责人但没关联到用户 ID 的部门 (对应报告里的 [待核对], 由管理员在系统里指定)
SELECT id, name FROM sys_dept WHERE manager_name IS NOT NULL AND manager_user_id IS NULL;

-- 4. 没有部门的用户 (返回空 = 正常; admin 除外)
SELECT id, username FROM sys_user WHERE dept_id IS NULL AND username <> 'admin';

-- 5. 部门树: 每个部门都能从根节点走到, 且 level 与实际深度一致 (返回空 = 正常; 有环的部门走不到)
WITH RECURSIVE tree AS (
    SELECT id, 1 AS depth FROM sys_dept WHERE parent_id IS NULL
    UNION ALL
    SELECT d.id, t.depth + 1 FROM sys_dept d JOIN tree t ON d.parent_id = t.id WHERE t.depth < 10
)
SELECT d.id, d.name, d.level, t.depth
FROM sys_dept d LEFT JOIN tree t ON t.id = d.id
WHERE t.id IS NULL OR t.depth <> d.level;

-- 6. 每个功能点都有全部动作的权限; 权限码 = 系统码:功能点编号:动作, 权限 ID 由编号和动作算出 (返回空 = 正常)
SELECT f.id, f.name, COUNT(p.id) AS n
FROM app_function f LEFT JOIN sys_permission p ON p.function_id = f.id
GROUP BY f.id, f.name
HAVING COUNT(p.id) <> (SELECT COUNT(*) FROM sys_action);

SELECT p.id, p.code
FROM sys_permission p
         JOIN sys_action a   ON a.id = p.action_id
         JOIN app_function f ON f.id = p.function_id
         JOIN app_module m   ON m.id = f.module_id
         JOIN app_system s   ON s.id = m.system_id
WHERE p.code <> CONCAT(s.code, ':', f.code, ':', a.code)
   OR f.code <> CONCAT('f', IF(f.id < 1000, LPAD(f.id, 3, '0'), f.id))
   OR p.id <> (f.id - 1) * (SELECT COUNT(*) FROM sys_action) + a.id;

-- 7. 导入的账号首次登录都必须修改密码 (导入后应返回空)
SELECT id, username FROM sys_user WHERE must_change_password <> 1;

-- 8. 从库里还原清单的一行, 与 Excel 对照 (改 WHERE 里的功能点名称抽查其他行)
--    范围不是 ALL 的动作在后面标出, 如 查看/编辑(DEPT)
SELECT f.name AS function_name, r.name AS role_name,
       GROUP_CONCAT(CONCAT(a.name, IF(rp.data_scope = 'ALL', '', CONCAT('(', rp.data_scope, ')')))
                    ORDER BY a.id SEPARATOR '/') AS actions
FROM app_function f
         JOIN sys_permission p       ON p.function_id = f.id
         JOIN sys_action a           ON a.id = p.action_id
         JOIN sys_role_permission rp ON rp.permission_id = p.id
         JOIN sys_role r             ON r.id = rp.role_id
WHERE f.name = '员工台账管理'
GROUP BY f.name, r.id, r.name
ORDER BY r.id;

-- 9. 重复导入核对: 业务数据指纹, 不含密码哈希 (盐每次随机)。同一份源表导入两次, 结果应完全相同
SELECT (SELECT BIT_XOR(CRC32(CONCAT_WS('|', id, name, short_name, IFNULL(parent_id, ''), dept_type, level,
                                       IFNULL(manager_name, ''), IFNULL(manager_user_id, ''))))
        FROM sys_dept)                                                                           AS dept_fp,
       (SELECT BIT_XOR(CRC32(CONCAT_WS('|', id, username, real_name, IFNULL(dept_id, ''), IFNULL(position, ''),
                                       IFNULL(gender, ''), IFNULL(phone, ''), IFNULL(email, ''), status)))
        FROM sys_user)                                                                           AS user_fp,
       (SELECT BIT_XOR(CRC32(CONCAT_WS('|', user_id, role_id))) FROM sys_user_role)              AS user_role_fp,
       (SELECT BIT_XOR(CRC32(CONCAT_WS('|', function_id, action_id, code))) FROM sys_permission) AS perm_fp,
       (SELECT BIT_XOR(CRC32(CONCAT_WS('|', role_id, permission_id, data_scope)))
        FROM sys_role_permission)                                                                AS grant_fp;
