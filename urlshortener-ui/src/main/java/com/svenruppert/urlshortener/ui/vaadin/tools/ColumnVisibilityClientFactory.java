package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.svenruppert.urlshortener.client.ColumnVisibilityClient;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;

public class ColumnVisibilityClientFactory {

  private ColumnVisibilityClientFactory() {
  }

  public static ColumnVisibilityClient newInstance() {
    return new ColumnVisibilityClient().withBearerToken(SessionAuth::currentToken);
  }
}