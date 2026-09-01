package com.svenruppert.urlshortener.api.handler.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.dx.runtime.JCustosRuntime;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.security.SecurityDiagnostics;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;

import java.io.IOException;

/**
 * GET /api/admin/health — security-runtime diagnostics (P250). Reports the
 * jCustos bootstrap health, mode, registered services and warnings from
 * {@link JCustosRuntime}. Admin-only (reuses the {@code storeInfo}
 * permission — operational info like the store-info endpoint). Feeds the
 * planned Server-Logs/Health view (00.40.00).
 */
@RequiresPermission("storeInfo")
public final class HealthHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGet(ex)) return;
    JCustosRuntime runtime = SecurityDiagnostics.runtime();
    if (runtime == null) {
      ErrorResponses.serviceUnavailable(ex, "security runtime not initialised");
      return;
    }
    SuccessResponses.okJson(ex, runtime.toJson());
  }
}
