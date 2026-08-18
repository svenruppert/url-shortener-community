package com.svenruppert.urlshortener.core.campaign;

import java.util.List;

/**
 * Replaces a tag by one or more other tags across every link that carries it:
 * on each such link {@code from} is removed and all {@code to} tags are added.
 * A single-element {@code to} is a rename; an empty {@code to} is a delete. All
 * names are normalized (trim + lowercase) server-side.
 */
public record ReplaceTagRequest(String from, List<String> to) {
}
