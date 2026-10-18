package com.municipal.rbac.procedural.service;

import com.municipal.rbac.procedural.dto.CreateUserRequest;
import com.municipal.rbac.procedural.dto.ReplaceRolesRequest;
import com.municipal.rbac.procedural.dto.UpdateUserStatusRequest;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.DepartmentMapper;
import com.municipal.rbac.procedural.mapper.RoleMapper;
import com.municipal.rbac.procedural.mapper.UserMapper;
import com.municipal.rbac.procedural.po.DepartmentPO;
import com.municipal.rbac.procedural.po.RolePO;
import com.municipal.rbac.procedural.po.UserPO;
import com.municipal.rbac.procedural.po.UserRolePO;
import com.municipal.rbac.procedural.vo.PageVO;
import com.municipal.rbac.procedural.vo.UserDetailVO;
import com.municipal.rbac.procedural.vo.UserRolesVO;
import com.municipal.rbac.procedural.vo.UserVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理过程：分页查询、详情、创建、状态变更、删除与用户角色替换。
 * Service 负责把 PO + 部门字典组装为 VO，Mapper 保持只读表行。
 */
@Service
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ACCOUNT_TYPES = Set.of("employee", "temporary-ops");
    private static final Set<String> STATUS_VALUES = Set.of("active", "inactive");
    private static final String SUPER_ADMIN_CODE = "super-admin";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorization;

    public UserService(UserMapper userMapper,
                       RoleMapper roleMapper,
                       DepartmentMapper departmentMapper,
                       PasswordEncoder passwordEncoder,
                       AuthorizationService authorization) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.departmentMapper = departmentMapper;
        this.passwordEncoder = passwordEncoder;
        this.authorization = authorization;
    }

    public PageVO<UserVO> list(HttpSession session, String keyword, String departmentId,
                               String accountType, int page, int pageSize) {
        authorization.require(session, "rbac:user:read");
        int size = clamp(pageSize);
        int offset = (Math.max(page, 1) - 1) * size;
        Map<String, DepartmentPO> departments = departmentDict();
        List<UserVO> items = userMapper.findPage(keyword, departmentId, accountType, offset, size)
                .stream().map(user -> toUserVO(user, departments)).toList();
        long total = userMapper.count(keyword, departmentId, accountType);
        return new PageVO<>(items, Math.max(page, 1), size, total);
    }

    public UserDetailVO detail(HttpSession session, String id) {
        authorization.require(session, "rbac:user:read");
        UserPO user = userMapper.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在。"));
        return buildDetail(user);
    }

    public UserRolesVO userRoles(HttpSession session, String id) {
        authorization.require(session, "rbac:user:read");
        userMapper.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在。"));
        List<String> roleIds = userMapper.findUserRoleRows(id).stream().map(UserRolePO::roleId).toList();
        return new UserRolesVO(id, roleIds, userMapper.roleVersion(id));
    }

    /** 创建用户：校验账号类型/部门/唯一性与角色集合后写入，返回 201 详情。 */
    @Transactional
    public UserDetailVO create(HttpSession session, CreateUserRequest request) {
        authorization.require(session, "rbac:user:create");

        if (!ACCOUNT_TYPES.contains(request.accountType().trim())) {
            throw ApiException.badRequest("INVALID_ARGUMENT",
                    "accountType 只允许 employee 或 temporary-ops。");
        }
        DepartmentPO department = departmentMapper.findById(request.departmentId().trim())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "部门不存在。",
                        Map.of("departmentId", request.departmentId())));
        String employeeNo = request.employeeNo().trim();
        if (userMapper.findByEmployeeNo(employeeNo).isPresent()) {
            throw ApiException.badRequest("DUPLICATE_ITEM", "员工号已存在。", Map.of("employeeNo", employeeNo));
        }
        String phone = normalize(request.phone());
        if (phone != null && userMapper.findByPhone(phone).isPresent()) {
            throw ApiException.badRequest("DUPLICATE_ITEM", "手机号已存在。", Map.of("phone", phone));
        }
        List<String> roleIds = request.roleIds() == null ? List.of() : request.roleIds();
        if (new HashSet<>(roleIds).size() != roleIds.size()) {
            throw ApiException.badRequest("DUPLICATE_ITEM", "roleIds 中存在重复元素。");
        }
        for (String roleId : roleIds) {
            RolePO role = roleMapper.findById(roleId)
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_ROLE", "存在未知角色。",
                            Map.of("roleId", roleId)));
            if (SUPER_ADMIN_CODE.equals(role.code())) {
                throw ApiException.forbidden("PROTECTED_ROLE", "不允许通过接口分配超级管理员角色。");
            }
        }

        String id = "u-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserPO user = new UserPO(id, request.name().trim(), employeeNo, phone, department.id(),
                normalize(request.position()), request.accountType().trim(), "active",
                passwordEncoder.encode(request.initialPassword()), 1);
        userMapper.insert(user);
        for (String roleId : roleIds) {
            userMapper.insertUserRole(id, roleId);
        }
        return buildDetail(user);
    }

    /** 变更用户状态：本人与超级管理员账号受保护。 */
    public UserVO updateStatus(HttpSession session, String id, UpdateUserStatusRequest request) {
        authorization.require(session, "rbac:user:update");
        String status = request.status().trim();
        if (!STATUS_VALUES.contains(status)) {
            throw ApiException.badRequest("INVALID_ARGUMENT", "status 只允许 active 或 inactive。");
        }
        requireUser(id);
        String currentUserId = authorization.requireUserId(session);
        if (id.equals(currentUserId)) {
            throw ApiException.forbidden("PROTECTED_ACCOUNT", "不能修改自己账号的状态。");
        }
        if (userMapper.hasRoleCode(id, SUPER_ADMIN_CODE)) {
            throw ApiException.forbidden("PROTECTED_ACCOUNT", "不能修改超级管理员账号的状态。");
        }
        userMapper.updateStatus(id, status);
        UserPO updated = requireUser(id);
        return toUserVO(updated, departmentDict());
    }

    /** 物理删除用户：先清理角色关联；本人与超级管理员账号受保护。 */
    @Transactional
    public void delete(HttpSession session, String id) {
        authorization.require(session, "rbac:user:delete");
        requireUser(id);
        String currentUserId = authorization.requireUserId(session);
        if (id.equals(currentUserId)) {
            throw ApiException.forbidden("PROTECTED_ACCOUNT", "不能删除自己的账号。");
        }
        if (userMapper.hasRoleCode(id, SUPER_ADMIN_CODE)) {
            throw ApiException.forbidden("PROTECTED_ACCOUNT", "不能删除超级管理员账号。");
        }
        userMapper.deleteUserRoles(id);
        userMapper.deleteById(id);
    }

    @Transactional
    public UserRolesVO replaceRoles(HttpSession session, String id, ReplaceRolesRequest request) {
        authorization.require(session, "rbac:user:assign-role");
        requireUser(id);

        if (new HashSet<>(request.roleIds()).size() != request.roleIds().size()) {
            throw ApiException.badRequest("DUPLICATE_ITEM", "roleIds 中存在重复元素。");
        }
        for (String roleId : request.roleIds()) {
            if (!roleMapper.exists(roleId)) {
                throw ApiException.badRequest("UNKNOWN_ROLE", "存在未知角色。", Map.of("roleId", roleId));
            }
        }

        int current = userMapper.roleVersion(id);
        if (request.version() != current) {
            throw ApiException.conflict("VERSION_CONFLICT", "授权已被其他人修改，请重新读取并复核。",
                    Map.of("expectedVersion", request.version(), "currentVersion", current));
        }

        int next = current + 1;
        userMapper.replaceRoles(id, request.roleIds(), next);
        return new UserRolesVO(id, request.roleIds(), next);
    }

    private UserPO requireUser(String id) {
        return userMapper.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在。"));
    }

    private UserDetailVO buildDetail(UserPO user) {
        DepartmentPO department = user.departmentId() == null ? null
                : departmentMapper.findById(user.departmentId()).orElse(null);
        List<String> roles = userMapper.findEnabledRoleIds(user.id()).stream()
                .map(roleMapper::findById)
                .flatMap(java.util.Optional::stream)
                .map(RolePO::name)
                .toList();
        List<String> permissionCodes = authorization.effectivePermissions(user.id()).permissionCodes();
        return new UserDetailVO(
                user.id(), user.name(), user.employeeNo(), user.departmentId(),
                department == null ? null : department.name(),
                department == null ? null : department.branch(),
                user.position(), user.status(), user.accountType(), user.phone(),
                roles, permissionCodes);
    }

    private Map<String, DepartmentPO> departmentDict() {
        return departmentMapper.findAll().stream()
                .collect(Collectors.toMap(DepartmentPO::id, Function.identity()));
    }

    private UserVO toUserVO(UserPO user, Map<String, DepartmentPO> departments) {
        DepartmentPO department = user.departmentId() == null ? null : departments.get(user.departmentId());
        return new UserVO(
                user.id(), user.name(), user.employeeNo(), user.departmentId(),
                department == null ? null : department.name(),
                department == null ? null : department.branch(),
                user.position(), user.status(), user.accountType());
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int clamp(int pageSize) {
        if (pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
