package com.svenruppert.urlshortener.core.campaign;

import java.util.List;

/** Request body for replacing a link's tag set. */
public record SetTagsRequest(List<String> tags) {
}
