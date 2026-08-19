package com.svenruppert.urlshortener.api.handler.statistics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;

import java.io.IOException;

import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;

/**
 * Debug handler for statistics - shows internal state of statistics storage.
 * GET /api/statistics/debug - shows overview of all stored statistics data
 */
@RequiresPermission("statisticsDebug")
public class StatisticsDebugHandler
    implements HttpHandler, HasLogger {

  private final StatisticsStore statisticsStore;

  public StatisticsDebugHandler(StatisticsStore statisticsStore) {
    this.statisticsStore = statisticsStore;
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    if (!RequestMethodUtils.requireGet(exchange)) return;

    logger().info("StatisticsDebugHandler invoked");

    var debugInfo = statisticsStore.getDebugInfo();
    writeJson(exchange, OK, debugInfo);
  }
}
