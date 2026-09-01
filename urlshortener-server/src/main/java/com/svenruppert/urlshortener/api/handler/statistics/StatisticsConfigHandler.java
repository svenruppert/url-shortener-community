package com.svenruppert.urlshortener.api.handler.statistics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.statistics.StatisticsConfig;
import com.svenruppert.urlshortener.core.statistics.StatisticsConfigResponse;

import java.io.IOException;
import java.io.InputStream;

import static com.svenruppert.dependencies.core.net.HttpStatus.BAD_REQUEST;
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Handler for statistics configuration queries and updates.
 * Endpoints:
 * - GET /api/statistics/config - get current configuration
 * - PUT /api/statistics/config - update configuration
 */
@RequiresPermission("statisticsConfig")
public class StatisticsConfigHandler
    implements HttpHandler, HasLogger {

  private static final String ERROR_INVALID_JSON =
      "{\"error\":\"bad_request\",\"message\":\"Invalid JSON body\"}";
  private static final String ERROR_INVALID_CONFIG =
      "{\"error\":\"bad_request\",\"message\":\"Invalid configuration values\"}";

  private final StatisticsStore statisticsStore;

  public StatisticsConfigHandler(StatisticsStore statisticsStore) {
    this.statisticsStore = statisticsStore;
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    String method = exchange.getRequestMethod();
    logger().info("StatisticsConfigHandler: {} /api/statistics/config", method);

    if ("GET".equalsIgnoreCase(method)) {
      handleGet(exchange);
    } else if ("PUT".equalsIgnoreCase(method)) {
      handlePut(exchange);
    } else {
      RequestMethodUtils.methodNotAllowed(exchange, "GET, PUT");
    }
  }

  private void handleGet(HttpExchange exchange)
      throws IOException {
    StatisticsConfig config = statisticsStore.getConfig();
    StatisticsConfigResponse response = StatisticsConfigResponse.from(config);
    writeJson(exchange, OK, response);
  }

  private void handlePut(HttpExchange exchange)
      throws IOException {
    String body;
    try (InputStream is = exchange.getRequestBody()) {
      body = new String(is.readAllBytes(), UTF_8);
    }

    logger().info("StatisticsConfigHandler PUT body: {}", body);

    try {
      ConfigUpdateRequest request = JsonUtils.fromJson(body, ConfigUpdateRequest.class);

      if (request.hotWindowDays() < 1 ||
          request.writerBatchSize() < 1 ||
          request.aggregatorIntervalSeconds() < 60) {
        writeJson(exchange, BAD_REQUEST, ERROR_INVALID_CONFIG);
        return;
      }

      StatisticsConfig newConfig = new StatisticsConfig();
      newConfig.setHotWindowDays(request.hotWindowDays());
      newConfig.setWriterBatchSize(request.writerBatchSize());
      newConfig.setAggregatorIntervalSeconds(request.aggregatorIntervalSeconds());
      newConfig.setStatisticsEnabled(request.statisticsEnabled());

      statisticsStore.updateConfig(newConfig);

      StatisticsConfigResponse response = StatisticsConfigResponse.from(statisticsStore.getConfig());
      writeJson(exchange, OK, response);

    } catch (Exception e) {
      logger().warn("Failed to parse config update request: {}", e.getMessage());
      writeJson(exchange, BAD_REQUEST, ERROR_INVALID_JSON);
    }
  }

  /**
   * Request body for configuration updates.
   */
  public record ConfigUpdateRequest(
      int hotWindowDays,
      int writerBatchSize,
      int aggregatorIntervalSeconds,
      boolean statisticsEnabled
  ) { }
}
