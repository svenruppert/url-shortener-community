package com.svenruppert.urlshortener.api.security.services;

import eu.jsentinel.jcustos.authorization.api.SubjectIdResolver;
import eu.jsentinel.jcustos.authorization.api.tenant.TenantId;
import eu.jsentinel.jcustos.logout.SubjectId;
import com.svenruppert.urlshortener.api.security.model.ApiUser;

/**
 * Maps the application's {@link ApiUser} to the framework's stable
 * {@link SubjectId}. Required for drift detection: login captures a
 * {@code JCustosVersion} snapshot keyed by this id and the
 * {@link com.svenruppert.urlshortener.api.security.services.VersionBumper}
 * increments the same key on role changes.
 *
 * <p>SPI-registered via
 * {@code META-INF/services/eu.jsentinel.jcustos.authorization.api.SubjectIdResolver}.
 */
public final class SubjectIdResolverImpl implements SubjectIdResolver<ApiUser> {

  @Override
  public SubjectId resolve(ApiUser subject) {
    return SubjectId.of(String.valueOf(subject.id()));
  }

  @Override
  public TenantId tenantFor(ApiUser subject) {
    return TenantId.DEFAULT;
  }
}
