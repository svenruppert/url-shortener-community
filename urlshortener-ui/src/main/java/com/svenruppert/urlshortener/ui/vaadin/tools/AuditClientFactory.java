package com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.urlshortener.client.AuditClient;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;

public class AuditClientFactory {

  private AuditClientFactory() {
  }

  public static AuditClient newInstance() {
    return new AuditClient().withBearerToken(SessionAuth::currentToken);
  }
}
