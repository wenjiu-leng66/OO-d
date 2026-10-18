package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.service.DepartmentService;
import com.municipal.rbac.procedural.vo.DepartmentNodeVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 部门组织树。 */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public List<DepartmentNodeVO> tree(HttpSession session) {
        return departmentService.tree(session);
    }
}
