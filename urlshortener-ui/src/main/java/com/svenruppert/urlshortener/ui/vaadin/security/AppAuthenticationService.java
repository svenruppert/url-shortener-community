package com.svenruppert.urlshortener.ui.vaadin.security;



import eu.jsentinel.jcustos.authentication.AuthenticationService;
import eu.jsentinel.jcustos.autoservice.api.JCustosAutoService;

import java.util.Set;

@JCustosAutoService(AuthenticationService.class)
public class AppAuthenticationService
    implements AuthenticationService<AppCredentials, AppUser> {

  static final String DEFAULT_USERNAME = "admin";

  @Override
  public boolean checkCredentials(AppCredentials credentials) {
    if (credentials == null || credentials.password() == null) return false;
    if (!LoginConfig.isLoginConfigured()) return false;
    return LoginConfig.matches(credentials.password().toCharArray());
  }

  @Override
  public AppUser loadSubject(AppCredentials credentials) {
    String name = (credentials != null && credentials.username() != null
        && !credentials.username().isBlank())
        ? credentials.username()
        : DEFAULT_USERNAME;
    return new AppUser(name, Set.of(AppRole.USER));
  }

  @Override
  public Class<AppUser> subjectType() {
    return AppUser.class;
  }
}