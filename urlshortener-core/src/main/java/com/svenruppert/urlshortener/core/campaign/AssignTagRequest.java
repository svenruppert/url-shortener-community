package com.svenruppert.urlshortener.core.campaign;

import java.util.List;

/**
 * Adds a tag to several links at once: the tag is added to each link's existing
 * tags (union, not replace). The tag name is normalized (trim + lowercase).
 */
public record AssignTagRequest(String tag, List<String> shortCodes) {
}
