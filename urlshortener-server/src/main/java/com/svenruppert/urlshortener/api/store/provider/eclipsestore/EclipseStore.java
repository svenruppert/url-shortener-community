package com.svenruppert.urlshortener.api.store.provider.eclipsestore;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.security.ApiKeyStore;
import com.svenruppert.urlshortener.api.security.LinkOwnershipStore;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.security.roles.AuthorizationRole;
import com.svenruppert.urlshortener.api.extension.LinkMetaStorage;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseApiKeyStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseLinkOwnershipStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseLinkMetaStorage;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipsePreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseStatisticsStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseUrlMappingStore;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.types.StorageManager;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class EclipseStore
    implements HasLogger, Closeable {

  private final StorageManager storage;

  private final EclipseUrlMappingStore eclipseUrlMappingStore;
  private final EclipsePreferencesStore preferencesStore;
  private final EclipseStatisticsStore statisticsStore;
  private final EclipseLinkMetaStorage linkMetaStorage;


  public EclipseStore(String storageDir,
                      ShortCodeGenerator generator,
                      Clock clock) {

    var storagePath = Paths.get(storageDir);
    logger().info("StoragePath is '{}'", storagePath.toAbsolutePath());
    try {
      Files.createDirectories(storagePath);
    } catch (Exception ignored) {
      logger().info("createDirectories ... {}", ignored);
    }

    this.storage = EmbeddedStorage.start(storagePath);

    DataRoot r = dataRoot();
    if (r != null) {
      logger().info("Found existing DataRoot");
      var size = r.shortUrlMappings().size();
      logger().info("DataRoot Mappings contains {} elements", size);
      // Ensure statistics fields are initialized (for backwards compatibility)
      if (r.ensureStatisticsInitialized()) {
        logger().info("Initialized missing statistics/api-key fields in existing DataRoot");
        storage.store(r.redirectEvents());
        storage.store(r.hourlyAggregates());
        storage.store(r.dailyAggregates());
        storage.store(r.statisticsConfig());
        storage.store(r.apiKeys());
        storage.store(r.campaigns());
        storage.store(r.campaignOfLink());
        storage.store(r.linkTags());
        storage.store(r.linkLabels());
        storage.store(r.knownTags());
        storage.store(r.linkOwners());
        storage.storeRoot();
      }
    } else {
      logger().info("No DataRoot found, creating a new one...");
      storage.setRoot(new DataRoot());
      storage.storeRoot();
    }

    this.eclipseUrlMappingStore = new EclipseUrlMappingStore(storage, clock, generator);
    this.preferencesStore = new EclipsePreferencesStore(storage);
    this.statisticsStore = new EclipseStatisticsStore(storage, clock);

    // Route API-key storage through EclipseStore so keys survive a restart.
    ApiKeyStore.INSTANCE.useBackend(new EclipseApiKeyStore(storage));

    // Route link ownership through EclipseStore so the owner boundary survives
    // a restart (V00.17.00 P020), then run the one-time backfill (D1).
    LinkOwnershipStore.INSTANCE.useBackend(new EclipseLinkOwnershipStore(storage));
    backfillOwnershipToFirstAdmin(dataRoot());

    this.linkMetaStorage = new EclipseLinkMetaStorage(storage);
  }

  /**
   * One-time migration (V00.17.00 concept, DECISION 1): links created before
   * ownership persistence existed have no recoverable owner — they are assigned
   * to the first admin, who redistributes via the admin reassign endpoint.
   * Runs only while NO ownership is recorded at all; a later start with a
   * partially-filled map never reassigns anything.
   */
  private void backfillOwnershipToFirstAdmin(DataRoot r) {
    if (!LinkOwnershipStore.INSTANCE.isEmpty() || r.shortUrlMappings().isEmpty()) {
      return;
    }
    Optional<ApiUser> firstAdmin = UserDirectoryProvider.directory().all()
        .filter(u -> u.roles().contains(AuthorizationRole.ADMIN))
        .min(Comparator.comparing(ApiUser::id));
    if (firstAdmin.isEmpty()) {
      logger().warn("ownership backfill skipped — no admin in the user directory yet "
          + "({} unowned mappings remain admin-only until the next start)",
          r.shortUrlMappings().size());
      return;
    }
    Long adminId = firstAdmin.get().id();
    Map<String, Long> assignment = new HashMap<>();
    r.shortUrlMappings().keySet().forEach(code -> assignment.put(code, adminId));
    LinkOwnershipStore.INSTANCE.recordAll(assignment);
    logger().info("ownership backfill: assigned {} pre-existing links to first admin '{}' (id {})",
        assignment.size(), firstAdmin.get().name(), adminId);
  }

  /** Raw link-meta maps for the plug-in seam (V00.18.00 P085) — dormant without a plug-in. */
  public LinkMetaStorage getLinkMetaStorage() {
    return linkMetaStorage;
  }

  public UrlMappingStore getUrlMappingStore() {
    return eclipseUrlMappingStore;
  }

  public PreferencesStore getPreferencesStore() {
    return preferencesStore;
  }

  public StatisticsStore getStatisticsStore() {
    return statisticsStore;
  }

  /**
   * Starts background processing threads for statistics.
   * Should be called once during application startup.
   */
  public void start() {
    logger().info("Starting EclipseStore services");
    statisticsStore.start();
  }

  private DataRoot dataRoot() {
    return (DataRoot) storage.root();
  }


  /* ---------- internal ---------- */

  @Override
  public void close() {
    try {
      logger().info("Closing EclipseStore");
      // Drop the backends that point at this (about to be closed) storage,
      // so the global singletons never dangle at a closed store.
      ApiKeyStore.INSTANCE.resetToInMemory();
      LinkOwnershipStore.INSTANCE.resetToInMemory();
      statisticsStore.stop();
      storage.close();
    } catch (Exception e) {
      logger().warn("Storage close failed", e);
    }
  }
}
