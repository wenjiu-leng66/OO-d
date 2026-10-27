package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.map;

import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.CatalogMapper;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * 按当前有效 read 权限组装门户菜单和动作元数据；此处返回按钮可用性不能代替业务接口鉴权。
 */
@Service
public class PortalService {
  private final CatalogMapper catalog;
  private final AuthorizationService authorization;

  public PortalService(CatalogMapper catalog, AuthorizationService authorization) {
    this.catalog = catalog;
    this.authorization = authorization;
  }

  /**
   * 只有可查看的功能点才创建其所属系统/模块，避免返回空菜单或泄露无权功能入口。
   * LinkedHashMap 保留目录顺序，同一模块只追加一次。
   */
  public List<Map<String, Object>> menu(HttpSession session) {
    var u = authorization.ready(session);
    var p = authorization.effectivePermissions(u.id());
    Map<String, Map<String, Object>> systems = new LinkedHashMap<>(),
        modules = new LinkedHashMap<>();
    for (var f : catalog.functions())
      if (p.scopes().containsKey(f.get("systemCode") + ":" + f.get("functionCode") + ":read")) {
        String sys = (String) f.get("systemCode"), module = (String) f.get("moduleId");
        var s =
            systems.computeIfAbsent(
                sys,
                k ->
                    map(
                        "systemCode",
                        sys,
                        "name",
                        f.get("systemName"),
                        "modules",
                        new ArrayList<Map<String, Object>>()));
        var m = modules.get(module);
        if (m == null) {
          m =
              map(
                  "moduleId",
                  module,
                  "name",
                  f.get("moduleName"),
                  "functions",
                  new ArrayList<Map<String, Object>>());
          modules.put(module, m);
          list(s, "modules").add(m);
        }
        list(m, "functions")
            .add(
                map(
                    "functionCode",
                    f.get("functionCode"),
                    "name",
                    f.get("name"),
                    "description",
                    f.get("description")));
      }
    return List.copyOf(systems.values());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> list(Map<String, Object> item, String field) {
    return (List<Map<String, Object>>) item.get(field);
  }

  /**
   * 直接访问功能详情也重新检查 read；未知功能与无权功能统一拒绝。
   * 六动作分别返回 enabled 与 scope；一期只有 f046 员工台账有本服务对应的真实业务写接口，其余标记 placeholder。
   */
  public Map<String, Object> function(HttpSession session, String code) {
    authorization.ready(session);
    var f = catalog.function(code).orElseThrow(() -> ApiException.forbidden("未知功能点或没有查看权限。"));
    String prefix = f.get("systemCode") + ":" + code + ":";
    authorization.require(session, prefix + "read");
    var p = authorization.effectivePermissions(authorization.current(session).id());
    Map<String, Object> result = new LinkedHashMap<>(f);
    result.put(
        "actions",
        catalog.actions().stream()
            .map(
                a ->
                    map(
                        "action",
                        a.get("code"),
                        "label",
                        a.get("label"),
                        "enabled",
                        p.scopes().containsKey(prefix + a.get("code")),
                        "scope",
                        p.scopes().get(prefix + a.get("code"))))
            .toList());
    result.put("placeholder", !"f046".equals(code));
    return result;
  }
}
