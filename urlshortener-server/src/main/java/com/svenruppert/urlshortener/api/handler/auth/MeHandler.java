package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.api.JCustosSubject;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GET /api/me — identity of the authenticated caller: id, display
 * name, roles and permissions. Authenticated-only (wrapped in
 * SecurityHttpHandler WITHOUT an @Authorize permission).
 */
public class MeHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGet(ex)) return;
    Optional<JCustosSubject> subject = CurrentSubject.of(ex);
    if (subject.isEmpty()) {
      ErrorResponses.unauthorized(ex);
      return;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", subject.get().subjectId());
    body.put("displayName", subject.get().displayName());
    body.put("roles", subject.get().roleNames().stream().map(r -> r.value()).sorted().toList());
    body.put("permissions", subject.get().permissionNames().stream().map(p -> p.value()).sorted().toList());
    SuccessResponses.okJson(ex, JacksonJson.toJson(body));
  }
}
