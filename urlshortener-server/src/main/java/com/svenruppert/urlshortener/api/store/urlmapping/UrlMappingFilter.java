package com.svenruppert.urlshortener.api.store.urlmapping;

import com.svenruppert.urlshortener.core.AliasPolicy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Advanced filter:
 * - codePart/urlPart: Substring search (optionally case-sensitive)
 * - createdFrom/To: Inclusive time range
 * - paging: offset/limit
 * - sorting: sortBy + direction
 */
public final class UrlMappingFilter {

  public static final String CODE_PART = "codePart";
  public static final String URL_PART = "urlPart";
  public static final String CREATED_FROM = "createdFrom";
  public static final String CREATED_TO = "createdTo";
  public static final String PAGING = "paging";
  public static final String OFFSET = "offset";
  public static final String LIMIT = "limit";
  public static final String SORT_BY = "sortBy";
  public static final String DIRECTION = "direction";
  public static final String ACTIVE = "active";

  private final String codePart;            // already normalized (AliasPolicy) or null
  private final String urlPart;             // lowercase if necessary depending on the flag
  private final Instant createdFrom;        // inclusive
  private final Instant createdTo;          // inclusive
  private final Integer offset;             // nullable -> no paging
  private final Integer limit;              // nullable -> no paging
  private final SortBy sortBy;              // nullable -> unsorted
  private final Direction direction;        // nullable -> ASC default if sortBy != null
  private final Boolean active;
  private final Set<String> restrictToCodes; // nullable -> no restriction; empty -> matches nothing

  private UrlMappingFilter(String codePart,
                           String urlPart,
                           Instant createdFrom,
                           Instant createdTo,
                           Integer offset,
                           Integer limit,
                           SortBy sortBy,
                           Direction direction,
                           Boolean active,
                           Set<String> restrictToCodes) {
    this.codePart = codePart;
    this.urlPart = urlPart;
    this.createdFrom = createdFrom;
    this.createdTo = createdTo;
    this.offset = offset;
    this.limit = limit;
    this.sortBy = sortBy;
    this.direction = direction;
    this.active = active;
    this.restrictToCodes = restrictToCodes;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Optional<String> codePart() {
    return Optional.ofNullable(codePart);
  }

  public Optional<String> urlPart() {
    return Optional.ofNullable(urlPart);
  }

  public Optional<Instant> createdFrom() {
    return Optional.ofNullable(createdFrom);
  }

  public Optional<Instant> createdTo() {
    return Optional.ofNullable(createdTo);
  }

  public Optional<Integer> offset() {
    return Optional.ofNullable(offset);
  }

  public Optional<Integer> limit() {
    return Optional.ofNullable(limit);
  }

  public Optional<SortBy> sortBy() {
    return Optional.ofNullable(sortBy);
  }

  public Optional<Direction> direction() {
    return Optional.ofNullable(direction);
  }

  public Optional<Boolean> active() {
    return Optional.ofNullable(active);
  }

  /**
   * Optional whitelist of short codes the result is restricted to (e.g. the
   * links carrying a given tag). {@code empty} present set matches nothing;
   * absent means no restriction.
   */
  public Optional<Set<String>> restrictToCodes() {
    return Optional.ofNullable(restrictToCodes);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UrlMappingFilter f)) return false;
    return
        Objects.equals(codePart, f.codePart) &&
        Objects.equals(urlPart, f.urlPart) &&
        Objects.equals(createdFrom, f.createdFrom) &&
        Objects.equals(createdTo, f.createdTo) &&
        Objects.equals(offset, f.offset) &&
        Objects.equals(limit, f.limit) &&
        Objects.equals(active, f.active) &&
        Objects.equals(restrictToCodes, f.restrictToCodes) &&
        sortBy == f.sortBy &&
        direction == f.direction;
  }

  @Override
  public int hashCode() {
    return Objects.hash(codePart, urlPart,
                        createdFrom, createdTo, offset, limit, sortBy, direction, active, restrictToCodes);
  }

  @Override
  public String toString() {
    var sb = new StringBuilder("UrlMappingFilter{");

    if (codePart != null) {
      sb.append(CODE_PART + "='").append(codePart).append('\'');
    }

    if (urlPart != null) {
      if (sb.length() > 19) sb.append(", ");
      sb.append(URL_PART + "='").append(urlPart).append('\'');
    }

    if (createdFrom != null) {
      if (sb.length() > 19) sb.append(", ");
      sb.append(CREATED_FROM + "=").append(createdFrom);
    }

    if (createdTo != null) {
      if (sb.length() > 19) sb.append(", ");
      sb.append(CREATED_TO + "=").append(createdTo);
    }

    if (offset != null || limit != null) {
      if (sb.length() > 19) sb.append(", ");
      sb.append(PAGING + "=");
      if (offset != null) sb.append(OFFSET + "=").append(offset);
      if (offset != null && limit != null) sb.append(", ");
      if (limit != null) sb.append(LIMIT + "=").append(limit);
    }

    if (sortBy != null) {
      if (sb.length() > 19) sb.append(", ");
      sb.append(SORT_BY + "=").append(sortBy);
    }

    if (direction != null) {
      if (sb.length() > 19) sb.append(", ");
      sb.append(DIRECTION + "=").append(direction);
    }

    if (active != null) {
      if (sb.length() > 19) sb.append(", ");
      sb.append(ACTIVE + "=").append(active);
    }

    sb.append('}');
    return sb.toString();
  }


  public enum SortBy { CREATED_AT, SHORT_CODE, ORIGINAL_URL, EXPIRES_AT }

  public enum Direction { ASC, DESC }

  public static final class Builder {
    private String codePart;
    private String urlPart;

    private Instant createdFrom;
    private Instant createdTo;

    private Integer offset;
    private Integer limit;

    private SortBy sortBy;
    private Direction direction;

    private Boolean active;

    private Set<String> restrictToCodes;

    /**
     * Substring for shortcode; preprocessed with AliasPolicy.normalize.
     */
    public Builder codePart(String codePart) {
      if (codePart != null && !codePart.isBlank()) {
        var norm = AliasPolicy.normalize(codePart);
        if (!norm.isBlank()) this.codePart = norm;
      }
      return this;
    }


    /**
     * Substring for original URL.
     */
    public Builder urlPart(String urlPart) {
      if (urlPart != null && !urlPart.isBlank()) this.urlPart = urlPart;
      return this;
    }


    public Builder createdFrom(Instant from) {
      this.createdFrom = from;
      return this;
    }

    public Builder createdTo(Instant to) {
      this.createdTo = to;
      return this;
    }

    /**
     * Paging: offset>=0, limit>0
     */
    public Builder offset(Integer off) {
      this.offset = off;
      return this;
    }

    public Builder limit(Integer lim) {
      this.limit = lim;
      return this;
    }

    public Builder sortBy(SortBy s) {
      this.sortBy = s;
      return this;
    }

    public Builder direction(Direction d) {
      this.direction = d;
      return this;
    }

    public Builder active(Boolean a) {
      this.active = a;
      return this;
    }

    /**
     * Restricts the result to this set of short codes (e.g. the links of a
     * tag). A non-null empty set matches nothing; null means no restriction.
     */
    public Builder restrictToCodes(Set<String> codes) {
      this.restrictToCodes = codes;
      return this;
    }

    public UrlMappingFilter build() {
      return new UrlMappingFilter(codePart, urlPart,
                                  createdFrom, createdTo, offset, limit, sortBy, direction, active, restrictToCodes);
    }
  }
}
