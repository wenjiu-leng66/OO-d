package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.*;

import com.municipal.rbac.procedural.mapper.CatalogMapper;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * 授权目录只读查询：系统 → 模块 → 功能点 → 动作。目录查看不等于拥有其中每项业务权限。
 */
@Service
public class CatalogService {
  private final CatalogMapper catalog;
  private final AuthorizationService authorization;

  public CatalogService(CatalogMapper catalog, AuthorizationService authorization) {
    this.catalog = catalog;
    this.authorization = authorization;
  }

  /**
   * 以稳定系统码/功能码拼接动作权限码，分页在筛选完成后进行；stats 是完整目录规模，不是筛选结果数。
   */
  public Map<String, Object> functions(
      HttpSession session, String system, String module, String keyword, int page, int size) {
    authorization.require(session, "rbac:permission:read");
    UserService.paging(page, size);
    var actions = catalog.actions();
    var result =
        catalog.functions().stream()
            .filter(f -> system == null || f.get("systemCode").equals(system))
            .filter(f -> module == null || f.get("moduleId").equals(module))
            .filter(
                f ->
                    keyword == null
                        || UserService.contains((String) f.get("name"), keyword)
                        || UserService.contains((String) f.get("functionCode"), keyword)
                        || UserService.contains((String) f.get("moduleName"), keyword))
            .map(
                f -> {
                  Map<String, Object> row = new LinkedHashMap<>(f);
                  row.put(
                      "permissions",
                      actions.stream()
                          .map(
                              a ->
                                  map(
                                      "action",
                                      a.get("code"),
                                      "code",
                                      f.get("systemCode")
                                          + ":"
                                          + f.get("functionCode")
                                          + ":"
                                          + a.get("code")))
                          .toList());
                  return row;
                })
            .toList();
    var response = page(result, page, size);
    response.put("stats", catalog.stats());
    return response;
  }

  /**
   * 展开功能点和动作的组合，供授权选择器使用；不能接受客户端自造权限码进入授权表。
   */
  public Map<String, Object> permissions(
      HttpSession session, String system, String module, String keyword, int page, int size) {
    authorization.require(session, "rbac:permission:read");
    UserService.paging(page, size);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (var f : catalog.functions())
      if ((system == null || f.get("systemCode").equals(system))
          && (module == null || f.get("moduleId").equals(module)))
        for (var a : catalog.actions()) {
          String code = f.get("systemCode") + ":" + f.get("functionCode") + ":" + a.get("code");
          if (keyword == null
              || code.contains(keyword)
              || UserService.contains((String) f.get("name"), keyword))
            rows.add(
                map(
                    "code",
                    code,
                    "functionCode",
                    f.get("functionCode"),
                    "functionName",
                    f.get("name"),
                    "systemCode",
                    f.get("systemCode"),
                    "systemName",
                    f.get("systemName"),
                    "moduleId",
                    f.get("moduleId"),
                    "moduleName",
                    f.get("moduleName"),
                    "action",
                    a.get("code")));
        }
    return page(rows, page, size);
  }

  /**
   * 构建带类型前缀的资源标识，避免系统与模块 ID 碰撞；仅返回指定父节点的一层，供前端懒加载。
   */
  public Map<String, Object> resources(HttpSession session, String parent, int page, int size) {
    authorization.require(session, "rbac:permission:read");
    UserService.paging(page, size);
    Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
    for (var f : catalog.functions()) {
      String sys = (String) f.get("systemCode"),
          module = (String) f.get("moduleId"),
          fn = (String) f.get("functionCode");
      rows.putIfAbsent(
          "system:" + sys,
          map(
              "id",
              "system:" + sys,
              "name",
              f.get("systemName"),
              "type",
              "system",
              "parentId",
              null,
              "hasChildren",
              true));
      rows.putIfAbsent(
          "module:" + module,
          map(
              "id",
              "module:" + module,
              "name",
              f.get("moduleName"),
              "type",
              "module",
              "parentId",
              "system:" + sys,
              "hasChildren",
              true));
      rows.put(
          fn,
          map(
              "id",
              fn,
              "name",
              f.get("name"),
              "type",
              "function",
              "parentId",
              "module:" + module,
              "hasChildren",
              false));
    }
    return page(
        rows.values().stream().filter(r -> Objects.equals(r.get("parentId"), parent)).toList(),
        page,
        size);
  }
}
