package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.bootstrap.BootstrapWiring;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;

import java.io.IOException;
import java.util.Map;

/**
 * GET /api/bootstrap/status — public. Reports whether the initial
 * admin still needs to be created (PRE-REBUILD-SCOPE Kap. 2.8).
 * Response: {@code {"bootstrapRequired": true|false}}.
 */
public class BootstrapStatusHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGet(ex)) return;
    boolean required = BootstrapWiring.instance().stateService().bootstrapRequired();
    SuccessResponses.okJson(ex, JacksonJson.toJson(Map.of("bootstrapRequired", required)));
  }
}
