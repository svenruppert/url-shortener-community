package com.svenruppert.urlshortener.core.campaign;

/**
 * Creates a tag as a first-class (initially empty) entry in the tag registry, so
 * it can be predefined and assigned to links later. The name is normalized
 * (trim + lowercase) server-side.
 */
public record CreateTagRequest(String name) {
}
