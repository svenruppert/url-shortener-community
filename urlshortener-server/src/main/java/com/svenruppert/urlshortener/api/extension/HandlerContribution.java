package com.svenruppert.urlshortener.api.extension;

import com.sun.net.httpserver.HttpHandler;

/**
 * ServiceLoader SPI for REST endpoint plug-ins (open-core seam, concept
 * DECISION 2). An Enterprise jar registers an implementation under
 * {@code META-INF/services/com.svenruppert.urlshortener.api.extension.HandlerContribution}
 * and thereby adds an endpoint on the admin server — without any change to
 * community sources.
 *
 * <p>Contributed handlers run behind the SAME security pipeline as community
 * handlers ({@code SecurityHttpHandler.secure(...)}): annotate the handler
 * with {@code @RequiresPermission} exactly like a community handler.
 * Duplicate paths — against community routes or between plug-ins — fail fast
 * at server startup.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface HandlerContribution {

  /** The context path to register, e.g. {@code /api/enterprise/audit-export}. */
  String path();

  /** Builds the handler against the running server's stores. */
  HttpHandler create(ServerContext context);
}
