package com.svenruppert.urlshortener.api.handler.urlmapping;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.parseOrBadRequest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;
import com.svenruppert.urlshortener.core.validation.UrlValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.api.security.AppPasswordHashing;
import com.svenruppert.urlshortener.api.security.OwnerGuard;



@RequiresPermission("editLink")
public class EditMappingHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingStore urlMappingStore;

  public EditMappingHandler(UrlMappingStore urlMappingStore) {
    this.urlMappingStore = urlMappingStore;
  }

  private static String readBody(InputStream in)
      throws IOException {
    if (in == null) return "";
    byte[] buf = in.readAllBytes();
    return new String(buf, StandardCharsets.UTF_8);
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    logger().info("EditMappingHandler invoked..");
    if (!RequestMethodUtils.requirePut(ex)) return;


    try {
      final String body = readBody(ex.getRequestBody());
      ShortenRequest req = parseOrBadRequest(ex, body, ShortenRequest.class);
      if (req == null) return;
      String shortCode = req.getShortURL();
      if (shortCode == null || shortCode.isBlank()) {
        ErrorResponses.badRequest(ex, "Missing shortCode in path");
        return;
      }

      // SEC-H1 (V00.17.00 P030): only the owner or link:read:all may edit —
      // without this check any USER could hijack a foreign link's target URL.
      if (!OwnerGuard.canManage(ex, shortCode)) {
        logger().info("edit denied — not owner of '{}'", shortCode);
        ErrorResponses.withStatus(ex, HttpStatus.FORBIDDEN,
            "not the owner of this alias");
        return;
      }

      var result = UrlValidator.validate(req.getUrl());
      if (!result.valid()) {
        ErrorResponses.badRequest(ex, result.message());
        return;
      } else {
        logger().info("valid URL {}", req.getUrl());
      }

      Optional<ShortUrlMapping> currentOpt = urlMappingStore.findByShortCode(shortCode);
      if (currentOpt.isEmpty()) {
        ErrorResponses.notFound(ex, "shortCode not found");
        return;
      }

      final Instant newExpires = req.getExpiresAt();
      final Boolean active = req.getActive();

      // Per-link password null semantics (distinct from expiry):
      //   password == null  -> field omitted -> keep existing protection
      //   password is blank  -> explicit clear -> remove protection
      //   password non-blank -> set/replace -> store the fresh hash
      final String pw = req.getPassword();
      final String resolvedPasswordHash;
      if (pw == null) {
        resolvedPasswordHash = currentOpt.get().getPasswordHash();
      } else if (pw.isBlank()) {
        resolvedPasswordHash = null;
      } else {
        resolvedPasswordHash = AppPasswordHashing.hasher().hash(pw.toCharArray());
      }

      final Result<ShortUrlMapping, String> updated = urlMappingStore.editMapping(shortCode, req.getUrl(), newExpires, active, resolvedPasswordHash);

      if (updated.isFailure()) {
        updated.peekFailure(msg -> {
          logger().info("failed: {}", msg);
          try {
            ErrorResponses.notFound(ex, msg);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
        return;
      }
      SuccessResponses.noContent(ex);
    } catch (Exception e) {
      logger().error("EditMapping failed", e);
      ErrorResponses.internalServerError(ex, "internal error");

    }
  }
}
