package com.svenruppert.urlshortener.core.campaign;

import java.util.List;

/**
 * Assigns several links to a campaign in one request, with an optional shared
 * placement {@code label} applied to each. Links the caller cannot manage are
 * skipped (see {@link BatchAssignResponse}).
 */
public record BatchAssignRequest(List<String> shortCodes, String label) {
}
