package com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.urlshortener.client.CampaignClient;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;

public class CampaignClientFactory {

  private CampaignClientFactory() {
  }

  public static CampaignClient newInstance() {
    return new CampaignClient().withBearerToken(SessionAuth::currentToken);
  }
}
