package com.svenruppert.urlshortener.core.campaign;

/**
 * Renames a tag across every link that carries it. If {@code to} already exists
 * on some links, the rename merges into it (union). Both names are normalized
 * (trim + lowercase) server-side.
 */
public record RenameTagRequest(String from, String to) {
}
