package com.svenruppert.urlshortener.api.security;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.jsentinel.rest.RestRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bridges a JDK {@link HttpExchange} to jSentinel's {@link RestRequest}. */
public final class RestRequests {

  private RestRequests() {
  }

  public static RestRequest from(HttpExchange exchange) {
    URI uri = exchange.getRequestURI();
    Map<String, String> headers = new HashMap<>();
    exchange.getRequestHeaders().forEach((k, v) -> {
      if (!v.isEmpty()) headers.put(k, v.get(0));
    });
    Map<String, String> query = parseQuery(uri.getQuery());
    String method = exchange.getRequestMethod();
    String path = uri.getPath();
    return new RestRequest() {
      @Override
      public String method() {
        return method;
      }

      @Override
      public String path() {
        return path;
      }

      @Override
      public Map<String, String> headers() {
        return headers;
      }

      @Override
      public Map<String, String> queryParameters() {
        return query;
      }
    };
  }

  private static Map<String, String> parseQuery(String raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null || raw.isEmpty()) return out;
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      if (eq < 0) {
        out.put(pair, "");
      } else {
        out.put(pair.substring(0, eq), pair.substring(eq + 1));
      }
    }
    return out;
  }
}
