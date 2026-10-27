package com.municipal.rbac.procedural.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * HTTP 写入请求的白名单 DTO。Bean Validation 负责格式/长度，存在性、唯一性、范围及版本由 Service 校验。
 * 未知 JSON 字段由应用配置拒绝；ID 使用字符串以保持前后端契约一致。
 */
public final class Requests {
  private Requests() {}

  public record Login(
      @NotBlank @Size(max = 64) String account, @NotBlank @Size(max = 128) String password) {}

  public record Password(
      @NotBlank @Size(max = 128) String oldPassword,
      @NotBlank @Size(min = 8, max = 128) String newPassword) {}

  /**
   * employeeNo 只作为 username 的兼容别名；请求不接受 password、roleIds 或管理员标记，防止批量赋值越权。
   */
  public record CreateUser(
      @JsonAlias("employeeNo") @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String username,
      @NotBlank @Size(max = 32) String name,
      @Pattern(regexp = "[1-9][0-9]{0,9}") String departmentId,
      @Size(max = 32) String position,
      @Pattern(regexp = "(?:|[+0-9 -]{3,20})") String phone,
      @Email @Size(max = 64) String email) {}

  public record UpdateUser(
      @NotBlank @Size(max = 32) String name,
      @Pattern(regexp = "[1-9][0-9]{0,9}") String departmentId,
      @Size(max = 32) String position,
      @Pattern(regexp = "(?:|[+0-9 -]{3,20})") String phone,
      @Email @Size(max = 64) String email) {}

  public record Status(@NotNull @Pattern(regexp = "ENABLED|DISABLED") String status) {}

  /**
   * roleIds 是完整目标集合，空集合表示撤销全部允许撤销的角色；version 是读取该集合时的乐观版本。
   */
  public record Roles(
      @NotNull @Size(max = 1000)
          List<@NotBlank @Pattern(regexp = "[1-9][0-9]{0,9}") String> roleIds,
      @Min(1) int version) {}

  public record CreateRole(
      @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,31}") String code,
      @NotBlank @Size(max = 32) String name,
      @Size(max = 255) String description) {}

  public record UpdateRole(
      @NotBlank @Size(max = 32) String name, @Size(max = 255) String description) {}

  public record Enabled(@NotNull Boolean enabled) {}

  /**
   * scope 逐权限码保存，只有 ALL/DEPT/SELF 三种；权限码是否属于权威目录由授权服务再检查。
   */
  public record Grant(
      @NotBlank String code, @NotNull @Pattern(regexp = "ALL|DEPT|SELF") String scope) {}

  /**
   * grants 为全集合替换且逐项 @Valid，不能仅验证外层 List；重复码和 read 依赖由 Service 检查。
   */
  public record Grants(@NotNull @Size(max = 3000) List<@Valid Grant> grants, @Min(1) int version) {}
}
