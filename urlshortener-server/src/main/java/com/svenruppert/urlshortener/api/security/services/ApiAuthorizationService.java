package com.svenruppert.urlshortener.api.security.services;

import eu.jsentinel.jcustos.authorization.api.AuthorizationService;
import eu.jsentinel.jcustos.authorization.api.permissions.HasPermissions;
import eu.jsentinel.jcustos.authorization.api.permissions.PermissionName;
import eu.jsentinel.jcustos.authorization.api.permissions.RolePermissionMapping;
import eu.jsentinel.jcustos.authorization.api.permissions.RolePermissionResolver;
import eu.jsentinel.jcustos.authorization.api.permissions.StaticRolePermissionMapping;
import eu.jsentinel.jcustos.authorization.api.roles.HasRoles;
import eu.jsentinel.jcustos.authorization.api.roles.RoleName;
import eu.jsentinel.jcustos.autoservice.api.JCustosAutoService;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.permissions.ApiPermission;
import com.svenruppert.urlshortener.api.security.roles.AuthorizationRole;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.svenruppert.urlshortener.api.security.permissions.ApiPermission.*;

/**
 * Role → permission table (operation ids from PRE-REBUILD-SCOPE.md).
 *
 * <ul>
 *   <li>ADMIN: every permission (incl. {@code link:read:all} bypassing
 *       the owner model in Stage G)</li>
 *   <li>USER: own-link management, statistics reads/exports and
 *       column preferences</li>
 * </ul>
 */
@JCustosAutoService(AuthorizationService.class)
public class ApiAuthorizationService
    implements AuthorizationService<ApiUser> {

  private static final Set<PermissionName> USER_PERMISSIONS = Set.of(
      CREATE_LINK.permissionName(),
      EDIT_LINK.permissionName(),
      DELETE_LINK.permissionName(),
      TOGGLE_ACTIVE.permissionName(),
      LIST_LINKS.permissionName(),
      LIST_LINKS_COUNT.permissionName(),
      BULK_CREATE_LINKS.permissionName(),
      BULK_VALIDATE_LINKS.permissionName(),
      STATISTICS_COUNT.permissionName(),
      STATISTICS_HOURLY.permissionName(),
      STATISTICS_DAILY.permissionName(),
      STATISTICS_TIMELINE.permissionName(),
      STATISTICS_EXPORT.permissionName(),
      PREFERENCES_COLUMNS.permissionName(),
      PREFERENCES_COLUMNS_BULK.permissionName(),
      PREFERENCES_COLUMNS_SINGLE.permissionName(),
      CAMPAIGN_MANAGE.permissionName());

  private static final Set<PermissionName> ADMIN_PERMISSIONS =
      EnumSet.allOf(ApiPermission.class).stream()
          .map(ApiPermission::permissionName)
          .collect(Collectors.toUnmodifiableSet());

  private static final RolePermissionMapping ROLE_PERMISSIONS = StaticRolePermissionMapping.builder()
      .put(roleName(AuthorizationRole.ADMIN), ADMIN_PERMISSIONS)
      .put(roleName(AuthorizationRole.USER), USER_PERMISSIONS)
      .build();

  @Override
  public HasRoles rolesFor(ApiUser subject) {
    Objects.requireNonNull(subject);
    List<RoleName> roles = subject.roles().stream()
        .map(ApiAuthorizationService::roleName)
        .toList();
    return () -> roles;
  }

  @Override
  public HasPermissions permissionsFor(ApiUser subject) {
    Objects.requireNonNull(subject);
    Set<RoleName> roles = subject.roles().stream()
        .map(ApiAuthorizationService::roleName)
        .collect(Collectors.toSet());
    Set<PermissionName> permissions =
        RolePermissionResolver.permissionsForRoles(roles, ROLE_PERMISSIONS);
    return () -> List.copyOf(permissions);
  }

  private static RoleName roleName(AuthorizationRole role) {
    return new RoleName(role.name());
  }
}
