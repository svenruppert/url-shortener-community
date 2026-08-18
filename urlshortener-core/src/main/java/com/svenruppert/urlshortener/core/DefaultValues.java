package com.svenruppert.urlshortener.core;





public final class DefaultValues {
  //TODO - must be editable by user
  public static final String SHORTCODE_BASE_URL = "https://8g8.eu/";

  public static final int ADMIN_SERVER_PORT = 9090;
  public static final String ADMIN_SERVER_HOST = "localhost";
  public static final String ADMIN_SERVER_PROTOCOL = "http";
  public static final String ADMIN_SERVER_URL = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + ADMIN_SERVER_PORT;

  public static final int DEFAULT_SERVER_PORT = 8081;
  public static final String DEFAULT_SERVER_HOST = "localhost";
  public static final String DEFAULT_SERVER_PROTOCOL = "http";
  public static final String DEFAULT_SERVER_URL = DEFAULT_SERVER_PROTOCOL + "://" + DEFAULT_SERVER_HOST + ":" + DEFAULT_SERVER_PORT;

  public static final String CONTENT_TYPE = "Content-Type";
  public static final String CONTENT_DISPOSITION = "Content-Disposition";
  public static final String ACCEPT = "Accept";


  public static final String PATH_REDIRECT = "/";

  public static final String PATH_PREFIX = "/api";

  // Authentication & self-service (PRE-REBUILD-SCOPE Kap. 2.6)
  public static final String PATH_AUTH_LOGIN = PATH_PREFIX + "/login";
  public static final String PATH_AUTH_LOGOUT = PATH_PREFIX + "/logout";
  public static final String PATH_AUTH_LOGOUT_ALL = PATH_PREFIX + "/logout/all";
  public static final String PATH_AUTH_REFRESH = PATH_PREFIX + "/refresh";
  public static final String PATH_PASSWORD_RESET = PATH_PREFIX + "/password-reset";
  public static final String PATH_AUTH_ME = PATH_PREFIX + "/me";
  public static final String PATH_AUTH_ME_PASSWORD = PATH_PREFIX + "/me/password";
  public static final String PATH_ME_API_KEYS = PATH_PREFIX + "/me/api-keys";
  public static final String PATH_ME_REFRESH_TOKEN = PATH_PREFIX + "/me/refresh-token";

  // Admin: users + audit (PRE-REBUILD-SCOPE Kap. 2.7 / 2.8)
  public static final String PATH_ADMIN_USERS = PATH_PREFIX + "/users";
  public static final String PATH_ADMIN_AUDIT = PATH_PREFIX + "/audit";

  // First-admin bootstrap (PRE-REBUILD-SCOPE Kap. 2.8)
  public static final String PATH_SETUP = PATH_PREFIX + "/setup";
  public static final String PATH_BOOTSTRAP_STATUS = PATH_PREFIX + "/bootstrap/status";

  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String BEARER_PREFIX = "Bearer ";
  /** Header carrying a long-lived API key (alternative to a Bearer token). */
  public static final String API_KEY_HEADER = "X-API-Key";

  public static final String PATH_ADMIN_SHORTEN = PATH_PREFIX + "/shorten";
  public static final String PATH_ADMIN_SHORTEN_BULK = PATH_PREFIX + "/shorten/bulk";
  public static final String PATH_ADMIN_VALIDATE_BULK = PATH_PREFIX + "/validate/bulk";
  public static final String PATH_ADMIN_EDIT = PATH_PREFIX + "/edit";
  public static final String PATH_ADMIN_DELETE = PATH_PREFIX + "/delete";
  public static final String PATH_ADMIN_TOGGLE_ACTIVE = PATH_PREFIX + "/toggleActive";
  public static final String PATH_ADMIN_LIST = PATH_PREFIX + "/list";
  public static final String PATH_ADMIN_LIST_ALL = PATH_PREFIX + "/list/all";
  public static final String PATH_ADMIN_LIST_EXPIRED = PATH_PREFIX + "/list/expired";
  public static final String PATH_ADMIN_LIST_ACTIVE = PATH_PREFIX + "/list/active";
  public static final String PATH_ADMIN_LIST_INACTIVE = PATH_PREFIX + "/list/inactive";
  public static final String PATH_ADMIN_LIST_COUNT = PATH_PREFIX + "/list/count";

