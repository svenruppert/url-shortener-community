package com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.DataRoot;
import org.eclipse.store.storage.types.StorageManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EclipsePreferencesStore
    implements PreferencesStore, HasLogger {

  private final StorageManager storage;

  public EclipsePreferencesStore(StorageManager storage) {
    this.storage = storage;
  }

  private DataRoot dataRoot() {
    return (DataRoot) storage.root();
  }

  @Override
  public Map<String, Boolean> load(String userId, String viewId) {
    logger().info("load user: {} , view: {}", userId, viewId);

    var byUser = columnVisibilities().get(userId);
    if (byUser == null) {
      logger().info("no preference for user {}", userId);
      return Map.of();
    }
    var byView = byUser.get(viewId);
    if (byView == null) {
      logger().info("no preference for user {} and view {}", userId, viewId);
      return Map.of();
    }
    return Map.copyOf(byView);
  }

  private Map<String, Map<String, Map<String, Boolean>>> columnVisibilities() {
    return dataRoot().columnVisibilityPreferences();
  }

  @Override
  public void saveColumnVisibilities(String userId, String viewId, Map<String, Boolean> visibility) {
    columnVisibilities()
        .computeIfAbsent(userId, __ -> new ConcurrentHashMap<>())
        .put(viewId, new ConcurrentHashMap<>(visibility));
    storage.store(columnVisibilities());
  }

  @Override
  public void delete(String userId, String viewId, String columnKey) {
    logger().info("delete - viewId {} for userId {} and columnKey {}", userId, viewId, columnKey);
    var userColumnVisibilities = columnVisibilities().get(userId);
    if (userColumnVisibilities != null) {
      var viewVisibilities = userColumnVisibilities.get(viewId);
      if (viewVisibilities == null) {
        logger().info("no viewId {} found for userId {}", viewId, userId);
      } else {
        var containsKey = viewVisibilities.containsKey(columnKey);
        if (containsKey) viewVisibilities.remove(columnKey);
        else logger().info("no def for column {} found..", columnKey);
      }
    } else {
      logger().info("delete - no column preferences for userId {}", userId);
    }
  }

  @Override
  public void delete(String userId, String viewId) {
    logger().info("delete - viewId {} for userId {}", userId, viewId);
    var userColumnVisibilities = columnVisibilities().get(userId);
    if (userColumnVisibilities != null) {
      if (userColumnVisibilities.remove(viewId) == null) {
        logger().info("no viewId {} found for userId {}", viewId, userId);
      } else {
        storage.store(columnVisibilities());
      }
    } else {
      logger().info("delete - no column preferences for userId {}", userId);
    }
  }

  @Override
  public void delete(String userId) {
    logger().info("delete - userId {} ", userId);
    if (columnVisibilities().remove(userId) == null) {
      logger().info("no userId {} found for deletion", userId);
    } else {
      storage.store(columnVisibilities());
    }
  }
}
