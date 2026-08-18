package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.client.ColumnVisibilityClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColumnVisibilityClientIT {

  protected static final String VIEW_ID = "overview";
  private ShortenerServer server;
  private PreferencesStore store;
  private ColumnVisibilityClient client;

  @BeforeEach
  public void startServer()
      throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init();
    TestAuth.loginAsAdmin();
  }

  @AfterEach
  public void stopServer() {
    server.shutdown();
  }

  @BeforeEach
  void setUp() {
    client = new ColumnVisibilityClient();
  }

  @Test
  void endToEnd_load_editSingle_editBulk_deleteAll()
      throws Exception {

    // initial load → empty
    var viewId = VIEW_ID;
    var userId = "u1";
    var m0 = client.load(userId, viewId);
    assertTrue(m0.isEmpty());

    // single edit
    client.editSingle(userId, viewId, "shortCode", true);
    var m1 = client.load(userId, viewId);
    assertEquals(Boolean.TRUE, m1.get("shortCode"));

    // bulk edit
    Map<String, Boolean> changes = new LinkedHashMap<>();
    changes.put("url", false);
    changes.put("created", true);
    client.editBulk(userId, viewId, changes);

    var m2 = client.load(userId, viewId);
    assertEquals(Boolean.TRUE, m2.get("shortCode"));
    assertEquals(Boolean.FALSE, m2.get("url"));
    assertEquals(Boolean.TRUE, m2.get("created"));

    // delete all for view
    client.deleteAllForView(userId, viewId);
    var m3 = client.load(userId, viewId);
    assertTrue(m3.isEmpty());
  }

  @Test
  void deleteSingle_isIdempotent()
      throws Exception {

    // set two keys
    var userId = "u2";
    client.editBulk(userId, VIEW_ID, Map.of("hits", true, "owner", false));
    var m1 = client.load(userId, VIEW_ID);
    assertEquals(Boolean.TRUE, m1.get("hits"));
    assertEquals(Boolean.FALSE, m1.get("owner"));

    // delete 'owner'
    client.deleteSingle(userId, VIEW_ID, "owner");
    var m2 = client.load(userId, VIEW_ID);
    assertEquals(Boolean.TRUE, m2.get("hits"));
    assertNull(m2.get("owner"));

    // delete again → should not fail
    client.deleteSingle(userId, VIEW_ID, "owner");
    var m3 = client.load(userId, VIEW_ID);
    assertEquals(Boolean.TRUE, m3.get("hits"));
    assertNull(m3.get("owner"));
  }

  @Test
  void bulkEdit_withEmptyChanges_throwsIAE() {
    assertThrows(IllegalArgumentException.class,
                 () -> client.editBulk("u3", VIEW_ID, Map.of()));
  }
}
