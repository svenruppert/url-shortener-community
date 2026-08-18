package com.svenruppert.urlshortener.api.security.model;

/** Username/password credentials passed into the AuthenticationService. */
public record Credentials(String username, String password) {
}
