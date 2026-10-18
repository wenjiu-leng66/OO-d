package com.municipal.rbac.procedural.service;

import com.municipal.rbac.procedural.mapper.DepartmentMapper;
import com.municipal.rbac.procedural.mapper.UserMapper;
import com.municipal.rbac.procedural.po.DepartmentPO;
import com.municipal.rbac.procedural.vo.DepartmentNodeVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 部门树查询过程：读 PO 列表后在内存组装树形 VO。 */
@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final AuthorizationService authorization;

    public DepartmentService(DepartmentMapper departmentMapper,
                             UserMapper userMapper,
                             AuthorizationService authorization) {
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.authorization = authorization;
    }

    public List<DepartmentNodeVO> tree(HttpSession session) {
        authorization.require(session, "rbac:department:read");
        List<DepartmentPO> rows = departmentMapper.findAll();
        Map<String, DepartmentPO> byId = new HashMap<>();
        Map<String, List<DepartmentPO>> byParent = new HashMap<>();
        for (DepartmentPO row : rows) {
            byId.put(row.id(), row);
            byParent.computeIfAbsent(row.parentId(), k -> new ArrayList<>()).add(row);
        }
        List<DepartmentNodeVO> roots = new ArrayList<>();
        for (DepartmentPO row : rows) {
            if (row.parentId() == null || !byId.containsKey(row.parentId())) {
                roots.add(build(row, byId, byParent));
            }
        }
        return roots;
    }

    private DepartmentNodeVO build(DepartmentPO row,
                                   Map<String, DepartmentPO> byId,
                                   Map<String, List<DepartmentPO>> byParent) {
        String parentName = (row.parentId() != null && byId.containsKey(row.parentId()))
                ? byId.get(row.parentId()).name() : null;
        String manager = (row.managerId() != null)
                ? userMapper.findById(row.managerId()).map(u -> u.name()).orElse(null) : null;
        int employeeCount = (int) departmentMapper.countEmployees(row.id());
        List<DepartmentNodeVO> children = byParent.getOrDefault(row.id(), List.of()).stream()
                .map(child -> build(child, byId, byParent))
                .toList();
        return new DepartmentNodeVO(row.id(), row.name(), manager, row.branch(),
                row.parentId(), parentName, employeeCount, children);
    }
}
