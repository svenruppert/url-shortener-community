package com.svenruppert.urlshortener.api.handler.admin.users;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.UserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.security.roles.AuthorizationRole;
import com.svenruppert.urlshortener.api.security.services.PasswordPreflight;
import com.svenruppert.urlshortener.api.security.services.VersionBumper;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.auth.CreateUserRequest;
import com.svenruppert.urlshortener.core.auth.UserSummary;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.EnumSet;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.jsonBodyOrBadRequest;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_USERS;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * User administration (PRE-REBUILD-SCOPE Kap. 2.7), one context at
 * {@code /api/users} dispatching by method + path:
 *
 * <ul>
 *   <li>{@code GET  /api/users} — list (never exposes hashes)</li>
 *   <li>{@code POST /api/users} — create ({username,password,roles})</li>
 *   <li>{@code DELETE /api/users/{id}} — delete</li>
 *   <li>{@code POST   /api/users/{id}/roles/{role}} — assign role</li>
 *   <li>{@code DELETE /api/users/{id}/roles/{role}} — revoke role</li>
 * </ul>
 *
 * <p>Role mutations bump the subject's jCustos version
 * ({@link VersionBumper#bump}) so any open Bearer token of that user
 * drifts on its next request (drift detection).
 */
@RequiresPermission("listUsers")
public class UsersHandler
    implements HttpHandler, HasLogger {

  private final AtomicLong sequence = new AtomicLong(2000);

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    String method = ex.getRequestMethod();
    String path = ex.getRequestURI().getPath();
    String rest = path.length() > PATH_ADMIN_USERS.length()
        ? path.substring(PATH_ADMIN_USERS.length())
        : "";
    String[] segments = rest.startsWith("/") ? rest.substring(1).split("/") : new String[0];

    try {
      if (segments.length == 0) {
        switch (method) {
          case "GET" -> list(ex);
          case "POST" -> create(ex);
          default -> ErrorResponses.methodNotAllowed(ex, "GET, POST");
        }
        return;
      }
      Long id = parseId(segments[0]);
      if (id == null) {
        ErrorResponses.badRequest(ex, "invalid user id");
        return;
      }
      // /api/users/{id}
      if (segments.length == 1) {
        if ("DELETE".equals(method)) {
          deleteUser(ex, id);
        } else {
          ErrorResponses.methodNotAllowed(ex, "DELETE");
        }
        return;
      }
      // /api/users/{id}/roles/{role}
      if (segments.length == 3 && "roles".equals(segments[1])) {
        roleMutation(ex, method, id, segments[2]);
        return;
      }
      ErrorResponses.notFound(ex, "unknown users sub-resource");
    } catch (RuntimeException e) {
      logger().warn("users handler failed - {}", e.toString());
      ErrorResponses.internalServerError(ex, "internal_error");
    }
  }

  private void list(HttpExchange ex)
      throws IOException {
    List<UserSummary> users = UserDirectoryProvider.directory().all()
        .sorted((a, b) -> Long.compare(a.id(), b.id()))
        .map(UsersHandler::toSummary)
        .toList();
    SuccessResponses.okJson(ex, JacksonJson.toJson(users));
  }

  private void create(HttpExchange ex)
      throws IOException {
    CreateUserRequest req = jsonBodyOrBadRequest(ex, CreateUserRequest.class);
    if (req == null) return;
    if (req == null || isNullOrBlank(req.username()) || isNullOrBlank(req.password())) {
      ErrorResponses.badRequest(ex, "username and password are required");
      return;
    }
    UserDirectory directory = UserDirectoryProvider.directory();
    if (directory.findByUsername(req.username()).isPresent()) {
      ErrorResponses.conflict(ex, "username_exists", "username already exists");
      return;
    }
    if (!PasswordPreflight.isAcceptable(req.password())) {
      ErrorResponses.invalidParameter(ex, "password is known to be compromised");
      return;
    }
    var roles = parseRoles(req.roles());
    if (roles == null) {
      ErrorResponses.badRequest(ex, "unknown_role");
      return;
    }
    ApiUser user = new ApiUser(sequence.incrementAndGet(), req.username(), roles);
    directory.addUser(req.username(), req.password(), user);
    logger().info("user '{}' (id={}) created with roles {}", req.username(), user.id(), roles);
    SuccessResponses.withStatusJson(ex, 201, JacksonJson.toJson(toSummary(user)));
  }

  private void deleteUser(HttpExchange ex, Long id)
      throws IOException {
    UserDirectory directory = UserDirectoryProvider.directory();
    Optional<ApiUser> user = directory.findById(id);
    if (user.isEmpty()) {
      ErrorResponses.notFound(ex, "user not found");
      return;
    }
    if (isLastAdmin(directory, user.get())) {
      ErrorResponses.conflict(ex, "last_admin_protected", "cannot delete the last administrator");
      return;
    }
    directory.deleteUser(id);
    VersionBumper.bump(user.get());
    SuccessResponses.noContent(ex);
  }

  private void roleMutation(HttpExchange ex, String method, Long id, String roleName)
      throws IOException {
    UserDirectory directory = UserDirectoryProvider.directory();
    Optional<ApiUser> user = directory.findById(id);
    if (user.isEmpty()) {
      ErrorResponses.notFound(ex, "user not found");
      return;
    }
    AuthorizationRole role = parseRole(roleName);
    if (role == null) {
      ErrorResponses.badRequest(ex, "unknown_role");
      return;
    }
    switch (method) {
      case "POST" -> {
        directory.assignRole(id, role);
        VersionBumper.bump(user.get());
        SuccessResponses.okJson(ex, JacksonJson.toJson(toSummary(directory.findById(id).orElseThrow())));
      }
      case "DELETE" -> {
        if (role == AuthorizationRole.ADMIN && isLastAdmin(directory, user.get())) {
          ErrorResponses.conflict(ex, "last_admin_protected", "cannot revoke ADMIN from the last administrator");
          return;
        }
        directory.revokeRole(id, role);
        VersionBumper.bump(user.get());
        SuccessResponses.okJson(ex, JacksonJson.toJson(toSummary(directory.findById(id).orElseThrow())));
      }
      default -> ErrorResponses.methodNotAllowed(ex, "POST, DELETE");
    }
  }

  private static boolean isLastAdmin(UserDirectory directory, ApiUser candidate) {
    if (!candidate.roles().contains(AuthorizationRole.ADMIN)) {
      return false;
    }
    long admins = directory.all()
        .filter(u -> u.roles().contains(AuthorizationRole.ADMIN))
        .count();
    return admins <= 1;
  }

  private static UserSummary toSummary(ApiUser user) {
    return new UserSummary(user.id(), user.name(),
        user.roles().stream().map(Enum::name).sorted().toList());
  }

  private static EnumSet<AuthorizationRole> parseRoles(List<String> names) {
    var roles = EnumSet.noneOf(AuthorizationRole.class);
    if (names == null || names.isEmpty()) {
      roles.add(AuthorizationRole.USER);
      return roles;
    }
    for (String n : names) {
      AuthorizationRole r = parseRole(n);
      if (r == null) return null;
      roles.add(r);
    }
    return roles;
  }

  private static AuthorizationRole parseRole(String name) {
    try {
      return AuthorizationRole.valueOf(name.toUpperCase());
    } catch (IllegalArgumentException | NullPointerException e) {
      return null;
    }
  }

  private static Long parseId(String s) {
    try {
      return Long.valueOf(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
