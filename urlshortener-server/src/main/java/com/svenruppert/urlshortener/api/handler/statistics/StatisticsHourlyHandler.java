package com.svenruppert.urlshortener.api.handler.statistics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsReader;
import com.svenruppert.urlshortener.api.utils.QueryUtils;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.statistics.HourlyAggregate;
import com.svenruppert.urlshortener.core.statistics.HourlyStatisticsResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_STATISTICS_HOURLY;

/**
 * Handler for hourly statistics queries.
 * Only returns data for dates within the hot window.
 * Endpoint:
 * - GET /api/statistics/hourly/{shortCode}?date=2024-01-15
 */
@RequiresPermission("statisticsHourly")
public class StatisticsHourlyHandler
    implements HttpHandler, HasLogger {

  private static final String ERROR_MISSING_SHORT_CODE =
      "{\"error\":\"bad_request\",\"message\":\"shortCode is required\"}";
  private static final String ERROR_NOT_OWNER =
      "{\"error\":\"forbidden\",\"message\":\"not the owner of this alias\"}";
  private static final String ERROR_MISSING_DATE =
      "{\"error\":\"bad_request\",\"message\":\"date parameter is required\"}";
  private static final String ERROR_INVALID_DATE =
      "{\"error\":\"bad_request\",\"message\":\"Invalid date format. Use ISO format: yyyy-MM-dd\"}";
  private static final String ERROR_NOT_IN_HOT_WINDOW =
      "{\"error\":\"not_found\",\"message\":\"Hourly data not available. Date is outside hot window.\"}";

  private final StatisticsReader statisticsReader;

  public StatisticsHourlyHandler(StatisticsReader statisticsReader) {
    this.statisticsReader = statisticsReader;
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    if (!RequestMethodUtils.requireGet(exchange)) return;

    String path = exchange.getRequestURI().getPath();
    logger().info("StatisticsHourlyHandler: GET {}", path);

    String shortCode = extractShortCode(path);
    if (shortCode == null || shortCode.isBlank()) {
      writeJson(exchange, BAD_REQUEST, ERROR_MISSING_SHORT_CODE);
      return;
    }
    // SEC-M1 (V00.17.00 P050): per-link statistics are owner-scoped.
    if (!com.svenruppert.urlshortener.api.security.OwnerGuard.canManage(exchange, shortCode)) {
      writeJson(exchange, FORBIDDEN, ERROR_NOT_OWNER);
      return;
    }

    var queryParams = QueryUtils.parseQueryParams(exchange.getRequestURI().getRawQuery());
    String dateParam = QueryUtils.first(queryParams, "date");

    if (dateParam == null || dateParam.isBlank()) {
      writeJson(exchange, BAD_REQUEST, ERROR_MISSING_DATE);
      return;
    }

    try {
      LocalDate date = LocalDate.parse(dateParam);

      if (!statisticsReader.isInHotWindow(date)) {
        writeJson(exchange, NOT_FOUND, ERROR_NOT_IN_HOT_WINDOW);
        return;
      }

      Optional<HourlyAggregate> aggregate = statisticsReader.getHourlyAggregate(shortCode, date);

      if (aggregate.isEmpty()) {
        writeJson(exchange, NOT_FOUND, ERROR_NOT_IN_HOT_WINDOW);
        return;
      }

      HourlyStatisticsResponse response = HourlyStatisticsResponse.from(shortCode, aggregate.get());
      writeJson(exchange, OK, response);

    } catch (DateTimeParseException e) {
      logger().warn("Invalid date format: {}", e.getMessage());
      writeJson(exchange, BAD_REQUEST, ERROR_INVALID_DATE);
    }
  }

  private String extractShortCode(String path) {
    if (!path.startsWith(PATH_ADMIN_STATISTICS_HOURLY)) {
      return null;
    }
    String remainder = path.substring(PATH_ADMIN_STATISTICS_HOURLY.length());
    if (remainder.startsWith("/")) {
      remainder = remainder.substring(1);
    }
    return remainder.isBlank() ? null : remainder;
  }
}
