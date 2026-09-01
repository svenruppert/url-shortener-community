package com.svenruppert.urlshortener.ui.vaadin.security;



import eu.jsentinel.jcustos.authorization.api.AuthorizationService;
import eu.jsentinel.jcustos.authorization.api.roles.HasRoles;
import eu.jsentinel.jcustos.authorization.api.roles.RoleName;
import eu.jsentinel.jcustos.autoservice.api.JCustosAutoService;

import java.util.Objects;

@JCustosAutoService(AuthorizationService.class)
public class AppAuthorizationService
    implements AuthorizationService<AppUser> {

  @Override
  public HasRoles rolesFor(AppUser subject) {
    Objects.requireNonNull(subject);
    return () -> subject.roles()
        .stream()
        .map(role -> new RoleName(role.name()))
        .toList();
  }
}