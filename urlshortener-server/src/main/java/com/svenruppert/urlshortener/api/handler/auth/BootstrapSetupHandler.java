package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import eu.jsentinel.jcustos.bootstrap.CreateInitialAdminCommand;
import eu.jsentinel.jcustos.bootstrap.InitialAdminCreationResult;
import com.svenruppert.urlshortener.api.security.bootstrap.BootstrapWiring;
import com.svenruppert.urlshortener.api.security.services.PasswordPreflight;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;

import java.io.IOException;
import java.util.Map;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * POST /api/setup — token-gated creation of the first admin
 * (PRE-REBUILD-SCOPE Kap. 2.8). Public + rate-limited by the
 * framework; lifts the bootstrap-required guard on success.
 *
 * <p>Body: {@code {"token":"…","username":"…","password":"…"}}
 * (optional {@code name}, {@code email}).
 */
public class BootstrapSetupHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePost(ex)) return;

    BootstrapWiring wiring = BootstrapWiring.instance();
    if (!wiring.stateService().bootstrapRequired()) {
      ErrorResponses.conflict(ex, "already initialised");
      return;
    }

    Map<String, String> body = JsonUtils.parseJson(readBody(ex.getRequestBody()));
    String token = body.get("token");
    String username = body.get("username");
    String password = body.get("password");
    if (isNullOrBlank(token) || isNullOrBlank(username) || isNullOrBlank(password)) {
      ErrorResponses.badRequest(ex, "token, username and password are required");
      return;
    }
    if (!PasswordPreflight.isAcceptable(password)) {
      logger().info("bootstrap admin password rejected as compromised");
      ErrorResponses.withStatus(ex, HttpStatus.UNPROCESSABLE_CONTENT.code(),
          "password is known to be compromised; choose a different one");
      return;
    }

    InitialAdminCreationResult result = wiring.bootstrapService()
        .createInitialAdmin(new CreateInitialAdminCommand(
            token, username, password.toCharArray(),
            body.get("name"), body.get("email")));

    switch (result) {
      case InitialAdminCreationResult.Created created -> {
        logger().info("initial admin '{}' created via bootstrap token", created.username());
        SuccessResponses.withStatusJson(ex, HttpStatus.CREATED.code(),
            JsonUtils.toJson(Map.of("status", "created", "username", created.username())));
      }
      case InitialAdminCreationResult.AlreadyInitialized ignored ->
          ErrorResponses.conflict(ex, "already initialised");
      case InitialAdminCreationResult.InvalidBootstrapToken ignored ->
          ErrorResponses.unauthorized(ex);
      case InitialAdminCreationResult.PasswordPolicyViolation policy ->
          ErrorResponses.withStatus(ex, HttpStatus.UNPROCESSABLE_CONTENT.code(),
              policy.reason() == null ? "password policy violation" : policy.reason());
      case InitialAdminCreationResult.InvalidUsername invalid ->
          ErrorResponses.withStatus(ex, HttpStatus.UNPROCESSABLE_CONTENT.code(),
              invalid.reason() == null ? "invalid username" : invalid.reason());
      case InitialAdminCreationResult.InternalError ignored ->
          ErrorResponses.internalServerError(ex, "internal error");
      default ->
          ErrorResponses.internalServerError(ex, "unexpected bootstrap result");
    }
  }
}
