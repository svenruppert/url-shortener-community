package com.svenruppert.urlshortener.api;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authentication.AuthenticationService;
import com.svenruppert.jsentinel.authorization.api.AuthorizationService;
import com.svenruppert.jsentinel.dx.rest.bootstrap.RestSecurity;
import com.svenruppert.jsentinel.dx.runtime.JSentinelBootstrapMode;
import com.svenruppert.jsentinel.dx.runtime.JSentinelRuntime;
import com.svenruppert.urlshortener.api.filter.CorsPreflightFilter;
import com.svenruppert.urlshortener.api.filter.SecurityHeadersFilter;
import com.svenruppert.urlshortener.api.handler.RedirectHandler;
import com.svenruppert.urlshortener.api.handler.auth.BootstrapSetupHandler;
import com.svenruppert.urlshortener.api.handler.auth.BootstrapStatusHandler;
import com.svenruppert.urlshortener.api.handler.admin.AuditHandler;
import com.svenruppert.urlshortener.api.handler.admin.users.UsersHandler;
import com.svenruppert.urlshortener.api.handler.auth.LoginHandler;
import com.svenruppert.urlshortener.api.handler.auth.LogoutHandler;
import com.svenruppert.urlshortener.api.handler.auth.LogoutAllHandler;
import com.svenruppert.urlshortener.api.handler.auth.ApiKeysHandler;
import com.svenruppert.urlshortener.api.handler.auth.MeRefreshTokenHandler;
import com.svenruppert.urlshortener.api.handler.auth.RefreshHandler;
import com.svenruppert.urlshortener.api.handler.auth.PasswordResetHandler;
import com.svenruppert.urlshortener.api.handler.admin.HealthHandler;
import com.svenruppert.urlshortener.api.handler.admin.SessionsHandler;
import com.svenruppert.urlshortener.api.handler.admin.AliasReserveHandler;
import com.svenruppert.urlshortener.api.handler.admin.LinkOwnerHandler;
import com.svenruppert.urlshortener.api.handler.auth.MeHandler;
import com.svenruppert.urlshortener.api.handler.auth.MePasswordHandler;
import com.svenruppert.urlshortener.api.security.ApiRestSubjectResolver;
import com.svenruppert.urlshortener.api.security.SecurityHttpHandler;
import com.svenruppert.urlshortener.api.security.bootstrap.BootstrapBuilder;
import com.svenruppert.urlshortener.api.security.bootstrap.BootstrapWiring;
import com.svenruppert.urlshortener.api.handler.admin.StoreInfoHandler;
import com.svenruppert.urlshortener.api.handler.admin.columns.ColumnVisibilityBulkHandler;
import com.svenruppert.urlshortener.api.handler.admin.columns.ColumnVisibilityHandler;
import com.svenruppert.urlshortener.api.handler.admin.columns.ColumnVisibilitySingleHandler;
import com.svenruppert.urlshortener.api.handler.statistics.*;
import com.svenruppert.urlshortener.api.handler.statistics.exports.StatisticsExportHandler;
import com.svenruppert.urlshortener.api.handler.statistics.imports.StatisticsImportHandler;
import com.svenruppert.urlshortener.api.handler.urlmapping.*;
import com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportApplyHandler;
import com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportConflictsListHandler;
import com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportInvalidListHandler;
import com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportValidateHandler;
import com.svenruppert.urlshortener.api.store.imports.ImportStagingStore;
import com.svenruppert.urlshortener.api.store.imports.InMemoryImportStagingStore;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.EclipseStore;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryPreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryStatisticsStore;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryUrlMappingStore;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.svenruppert.urlshortener.core.DefaultValues.*;

