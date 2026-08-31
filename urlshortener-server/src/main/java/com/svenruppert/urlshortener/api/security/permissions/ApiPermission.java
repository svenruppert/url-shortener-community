package com.svenruppert.urlshortener.api.security.permissions;

import com.svenruppert.jsentinel.authorization.api.permissions.PermissionName;

/**
 * Permission catalog — the operation ids from PRE-REBUILD-SCOPE.md
 * (Kap. 2). Each entry binds the string id used in
 * {@code @Authorize("...")} annotations to a typed
 * {@link PermissionName} the AuthorizationService produces from the
 * subject's roles.
 */
public enum ApiPermission {

  // URL mappings (owner model lands in Stage G)
  CREATE_LINK("createLink"),
  EDIT_LINK("editLink"),
  DELETE_LINK("deleteLink"),
  TOGGLE_ACTIVE("toggleActive"),
  LIST_LINKS("listLinks"),
  LIST_LINKS_COUNT("listLinksCount"),
  LINK_READ_ALL("link:read:all"),
  LINK_OWNER_MANAGE("linkOwnerManage"),

  // Link campaigns / tags / labels
  CAMPAIGN_MANAGE("campaignManage"),

  // Bulk
  BULK_CREATE_LINKS("bulkCreateLinks"),
  BULK_VALIDATE_LINKS("bulkValidateLinks"),

  // Import (URL mappings)
  IMPORT_VALIDATE("importValidate"),
  IMPORT_APPLY("importApply"),
  IMPORT_CONFLICTS("importConflicts"),
  IMPORT_INVALID("importInvalid"),

  // Statistics
  STATISTICS_COUNT("statisticsCount"),
  STATISTICS_HOURLY("statisticsHourly"),
  STATISTICS_DAILY("statisticsDaily"),
  STATISTICS_TIMELINE("statisticsTimeline"),
  STATISTICS_CONFIG("statisticsConfig"),
  STATISTICS_DEBUG("statisticsDebug"),
  STATISTICS_EXPORT("statisticsExport"),
  STATISTICS_IMPORT("statisticsImport"),

  // Column preferences
  PREFERENCES_COLUMNS("preferencesColumns"),
  PREFERENCES_COLUMNS_BULK("preferencesColumnsBulk"),
  PREFERENCES_COLUMNS_SINGLE("preferencesColumnsSingle"),

  // Admin
  STORE_INFO("storeInfo"),
  BACKUP_MANAGE("backupManage"),
  /**
   * Subscribe to the signed security-event stream. The stream itself ships as
   * an enterprise plug-in; the permission lives here because the role table is
   * community-owned — same split as {@link #BACKUP_MANAGE}. Admin-only: the
   * feed carries every subject's security events, so it must never reach the
   * USER set.
   */
  EVENTS_SUBSCRIBE("events:subscribe"),
  LIST_AUDIT("listAudit"),
  LIST_USERS("listUsers"),
  BULK_CREATE_USERS("bulkCreateUsers"),
  EXPORT_USERS("exportUsers"),
  IMPORT_USERS("importUsers");

  private final PermissionName permissionName;

  ApiPermission(String value) {
    this.permissionName = new PermissionName(value);
  }

  public PermissionName permissionName() {
    return permissionName;
  }

  public String id() {
    return permissionName.value();
  }
}
