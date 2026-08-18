package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;

public class UrlShortenerClientFactory {

  private UrlShortenerClientFactory() {
  }

  public static URLShortenerClient newInstance() {
    return new URLShortenerClient().withBearerToken(SessionAuth::currentToken);
  }

}