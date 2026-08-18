package com.svenruppert.urlshortener.core.auth;

import java.util.List;

/** Admin view of a user (never carries the password hash). */
public record UserSummary(long id, String name, List<String> roles) {
}
