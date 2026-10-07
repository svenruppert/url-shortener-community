package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.extension.LinkLifecycleHooks;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;
import com.svenruppert.urlshortener.core.validation.UrlValidator;

import java.io.IOException;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.api.security.AppPasswordHashing;
import com.svenruppert.urlshortener.api.security.LinkOwnershipStore;
import com.svenruppert.urlshortener.api.security.OwnerGuard;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.parseOrBadRequest;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * POST /shorten
 * Request JSON: { "url": "https://example.org", "alias": "my-nice-code" } (alias optional)
 * Responses:
 * - 201 Created, body: ShortUrlMapping, Location: /r/{code}
 * - 400 Bad Request (invalid JSON/URL/Alias)
 * - 409 Conflict (alias already used)
 * - 405 Method Not Allowed (others)
 * <p>
 * Hint: Aliasse sind case-insensitive (werden intern lowercased gespeichert).
 */
@RequiresPermission("createLink")
public class ShortenHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingStore store;
  private final LinkLifecycleHooks hooks;

  public ShortenHandler(UrlMappingStore store, LinkLifecycleHooks hooks) {
    this.store = store;
    this.hooks = hooks;
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    logger().info("handle ... {} ", ex.getRequestMethod());
    if (!RequestMethodUtils.requirePost(ex)) return;
    try {
      final String body = readBody(ex.getRequestBody());
      ShortenRequest req = parseOrBadRequest(ex, body, ShortenRequest.class);
      if (req == null) return;
      if (isNullOrBlank(req.getUrl())) {
        ErrorResponses.badRequest(ex, "Missing 'url'");
        return;
      }

      var result = UrlValidator.validate(req.getUrl());
      if (!result.valid()) {
        ErrorResponses.badRequest(ex, result.message());
        return;
      } else {
        logger().info("valid URL {}", req.getUrl());
      }


      // Optional per-link password: hash the clear-text (HTTPS) before it reaches
      // the store; a blank/absent password means the link is not protected.
      final String pw = req.getPassword();
      final String passwordHash = (pw != null && !pw.isBlank())
          ? AppPasswordHashing.hasher().hash(pw.toCharArray())
          : null;

      logger().info("ShortenHandler - createMapping start");
      final Result<ShortUrlMapping, String> urlMappingResult = store.createMapping(req.getShortURL(),
                                                                                   req.getUrl(),
                                                                                   req.getExpiresAt(),
                                                                                   req.getActive(),
                                                                                   passwordHash);
      logger().info("ShortenHandler - createMapping stop");
      urlMappingResult
          .peek(success -> {
            logger().info("mapping created success {}", success.toString());
            LinkOwnershipStore.INSTANCE.record(
                success.shortCode(),
                OwnerGuard.currentUserId(ex));
            // plug-in hook (V00.18.00 P085): create-time metadata rides along
            // in the raw JSON; without a plug-in this is a no-op
            hooks.linkCreated(success.shortCode(), body, false);
          })
          .peekFailure(failed -> logger().info("mapping created failed - {}", failed));

      logger().info("ShortenHandler - createMapping consuming urlMappingResult");
      urlMappingResult
          .peekFailure(errorJson -> {
            try {
              var parsed = JsonUtils.parseJson(errorJson);
              logger().info("parsed Json {}", parsed);
              var code = parsed.get("code");
              var errorCode = Integer.parseInt(code);
              var message = parsed.get("message");
              ErrorResponses.withStatus(ex, errorCode, message);

            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          })
          .peek(mapping -> {
            // 201 + Location Header
            final Headers h = ex.getResponseHeaders();
            h.add("Location", "/r/" + mapping.shortCode());
            try {
              SuccessResponses.withStatus(ex, HttpStatus.CREATED.code(), mapping);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
      logger().info("hortenHandler .. try block .. done");
    } catch (IllegalArgumentException e) {
      // z.B. "Invalid JSON: ..." oder fachliche Validierung
      logger().warn("bad request - {}", e.getMessage());
      ErrorResponses.badRequest(ex, e.getMessage());
    } catch (Exception e) {
      logger().error("internal error", e);
      ErrorResponses.internalServerError(ex, "internal_error");
    } finally {
      logger().info("ShortenHandler .. finally");
      // ex.close() entfernen
    }

  }
}