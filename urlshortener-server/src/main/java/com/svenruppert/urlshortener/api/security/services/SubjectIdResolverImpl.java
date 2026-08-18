package com.svenruppert.urlshortener.api.security.services;

import com.svenruppert.jsentinel.authorization.api.SubjectIdResolver;
import com.svenruppert.jsentinel.authorization.api.tenant.TenantId;
import com.svenruppert.jsentinel.logout.SubjectId;
import com.svenruppert.urlshortener.api.security.model.ApiUser;

/**
 * Maps the application's {@link ApiUser} to the framework's stable
 * {@link SubjectId}. Required for drift detection: login captures a
 * {@code JSentinelVersion} snapshot keyed by this id and the
 * {@link com.svenruppert.urlshortener.api.security.services.VersionBumper}
 * increments the same key on role changes.
 *
 * <p>SPI-registered via
 * {@code META-INF/services/com.svenruppert.jsentinel.authorization.api.SubjectIdResolver}.
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
