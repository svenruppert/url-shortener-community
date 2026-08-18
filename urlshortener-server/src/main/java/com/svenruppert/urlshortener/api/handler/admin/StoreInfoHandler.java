package com.svenruppert.urlshortener.api.handler.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseUrlMappingStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.StoreInfo;

import java.io.IOException;

import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;

@RequiresPermission("storeInfo")
public final class StoreInfoHandler
    implements HttpHandler, HasLogger {


  private final UrlMappingStore store;
  private final long startedAtMs;

  public StoreInfoHandler(UrlMappingStore store, long startedAtMs) {
    this.startedAtMs = startedAtMs;
    this.store = store;
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGet(ex)) return;

    String mode = (store instanceof EclipseUrlMappingStore) ? "EclipseStore" : "InMemory";
    logger().info("StoreInfoHandler - mode {}", mode);
    int mappings = store.countAll();
    logger().info("amount of mappings in db {}", mappings);

    var info = new StoreInfo(mode, mappings, startedAtMs);
    logger().info("responseJson {}", info);

    writeJson(ex, OK, info);
  }
}

