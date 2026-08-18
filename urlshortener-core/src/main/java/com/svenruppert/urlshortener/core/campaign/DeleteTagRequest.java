package com.svenruppert.urlshortener.core.campaign;

/**
 * Removes a tag from every link that carries it. The tag name is normalized
 * (trim + lowercase) server-side.
 */
public record DeleteTagRequest(String tag) {
}
