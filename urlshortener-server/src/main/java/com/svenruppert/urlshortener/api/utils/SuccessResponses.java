package com.svenruppert.urlshortener.api.utils;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;

import java.io.IOException;

import static com.svenruppert.dependencies.core.net.HttpStatus.NO_CONTENT;
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;

public final class SuccessResponses {

  private SuccessResponses() {
  }

  public static void ok(HttpExchange ex, Object dto)
      throws IOException {
    JsonWriter.writeJson(ex, OK, dto);
  }

  public static void okJson(HttpExchange ex, String rawJson)
      throws IOException {
    JsonWriter.writeJsonRaw(ex, OK, rawJson);
  }

  public static void withStatus(HttpExchange ex, int statusCode, Object body)
      throws IOException {

    JsonWriter.writeJson(ex, HttpStatus.fromCode(statusCode), body);
  }

  public static void withStatusJson(HttpExchange ex, int statusCode, String rawJson)
      throws IOException {

    JsonWriter.writeJsonRaw(ex, HttpStatus.fromCode(statusCode), rawJson);
  }
  public static void noContent(HttpExchange ex) throws IOException {
    ex.getResponseHeaders().set(CONTENT_TYPE, MediaType.APPLICATION_JSON.mime());
    ex.sendResponseHeaders(NO_CONTENT.code(), -1);
  }

}
