package com.svenruppert.urlshortener.api.handler.statistics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsReader;
import com.svenruppert.urlshortener.api.utils.QueryUtils;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.statistics.StatisticsCountResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import com.svenruppert.urlshortener.api.security.OwnerGuard;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_STATISTICS_COUNT;

/**
 * Handler for statistics count queries.
 * <p>
 * Endpoints:
 * - GET /api/statistics/count/{shortCode} - total count for shortCode
 * - GET /api/statistics/count/{shortCode}?date=2024-01-15 - count for specific date
 * - GET /api/statistics/count/{shortCode}?from=2024-01-01&to=2024-01-31 - count for date range
 */
@RequiresPermission("statisticsCount")
public class StatisticsCountHandler
    implements HttpHandler, HasLogger {

  private static final String ERROR_MISSING_SHORT_CODE = "{\"error\":\"bad_request\",\"message\":\"shortCode is required\"}";
  private static final String ERROR_NOT_OWNER = "{\"error\":\"forbidden\",\"message\":\"not the owner of this alias\"}";
  private static final String ERROR_INVALID_DATE = "{\"error\":\"bad_request\",\"message\":\"Invalid date format. Use ISO format: yyyy-MM-dd\"}";

  private final StatisticsReader statisticsReader;

  public StatisticsCountHandler(StatisticsReader statisticsReader) {
    this.statisticsReader = statisticsReader;
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    if (!RequestMethodUtils.requireGet(exchange)) return;

    String path = exchange.getRequestURI().getPath();
    logger().info("StatisticsCountHandler invoked: {}", path);

    // Extract shortCode from path: /api/statistics/count/{shortCode}
    String shortCode = extractShortCode(path);
    logger().info(" shortCode from request {}", shortCode);
    if (shortCode == null || shortCode.isBlank()) {
      writeJson(exchange, BAD_REQUEST, ERROR_MISSING_SHORT_CODE);
      return;
    }
    // SEC-M1 (V00.17.00 P050): per-link statistics are owner-scoped.
    if (!OwnerGuard.canManage(exchange, shortCode)) {
      writeJson(exchange, FORBIDDEN, ERROR_NOT_OWNER);
      return;
    }

    var queryParams = QueryUtils.parseQueryParams(exchange.getRequestURI().getRawQuery());
    String dateParam = QueryUtils.first(queryParams, "date");
    String fromParam = QueryUtils.first(queryParams, "from");
    String toParam = QueryUtils.first(queryParams, "to");

    try {
      StatisticsCountResponse response;

      if (dateParam != null) {
        // Single date query
        logger().info("Single date query");
        LocalDate date = LocalDate.parse(dateParam);
        long count = statisticsReader.getCountForDate(shortCode, date);
        response = StatisticsCountResponse.forDate(shortCode, date, count);
      } else if (fromParam != null && toParam != null) {
        // Date range query
        logger().info("Date range query");
        LocalDate from = LocalDate.parse(fromParam);
        LocalDate to = LocalDate.parse(toParam);
        logger().info("range from {} - to {}", from, to);
        long count = statisticsReader.getCountForDateRange(shortCode, from, to);
        logger().info("count {}", count);


        response = new StatisticsCountResponse(shortCode, from, to, count);
      } else {
        // Total count query
        logger().info("Total count query");
        long count = statisticsReader.getTotalCount(shortCode);
        response = StatisticsCountResponse.total(shortCode, count);
      }
      logger().info("response {}", response);
      writeJson(exchange, OK, response);

    } catch (DateTimeParseException e) {
      logger().warn("Invalid date format in request: {}", e.getMessage());
      writeJson(exchange, BAD_REQUEST, ERROR_INVALID_DATE);
    }
  }

  private String extractShortCode(String path) {
    if (!path.startsWith(PATH_ADMIN_STATISTICS_COUNT)) {
      return null;
    }
    String remainder = path.substring(PATH_ADMIN_STATISTICS_COUNT.length());
    if (remainder.startsWith("/")) {
      remainder = remainder.substring(1);
    }
    return remainder.isBlank() ? null : remainder;
  }
}