public class ShortenerServer
    implements HasLogger {

  /** jSentinel installs once per JVM — tests start several servers. */
  private static final AtomicBoolean SECURITY_BOOTSTRAPPED = new AtomicBoolean();

  private HttpServer serverRedirect;
  private HttpServer serverAdmin;

  public static void main(String[] args)
      throws IOException {
    // Defaults (Fallbacks)
    String host = DEFAULT_SERVER_HOST;
    int port = DEFAULT_SERVER_PORT;
    HasLogger.staticLogger().info("Main called with arguments: {}", Arrays.toString(args));

    // Read parameters from CLI
    if (args.length > 0 && !args[0].isBlank()) {
      host = args[0].trim();
      HasLogger.staticLogger().info("Host is {}", host);
    }
    if (args.length > 1) {
      try {
        port = Integer.parseInt(args[1].trim());
      } catch (NumberFormatException e) {
        HasLogger.staticLogger().warn("Invalid port argument: {} - using default {}", args[1], port);
      }
    }


    boolean persistent = true;
    new ShortenerServer().init(host, port, persistent);
  }

  public void init()
      throws IOException {
    logger().info("Starting URL Shortener server with default parameters...");
    init(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
  }

  public void init(String hostRedirect, int portRedirect)
      throws IOException {
    init(hostRedirect, portRedirect, false);
  }

  public void init(String hostRedirect, int portRedirect, boolean persistent)
      throws IOException {
    logger().info("Starting server with urlMappingStore - persistent={}", persistent);
    // Open-core seam (V00.18.00 P090): startup contributions run BEFORE the
    // security bootstrap opens the jSentinel storages and before the app
    // store opens — the only window in which a plug-in may touch the raw
    // storage directories (restore-at-startup). First init per JVM only;
    // a contribution failure is logged and never blocks startup.
    if (!SECURITY_BOOTSTRAPPED.get()) {
      for (var contribution : java.util.ServiceLoader.load(
          com.svenruppert.urlshortener.api.extension.StartupContribution.class)) {
        try {
          contribution.beforeStoresOpen();
        } catch (RuntimeException e) {
          logger().error("startup contribution {} failed — continuing startup",
              contribution.getClass().getName(), e);
        }
      }
    }
    bootstrapSecurity();
    final long startedAt = System.currentTimeMillis();
    var shortCodeGenerator = new ShortCodeGenerator(1);

    UrlMappingStore urlMappingStore;
    PreferencesStore preferencesStore;
    StatisticsStore statisticsStore;
    com.svenruppert.urlshortener.api.extension.LinkMetaStorage linkMetaStorage;
    com.svenruppert.urlshortener.api.extension.StorageAdmin storageAdmin;
    ImportStagingStore importStagingStore = new InMemoryImportStagingStore();

    if (persistent) {
      var eclipseStore = new EclipseStore(
          STORAGE_DATA_PATH,
          shortCodeGenerator,
          Clock.systemUTC()
      );
      urlMappingStore = eclipseStore.getUrlMappingStore();
      preferencesStore = eclipseStore.getPreferencesStore();
      statisticsStore = eclipseStore.getStatisticsStore();
      linkMetaStorage = eclipseStore.getLinkMetaStorage();
      storageAdmin = new com.svenruppert.urlshortener.api.extension.StorageAdmin() {
        @Override
        public boolean persistent() {
          return true;
        }

        @Override
        public java.nio.file.Path appDataDir() {
          return java.nio.file.Paths.get(STORAGE_DATA_PATH).toAbsolutePath();
        }

        @Override
        public void issueAppStoreBackup(java.nio.file.Path targetDirectory) {
          eclipseStore.issueFullBackup(targetDirectory);
        }
      };

      // Start background threads for statistics processing
      eclipseStore.start();
      logger().info("EclipseStore started with statistics processing");

      logger().info("Registering shutdown hook for EclipseStore...");
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        shutdown();
        logger().info("Closing EclipseStore...");
        try {
          eclipseStore.close();
        } catch (Exception ignored) {
        }
        logger().info("Server shutdown complete");
      }));
    } else {
      urlMappingStore = new InMemoryUrlMappingStore(shortCodeGenerator);
      preferencesStore = new InMemoryPreferencesStore();
      statisticsStore = new InMemoryStatisticsStore(Clock.systemUTC());
      linkMetaStorage = new com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryLinkMetaStorage();
      storageAdmin = new com.svenruppert.urlshortener.api.extension.StorageAdmin() {
        @Override
        public boolean persistent() {
          return false;
        }

        @Override
        public java.nio.file.Path appDataDir() {
          return null;
        }

        @Override
        public void issueAppStoreBackup(java.nio.file.Path targetDirectory) {
          throw new IllegalStateException("in-memory profile has no persistent app store");
        }
      };
    }

    // Open-core seam (V00.18.00 P085): the plug-in context and the link
    // lifecycle hooks are discovered BEFORE handler registration — the
    // community handlers only ever talk to the hook facade, never to a
    // feature store. A plug-in-free classpath yields empty hooks.
    var serverContext = new com.svenruppert.urlshortener.api.extension.ServerContext(
        urlMappingStore, linkMetaStorage, statisticsStore, preferencesStore, storageAdmin);
    var lifecycleHooks = com.svenruppert.urlshortener.api.extension.LinkLifecycleHooks.discover(serverContext);

    logger().info("Starting URL Shortener server (redirect) with parameters: host={}, port={}", hostRedirect, portRedirect);
    this.serverRedirect = HttpServer.create(new InetSocketAddress(hostRedirect, portRedirect), 0);
    serverRedirect.createContext(PATH_REDIRECT, new RedirectHandler(urlMappingStore, statisticsStore))
        .getFilters().add(new SecurityHeadersFilter());

    logger().info("Starting URL Shortener server (admin) with parameters: host={}, port={}", ADMIN_SERVER_HOST, ADMIN_SERVER_PORT);
    this.serverAdmin = HttpServer.create(new InetSocketAddress(ADMIN_SERVER_HOST, ADMIN_SERVER_PORT), 0);

    // First-admin bootstrap (PRE-REBUILD-SCOPE Kap. 2.8) — public,
    // must be reachable while the 503 guard blocks everything else.
    open(serverAdmin, PATH_BOOTSTRAP_STATUS, new BootstrapStatusHandler());
    open(serverAdmin, PATH_SETUP, new BootstrapSetupHandler());

    // Authentication & self-service (PRE-REBUILD-SCOPE Kap. 2.6)
    open(serverAdmin, PATH_AUTH_LOGIN, new LoginHandler());
    open(serverAdmin, PATH_AUTH_LOGOUT, new LogoutHandler());
    open(serverAdmin, PATH_AUTH_REFRESH, new RefreshHandler());
    open(serverAdmin, PATH_PASSWORD_RESET, new PasswordResetHandler());
    secured(serverAdmin, PATH_AUTH_LOGOUT_ALL, new LogoutAllHandler());
    secured(serverAdmin, PATH_AUTH_ME_PASSWORD, new MePasswordHandler());
    secured(serverAdmin, PATH_AUTH_ME, new MeHandler());
    secured(serverAdmin, PATH_ME_API_KEYS, new ApiKeysHandler());
    secured(serverAdmin, PATH_ME_REFRESH_TOKEN, new MeRefreshTokenHandler());

    // Admin: user management + audit (PRE-REBUILD-SCOPE Kap. 2.7 / 2.8)
    secured(serverAdmin, PATH_ADMIN_USERS, new UsersHandler());
    secured(serverAdmin, PATH_ADMIN_AUDIT, new AuditHandler());

    secured(serverAdmin, PATH_ADMIN_VALIDATE_BULK, new BulkValidateHandler(urlMappingStore));
    secured(serverAdmin, PATH_ADMIN_SHORTEN_BULK, new BulkShortenHandler(urlMappingStore, lifecycleHooks));
    secured(serverAdmin, PATH_ADMIN_SHORTEN, new ShortenHandler(urlMappingStore, lifecycleHooks));
    secured(serverAdmin, PATH_ADMIN_LIST, new ListHandler(urlMappingStore, lifecycleHooks));
    secured(serverAdmin, PATH_ADMIN_LIST_COUNT, new ListCountHandler(urlMappingStore, lifecycleHooks));
    secured(serverAdmin, PATH_ADMIN_EDIT, new EditMappingHandler(urlMappingStore));
    secured(serverAdmin, PATH_ADMIN_DELETE, new DeleteMappingHandler(urlMappingStore, lifecycleHooks));
    secured(serverAdmin, PATH_ADMIN_TOGGLE_ACTIVE, new ToggleActiveHandler(urlMappingStore));
    secured(serverAdmin, PATH_ADMIN_STORE_INFO, new StoreInfoHandler(urlMappingStore, startedAt));
    secured(serverAdmin, PATH_ADMIN_HEALTH, new HealthHandler());
    secured(serverAdmin, PATH_ADMIN_SESSIONS, new SessionsHandler());
    secured(serverAdmin, PATH_ADMIN_ALIASES, new AliasReserveHandler());
    secured(serverAdmin, PATH_ADMIN_LINK_OWNER, new LinkOwnerHandler(urlMappingStore));

    secured(serverAdmin, PATH_ADMIN_IMPORT_VALIDATE, new ImportValidateHandler(urlMappingStore, importStagingStore));
    secured(serverAdmin, PATH_ADMIN_IMPORT_APPLY, new ImportApplyHandler(urlMappingStore, importStagingStore));
    secured(serverAdmin, PATH_ADMIN_IMPORT_CONFLICTS, new ImportConflictsListHandler(importStagingStore));
    secured(serverAdmin, PATH_ADMIN_IMPORT_INVALID, new ImportInvalidListHandler(importStagingStore));

    secured(serverAdmin, PATH_ADMIN_PREFERENCES_COLUMNS, new ColumnVisibilityHandler(preferencesStore));
    secured(serverAdmin, PATH_ADMIN_PREFERENCES_COLUMNS_EDIT, new ColumnVisibilityBulkHandler(preferencesStore));
    secured(serverAdmin, PATH_ADMIN_PREFERENCES_COLUMNS_SINGLE, new ColumnVisibilitySingleHandler(preferencesStore));

    secured(serverAdmin, PATH_ADMIN_STATISTICS_COUNT, new StatisticsCountHandler(statisticsStore));
    secured(serverAdmin, PATH_ADMIN_STATISTICS_HOURLY, new StatisticsHourlyHandler(statisticsStore));
    secured(serverAdmin, PATH_ADMIN_STATISTICS_DAILY, new StatisticsDailyHandler(statisticsStore));
    secured(serverAdmin, PATH_ADMIN_STATISTICS_TIMELINE, new StatisticsTimelineHandler(statisticsStore));
    secured(serverAdmin, PATH_ADMIN_STATISTICS_CONFIG, new StatisticsConfigHandler(statisticsStore));
    secured(serverAdmin, PATH_ADMIN_STATISTICS_DEBUG, new StatisticsDebugHandler(statisticsStore));
    secured(serverAdmin, PATH_ADMIN_STATISTICS_EXPORT, new StatisticsExportHandler(statisticsStore));
    secured(serverAdmin, PATH_ADMIN_STATISTICS_IMPORT, new StatisticsImportHandler(statisticsStore));
    logger().info("Statistics API handlers registered");

    // Open-core seam (V00.18.00 P030): endpoints contributed by plug-in jars
    // via ServiceLoader. They run behind the SAME security pipeline
    // (secured() -> SecurityHttpHandler with @RequiresPermission) and against
    // the same stores; duplicate plug-in paths abort in the registry, a
    // collision with a community route aborts in createContext — fail fast
    // either way. A plug-in-free classpath makes this a no-op.
    for (var contribution : com.svenruppert.urlshortener.api.extension.HandlerRegistry.discover()) {
      secured(serverAdmin, contribution.path(), contribution.create(serverContext));
      logger().info("Contributed endpoint registered at {}", contribution.path());
    }


    var execRedirect = Executors.newVirtualThreadPerTaskExecutor();
    serverRedirect.setExecutor(execRedirect);
    serverRedirect.start();

    var execAdmin = Executors.newVirtualThreadPerTaskExecutor();
    serverAdmin.setExecutor(execAdmin);
    serverAdmin.start();

    logger().info("URL Shortener server (redirect) running at {}:{}",
                  serverRedirect.getAddress().getHostName(),
                  serverRedirect.getAddress().getPort());
    logger().info("URL Shortener server (admin) running at {}:{}",
                  serverAdmin.getAddress().getHostName(),
                  serverAdmin.getAddress().getPort());


    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  /**
   * Installs the jSentinel REST stack once per JVM: subject
   * resolution from Bearer tokens plus the additive
   * {@code BootstrapExtension} contributions (audit, sessions,
   * credentials) — later stages plug in via the SPI, this entry
   * point stays untouched.
   */
  private void bootstrapSecurity() {
    if (!SECURITY_BOOTSTRAPPED.compareAndSet(false, true)) {
      return;
    }
    // Discover the @JSentinelAutoService-registered Authn/Authz via SPI
    // and pass them explicitly — RestSecurity.bootstrap() does not
    // auto-discover them, and without this the resolver has no
    // AuthenticationService and every login is rejected.
    AuthenticationService<?, ?> authn = ServiceLoader.load(AuthenticationService.class)
        .findFirst().orElse(null);
    AuthorizationService<?> authz = ServiceLoader.load(AuthorizationService.class)
        .findFirst().orElse(null);
    if (authn == null || authz == null) {
      throw new IllegalStateException(
          "jSentinel bootstrap requires both AuthenticationService and "
              + "AuthorizationService via @JSentinelAutoService SPI. Found: "
              + "authn=" + authn + ", authz=" + authz
              + ". Check the annotation processor in maven-compiler-plugin.");
    }

    JSentinelRuntime runtime = BootstrapBuilder.apply(
        RestSecurity.bootstrap()
            .mode(switch (com.svenruppert.urlshortener.core.SecurityProfile.current()) {
              case PRODUCTION -> JSentinelBootstrapMode.PRODUCTION;
              case STRICT -> JSentinelBootstrapMode.STRICT;
              case DEVELOPMENT -> JSentinelBootstrapMode.DEVELOPMENT;
            })
            .authentication(authn)
            .authorization(authz)
            .subjectResolver(new ApiRestSubjectResolver())
    ).install();
    com.svenruppert.urlshortener.api.security.SecurityDiagnostics.set(runtime);
    logger().info("{}", runtime.log());

    // Generate/print the one-time bootstrap token if no admin exists yet.
    // Runs after install() so the credentials + storage sub-builders are
    // configured before the user directory is first touched.
    BootstrapWiring.instance();
  }

  /** Registers a Bearer-protected context (permission via @Authorize). */
  private static void secured(HttpServer server, String path, HttpHandler handler) {
    var filters = server.createContext(path, SecurityHttpHandler.secure(handler)).getFilters();
    filters.add(new SecurityHeadersFilter());
    filters.add(new CorsPreflightFilter());
    filters.add(new com.svenruppert.urlshortener.api.filter.MaintenanceFilter());
  }

  /** Registers a public context (login/logout). */
  private static void open(HttpServer server, String path, HttpHandler handler) {
    var filters = server.createContext(path, handler).getFilters();
    filters.add(new SecurityHeadersFilter());
    filters.add(new CorsPreflightFilter());
  }

  public void shutdown() {
    if (serverRedirect != null) {
      serverRedirect.stop(0);
      logger().info("URL Shortener server stopped (redirect)");
    }
    if (serverAdmin != null) {
      serverAdmin.stop(0);
      logger().info("URL Shortener server stopped (admin)");
    }
  }

  public int getPortRedirect() {
    return serverRedirect.getAddress().getPort();
  }

  public int getPortAdmin() {
    return serverAdmin.getAddress().getPort();
  }


}