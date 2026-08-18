package com.svenruppert.urlshortener.ui.vaadin.security;



import com.svenruppert.jsentinel.authorization.api.AuthorizationService;
import com.svenruppert.jsentinel.authorization.api.roles.HasRoles;
import com.svenruppert.jsentinel.authorization.api.roles.RoleName;
import com.svenruppert.jsentinel.autoservice.api.JSentinelAutoService;

import java.util.Objects;

@JSentinelAutoService(AuthorizationService.class)
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