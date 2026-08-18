package com.svenruppert.urlshortener.core.auth;

import java.util.List;

/** Body of {@code POST /api/users}. */
public record CreateUserRequest(String username, String password, List<String> roles) {
}
