package com.svenruppert.urlshortener.ui.vaadin.components;



import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.AdminClient;
import com.svenruppert.urlshortener.core.StoreInfo;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreConnectionChanged;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreEvents;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreMode;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.server.Command;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shows the backend connection status by color: connected via EclipseStore
 * (green) or InMemory (blue), disconnected (red), or a connection attempt in
 * progress (amber). The store-info probe runs off the UI thread (Vaadin push),
 * so the "connecting" state is visible while (re)connecting; it is shown only
 * on connect/reconnect, never on every steady 10 s poll.
 */
@CssImport("./styles/store-indicator.css")
public class StoreIndicator
    extends HorizontalLayout
    implements HasLogger, I18nSupport {

  // CSS classes
  private static final String CLASS_ROOT = "store-indicator";
  private static final String CLASS_ICON = "store-indicator__icon";
  private static final String CLASS_BADGE = "store-indicator__badge";
  private static final String CLASS_DETAILS = "store-indicator__details";
  private static final String STATE_ECLIPSE = "store-indicator--eclipse";
  private static final String STATE_MEMORY = "store-indicator--memory";
  private static final String STATE_DOWN = "store-indicator--unavailable";
  private static final String STATE_CONNECTING = "store-indicator--connecting";
  // i18n keys
  private static final String NS = "storeIndicator";
  private static final String K_BADGE_ECLIPSE = NS + ".badge.eclipse";
  private static final String K_BADGE_MEMORY = NS + ".badge.memory";
  private static final String K_BADGE_UNAVAILABLE = NS + ".badge.unavailable";
  private static final String K_BADGE_CONNECTING = NS + ".badge.connecting";
  private static final String K_DETAILS_ITEMS = NS + ".details.items"; // "· {0} items"
  private static final String K_TITLE_ECLIPSE = NS + ".title.eclipse";
  private static final String K_TITLE_MEMORY = NS + ".title.memory";
  private static final String K_TITLE_UNAVAILABLE = NS + ".title.unavailable";
  private static final String K_TITLE_CONNECTING = NS + ".title.connecting";

  /** Daemon pool for the off-UI-thread store-info probes. */
  private static final ExecutorService PROBE = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "store-indicator-probe");
    t.setDaemon(true);
    return t;
  });

  private final AdminClient adminClient;
  private final Icon dbIcon = VaadinIcon.DATABASE.create();
  private final Span badge = new Span();
  private final Span details = new Span();
  private final AtomicBoolean inFlight = new AtomicBoolean(false);
  private StoreMode lastMode = StoreMode.UNAVAILABLE;
  /** Auth token captured on the UI thread; read by the probe thread's client. */
  private volatile String probeToken;

  public StoreIndicator(AdminClient adminClient) {
    this.adminClient = adminClient;
    // SessionAuth reads the Vaadin session (thread-local), which the probe
    // thread does not have — so the token is captured on the UI thread into
    // probeToken and the client reads it from there.
    this.adminClient.withBearerToken(() -> probeToken);

    setAlignItems(FlexComponent.Alignment.CENTER);
    setSpacing(true);
    setPadding(false);

    addClassName(CLASS_ROOT);
    dbIcon.addClassName(CLASS_ICON);
    badge.addClassName(CLASS_BADGE);
    details.addClassName(CLASS_DETAILS);

    add(dbIcon, badge, details);
    // Initial paint: a connection attempt is about to run (see onAttach).
    showConnecting();
  }

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    refreshOnce();
    UI ui = attachEvent.getUI();
    ui.setPollInterval(10000); // alle 10 Sekunden aktualisieren
    ui.addPollListener(_ -> refreshOnce());
  }

  /**
   * Probes {@code getStoreInfo()} off the UI thread and updates the indicator.
   * "Connecting" (amber) is shown only while not currently connected, so a
   * steady connection does not flash on every poll.
   */
  public void refreshOnce() {
    UI ui = getUI().orElse(null);
    if (ui == null) return;
    // One probe at a time; skip while the previous one is still running.
    if (!inFlight.compareAndSet(false, true)) return;

    // Capture the token now (UI thread); the probe thread has no Vaadin session.
    probeToken = SessionAuth.currentToken();

    if (!isConnected(lastMode)) {
      safeAccess(ui, this::showConnecting);
    }

    PROBE.submit(() -> {
      StoreInfo info = null;
      boolean ok = true;
      try {
        info = adminClient.getStoreInfo();
      } catch (Exception probeFailed) {
        ok = false;
        logger().debug("store-info probe failed: {}", probeFailed.getMessage());
      }
      final boolean success = ok;
      final StoreInfo result = info;
      try {
        safeAccess(ui, () -> {
          if (success) {
            applyConnected(result);
          } else {
            applyDisconnected();
          }
        });
      } finally {
        inFlight.set(false);
      }
    });
  }

  private static boolean isConnected(StoreMode mode) {
    return mode == StoreMode.ECLIPSE_STORE || mode == StoreMode.IN_MEMORY;
  }

  private void applyConnected(StoreInfo info) {
    boolean persistent = "EclipseStore".equalsIgnoreCase(info.mode());
    StoreMode newMode = persistent ? StoreMode.ECLIPSE_STORE : StoreMode.IN_MEMORY;

    applyTexts(newMode, info.mappings());
    setStateClass(persistent ? STATE_ECLIPSE : STATE_MEMORY);

    if (newMode != lastMode) {
      lastMode = newMode;
      StoreEvents.publish(new StoreConnectionChanged(newMode, info.mappings()));
    }
  }

  private void applyDisconnected() {
    applyTexts(StoreMode.UNAVAILABLE, 0);
    setStateClass(STATE_DOWN);

    if (lastMode != StoreMode.UNAVAILABLE) {
      lastMode = StoreMode.UNAVAILABLE;
      StoreEvents.publish(new StoreConnectionChanged(StoreMode.UNAVAILABLE, 0));
    }
  }

  private void showConnecting() {
    badge.setText(tr(K_BADGE_CONNECTING, "Connecting…"));
    details.setText("");
    getElement().setAttribute("title", tr(K_TITLE_CONNECTING, "Connecting to the store…"));
    setStateClass(STATE_CONNECTING);
  }

  private void applyTexts(StoreMode mode, long mappings) {
    Objects.requireNonNull(mode, "mode");

    switch (mode) {
      case ECLIPSE_STORE -> {
        badge.setText(tr(K_BADGE_ECLIPSE, "EclipseStore"));
        details.setText(tr(K_DETAILS_ITEMS, "· {0} items", mappings));
        getElement().setAttribute("title", tr(K_TITLE_ECLIPSE, "Persistent via EclipseStore"));
      }
      case IN_MEMORY -> {
        badge.setText(tr(K_BADGE_MEMORY, "InMemory"));
        details.setText(tr(K_DETAILS_ITEMS, "· {0} items", mappings));
        getElement().setAttribute("title", tr(K_TITLE_MEMORY, "Volatile (InMemory)"));
      }
      default -> {
        badge.setText(tr(K_BADGE_UNAVAILABLE, "Disconnected"));
        details.setText("");
        getElement().setAttribute("title", tr(K_TITLE_UNAVAILABLE, "No connection to the store"));
      }
    }
  }

  private void setStateClass(String stateClass) {
    removeClassNames(STATE_ECLIPSE, STATE_MEMORY, STATE_DOWN, STATE_CONNECTING);
    addClassName(stateClass);
  }

  private void safeAccess(UI ui, Command command) {
    try {
      ui.access(command);
    } catch (UIDetachedException gone) {
      // UI detached (navigated away / closed) — nothing to update.
    }
  }
}
