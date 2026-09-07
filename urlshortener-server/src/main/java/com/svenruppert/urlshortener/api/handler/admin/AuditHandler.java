package com.svenruppert.urlshortener.api.handler.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.audit.AuditEvent;
import eu.jsentinel.jcustos.audit.AuditQuery;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.QueryUtils;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.auth.AuditEntry;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.svenruppert.urlshortener.api.utils.QueryUtils.first;

/**
 * GET /api/audit — paginated security audit log (PRE-REBUILD-SCOPE
 * Kap. 2.8). Optional query params: {@code subject}, {@code limit}
 * (default 500).
 */
@RequiresPermission("listAudit")
public class AuditHandler
    implements HttpHandler, HasLogger {

  private static final int DEFAULT_LIMIT = 500;

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGet(ex)) return;
    try {
      Map<String, List<String>> params = QueryUtils.parseQueryParams(ex.getRequestURI().getRawQuery());
      String subject = first(params, "subject");
      int limit;
      try {
        limit = parseLimit(first(params, "limit"));
      } catch (IllegalArgumentException badLimit) {
        // ?limit=abc used to be swallowed and silently turned into 500 entries.
        // A parameter the caller cannot have meant should be reported, the way
        // the statistics handlers report an unreadable date.
        ErrorResponses.invalidParameter(ex, "limit");
        return;
      }

      AuditQuery query = (subject == null || subject.isBlank())
          ? AuditQuery.all()
          : AuditQuery.forSubject(subject);

      List<AuditEntry> entries = JCustosServiceResolver.securityAuditService().query(query)
          .stream()
          .limit(limit)
          .map(AuditHandler::toEntry)
          .toList();
      SuccessResponses.okJson(ex, JacksonJson.toJson(entries));
    } catch (RuntimeException e) {
      logger().warn("audit query failed - {}", e.toString());
      ErrorResponses.internalServerError(ex, "internal_error");
    }
  }

  private static AuditEntry toEntry(AuditEvent event) {
    return new AuditEntry(
        String.valueOf(event.timestamp()),
        event.getClass().getSimpleName(),
        subjectOf(event),
        event.toString());
  }

  private static String subjectOf(AuditEvent event) {
    // AuditEvent subtypes carry differently-named subject fields;
    // the readable toString() already includes them, so expose "".
    return "";
  }

  /**
   * @throws IllegalArgumentException if the parameter is present but unreadable
   *     or not positive — the caller gets a 400 rather than a silent default
   */
  private static int parseLimit(String raw) {
    if (raw == null) return DEFAULT_LIMIT;
    int v;
    try {
      v = Integer.parseInt(raw.trim());
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException("limit is not a number: " + raw, notANumber);
    }
    if (v <= 0) throw new IllegalArgumentException("limit must be positive: " + v);
    return v;
  }
}
