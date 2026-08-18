package com.svenruppert.urlshortener.api.store.provider.inmemory;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPreferencesStore
    implements PreferencesStore, HasLogger {

  // Structure: userId -> viewId -> columnKey -> visible?
  public final Map<String, Map<String, Map<String, Boolean>>> columnVisibilityByUserAndView
      = new ConcurrentHashMap<>();

  @Override
  public Map<String, Boolean> load(String userId, String viewId) {
    logger().info("load user: {} , view: {}", userId, viewId);
    var byUser = columnVisibilityByUserAndView.get(userId);
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

  @Override
  public void saveColumnVisibilities(String userId, String viewId, Map<String, Boolean> visibility) {
    columnVisibilityByUserAndView
        .computeIfAbsent(userId, __ -> new ConcurrentHashMap<>())
        .put(viewId, new ConcurrentHashMap<>(visibility));
  }

  @Override
  public void delete(String userId, String viewId, String columnKey) {
    logger().info("delete - viewId {} for userId {} and columnKey {}", userId, viewId, columnKey);
    var userColumnVisibilities = columnVisibilityByUserAndView.get(userId);
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
    var userColumnVisibilities = columnVisibilityByUserAndView.get(userId);
    if (userColumnVisibilities != null) {
      if (userColumnVisibilities.remove(viewId) == null) {
        logger().info("no viewId {} found for userId {}", viewId, userId);
      }
    } else {
      logger().info("delete - no column preferences for userId {}", userId);
    }
  }

  @Override
  public void delete(String userId) {
    logger().info("delete - userId {} ", userId);
    columnVisibilityByUserAndView.remove(userId);
  }
}
