package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.svenruppert.urlshortener.client.StatisticsClient;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;

public class StatisticsClientFactory {

  private StatisticsClientFactory() {
  }

  public static StatisticsClient newInstance() {
    return new StatisticsClient().withBearerToken(SessionAuth::currentToken);
  }
}