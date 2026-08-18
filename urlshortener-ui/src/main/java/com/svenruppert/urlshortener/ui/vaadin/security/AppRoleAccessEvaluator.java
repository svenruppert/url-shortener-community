package com.svenruppert.urlshortener.ui.vaadin.security;



import com.svenruppert.jsentinel.authorization.api.AccessEvaluator;
import com.svenruppert.jsentinel.authorization.api.AuthorizationService;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.svenruppert.jsentinel.authorization.api.roles.RoleName;
import com.svenruppert.jsentinel.authorization.navigation.AccessContext;
import com.svenruppert.jsentinel.authorization.navigation.AccessDecision;
import com.svenruppert.urlshortener.ui.vaadin.views.login.LoginView;
import com.svenruppert.urlshortener.ui.vaadin.views.overview.OverviewView;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AppRoleAccessEvaluator implements AccessEvaluator<VisibleFor> {

  @Override
  public AccessDecision evaluate(AccessContext context, VisibleFor annotation) {

    Set<RoleName> requiredRoles = Arrays.stream(annotation.value())
        .map(Enum::name)
        .map(RoleName::new)
        .collect(Collectors.toSet());

    if (requiredRoles.isEmpty()) {
      return AccessDecision.granted();
    }

    var currentSubject = SubjectStores.subjectStore().currentSubject(AppUser.class);
    if (currentSubject.isEmpty()) {
      return AccessDecision.denied(LoginView.PATH, false);
    }

    AuthorizationService<AppUser> authorizationService =
        JSentinelServiceResolver.authorizationService();

    boolean hasRole = authorizationService.rolesFor(currentSubject.get())
        .roleNames()
        .stream()
        .anyMatch(requiredRoles::contains);

    if (hasRole) {
      return AccessDecision.granted();
    }

    return AccessDecision.denied(OverviewView.PATH, true);
  }
}