package com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.urlshortener.client.UsersClient;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;

public class UsersClientFactory {

  private UsersClientFactory() {
  }

  public static UsersClient newInstance() {
    return new UsersClient().withBearerToken(SessionAuth::currentToken);
  }
}
