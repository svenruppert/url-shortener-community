package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.svenruppert.urlshortener.client.AdminClient;
import com.svenruppert.urlshortener.core.DefaultValues;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;

public class AdminClientFactory {

  private AdminClientFactory() {
  }

  public static AdminClient newInstance() {
    return new AdminClient(DefaultValues.ADMIN_SERVER_URL)
        .withBearerToken(SessionAuth::currentToken);
  }
}