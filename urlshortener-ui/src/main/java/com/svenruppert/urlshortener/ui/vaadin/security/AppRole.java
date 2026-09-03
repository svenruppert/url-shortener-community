package com.svenruppert.urlshortener.ui.vaadin.security;



public enum AppRole {
  ADMIN,
  USER,
  /** Read-only analyst on assigned campaigns (V00.19.00 P050). */
  CAMPAIGN_ANALYST
}