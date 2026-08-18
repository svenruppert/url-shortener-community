package com.svenruppert.urlshortener.ui.vaadin.security;



import com.svenruppert.jsentinel.authorization.LoginListener;
import com.svenruppert.urlshortener.ui.vaadin.views.login.LoginView;
import com.svenruppert.urlshortener.ui.vaadin.views.overview.OverviewView;
import com.vaadin.flow.component.Component;

public class AppLoginListener extends LoginListener<AppUser> {

  @Override
  public void notARestrictedTarget(Class<?> navigationTarget) {
    logger().debug("Public navigation target: {}", navigationTarget.getSimpleName());
  }

  @Override
  public Class<? extends com.svenruppert.jsentinel.authorization.LoginView> loginNavigationTarget() {
    return LoginView.class;
  }

  @Override
  public Class<? extends Component> defaultNavigationTarget() {
    return OverviewView.class;
  }
}