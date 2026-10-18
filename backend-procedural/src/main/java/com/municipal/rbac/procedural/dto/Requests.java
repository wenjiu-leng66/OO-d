package com.municipal.rbac.procedural.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class Requests {
  private Requests() {}

  public record Login(
      @NotBlank @Size(max = 64) String account, @NotBlank @Size(max = 128) String password) {}

  public record Password(
      @NotBlank @Size(max = 128) String oldPassword,
      @NotBlank @Size(min = 8, max = 128) String newPassword) {}

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

  public record Grant(
      @NotBlank String code, @NotNull @Pattern(regexp = "ALL|DEPT|SELF") String scope) {}

  public record Grants(@NotNull @Size(max = 3000) List<@Valid Grant> grants, @Min(1) int version) {}
}