  //TODO extract into own handler
  public static final String PATH_ADMIN_EXPORT = PATH_PREFIX + "/list/export";
  public static final String PATH_ADMIN_IMPORT_VALIDATE = PATH_PREFIX + "/list/import/validate";
  public static final String PATH_ADMIN_IMPORT_APPLY = PATH_PREFIX + "/list/import/apply";
  public static final String PATH_ADMIN_IMPORT_CONFLICTS = PATH_PREFIX + "/list/import/staging/conflicts";
  public static final String PATH_ADMIN_IMPORT_INVALID   = PATH_PREFIX + "/list/import/staging/invalid";


  public static final String PATH_ADMIN_STORE_INFO = PATH_PREFIX + "/store/info";
  public static final String PATH_ADMIN_HEALTH = PATH_PREFIX + "/admin/health";
  public static final String PATH_ADMIN_SESSIONS = PATH_PREFIX + "/admin/sessions";
  public static final String PATH_ADMIN_ALIASES = PATH_PREFIX + "/admin/aliases";
  // V00.17.00 P020: admin-only owner reassignment — PUT {this}/{shortCode}
  public static final String PATH_ADMIN_LINK_OWNER = PATH_PREFIX + "/admin/links/owner";

  public static final String PATH_ADMIN_PREFERENCES_COLUMNS = PATH_PREFIX + "/admin/preferences/columns";
  public static final String PATH_ADMIN_PREFERENCES_COLUMNS_EDIT = PATH_PREFIX + "/admin/preferences/columns/edit";
  public static final String PATH_ADMIN_PREFERENCES_COLUMNS_SINGLE = PATH_PREFIX + "/admin/preferences/columns/single";

  // Campaign / tag / label API paths
  public static final String PATH_ADMIN_CAMPAIGNS = PATH_PREFIX + "/campaigns";
  public static final String PATH_ADMIN_LINK_META = PATH_PREFIX + "/link-meta";
  public static final String PATH_ADMIN_TAGS = PATH_PREFIX + "/tags";

  // Statistics API paths
  public static final String PATH_ADMIN_STATISTICS = PATH_PREFIX + "/statistics";
  public static final String PATH_ADMIN_STATISTICS_COUNT = PATH_ADMIN_STATISTICS + "/count";
  public static final String PATH_ADMIN_STATISTICS_TIMELINE = PATH_ADMIN_STATISTICS + "/timeline";
  public static final String PATH_ADMIN_STATISTICS_HOURLY = PATH_ADMIN_STATISTICS + "/hourly";
  public static final String PATH_ADMIN_STATISTICS_DAILY = PATH_ADMIN_STATISTICS + "/daily";
  public static final String PATH_ADMIN_STATISTICS_CONFIG = PATH_ADMIN_STATISTICS + "/config";
  public static final String PATH_ADMIN_STATISTICS_DEBUG = PATH_ADMIN_STATISTICS + "/debug";
  public static final String PATH_ADMIN_STATISTICS_EXPORT = PATH_ADMIN_STATISTICS + "/export";
  public static final String PATH_ADMIN_STATISTICS_IMPORT = PATH_ADMIN_STATISTICS + "/import";

  public static final String STATISTICS_EXPORT_FORMAT_VERSION = "1";
  public static final String STATISTICS_EXPORT_FILE_NAME = "urlshortener-statistics-export";
  public static final String STATISTICS_EXPORT_NDJSON_ENTRY = STATISTICS_EXPORT_FILE_NAME + ".ndjson";
  public static final long STATISTICS_IMPORT_MAX_ZIP_BYTES = 500L * 1024 * 1024;
  public static final long STATISTICS_IMPORT_MAX_NDJSON_BYTES = 4L * 1024 * 1024 * 1024;


  public static final String PATTERN_DATE_TIME = "yyyy.MM.dd HH:mm";
  public static final String PATTERN_DATE_TIME_EXPORT = "yyyy-MM-dd'T'HH-mm-ss'Z'";

  public static final String EXPORT_FORMAT_VERSION = "1";
  public static final String EXPORT_FILE_NAME = "urlshortener-export";
  public static final String EXPORT_DEFAULT_ZIP_FILENAME = EXPORT_FILE_NAME + ".zip";
  public static final String EXPORT_TIMESTAMP_HEADER = "X-Export-TS";
  public static final String EXPORT_TIMESTAMP_PARAM = "exportTs";

  public static final int IMPORT_MAX_ZIP_BYTES = 50 * 1024 * 1024;
  public static final int IMPORT_MAX_JSON_BYTES = 200 * 1024 * 1024;
  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_SIZE = 50;
  public static final int MAX_SIZE     = 500;


  public static final String STORAGE_DATA_PATH = "data";

  private DefaultValues() {
  }
}