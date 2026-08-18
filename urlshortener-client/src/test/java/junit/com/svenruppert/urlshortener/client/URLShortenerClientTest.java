package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static com.svenruppert.urlshortener.core.JsonUtils.toJson;
import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.*;

public class URLShortenerClientTest
    implements HasLogger {

  protected static final String HTTP_SVENRUPPERT_COM = "http://svenruppert.com";
  private URLShortenerClient client;
  private ShortenerServer server;

  @BeforeEach
  public void startServer()
      throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init();
    TestAuth.loginAsAdmin();
    client = new URLShortenerClient();
  }

  @AfterEach
  public void stopServer() {
    server.shutdown();
  }

  @Test
  void listEndpoints_useGET_and_returnJson()
      throws IOException {
    var allRaw = client.listAllAsJson();
    assertNotNull(allRaw);
    assertTrue(allRaw.contains("\"mode\""));
    assertTrue(allRaw.contains("\"count\""));
    assertTrue(allRaw.contains("\"items\""));

    var activeRaw = client.listActiveAsJson();
    assertNotNull(activeRaw);
    assertTrue(activeRaw.contains("\"mode\""));
    assertTrue(activeRaw.contains("\"items\""));

    var expiredRaw = client.listExpiredAsJson();
    assertNotNull(expiredRaw);
    assertTrue(expiredRaw.contains("\"mode\""));
    assertTrue(expiredRaw.contains("\"items\""));
  }

  @Test
  void testCreateMappingAndResolve()
      throws IOException {
    var shortCode = client.createMapping(HTTP_SVENRUPPERT_COM);
    logger().info("shortCode: {}", shortCode);

    assertNotNull(shortCode);
    assertNotNull(shortCode.getShortCode());

    assertEquals(HTTP_SVENRUPPERT_COM, shortCode.originalUrl());
    assertTrue(shortCode.expiresAt().isEmpty());
    var createdAt = shortCode.createdAt();
    assertNotNull(createdAt);
    assertTrue(createdAt.isBefore(now()));

    var resolvedURL = client.resolveShortcode(shortCode.shortCode());
    logger().info("resolvedURL: {}", resolvedURL);
    assertEquals(HTTP_SVENRUPPERT_COM, resolvedURL);

    //TODO - impl. ShortCode with ExpiredAt
    //create second with expiredAt
    //    var shortCodeWithExpired = client.createMapping(HTTP_SVENRUPPERT_COM);
    //    assertEquals("1", shortCode.shortCode()); //TODO will break soo after int will be replaced with hash value

  }


  @Test
  void test002()
      throws IOException {
    var emptyList = client.listAll();
    assertTrue(emptyList.isEmpty());

    var mapping = client.createMapping(HTTP_SVENRUPPERT_COM);
    assertNotNull(mapping);

    var listOneEntry = client.listAll();
    logger().info("listOneEntry {}", listOneEntry);
    assertFalse(listOneEntry.isEmpty());
    assertEquals(1, listOneEntry.size());

  }

  @Test
  void test003()
      throws IOException {
    assertThrows(IllegalArgumentException.class, () -> {
      var mapping = client.createMapping("http://svende");
    });
    var listOneEntry = client.listAll();
    assertTrue(listOneEntry.isEmpty());

  }

  // ————————————————————————————————————————————————————————————————
  // NEU: listActive – leer -> 1 entry after Create
  // ————————————————————————————————————————————————————————————————
  @Test
  void test003_findActive_initiallyEmpty_thenOne()
      throws IOException {
    var active0 = client.listActive();
    assertNotNull(active0);
    assertTrue(active0.isEmpty(), "active list should be empty initially");

    client.createMapping(HTTP_SVENRUPPERT_COM);

    var active1 = client.listActive();
    assertNotNull(active1);
    assertEquals(1, active1.size(), "active list should contain exactly one entry after first create");
    assertEquals(HTTP_SVENRUPPERT_COM, active1.getFirst().originalUrl());
  }

  // ————————————————————————————————————————————————————————————————
  // listExpired – currently always empty (no expiries in store)
  // ————————————————————————————————————————————————————————————————
  @Test
  void test004_findExpired_isEmpty_withoutExpirySupport()
      throws IOException {
    // Vorbedingung: keine Expiry-Unterstützung im Store -> erwartete leere Liste
    var expired0 = client.listExpired();
    assertNotNull(expired0);
    assertTrue(expired0.isEmpty(), "expired list should be empty initially");

    client.createMapping(HTTP_SVENRUPPERT_COM);

    var expired1 = client.listExpired();
    assertNotNull(expired1);
    assertTrue(expired1.isEmpty(), "expired list should still be empty (no expiresAt set)");
  }

  // ————————————————————————————————————————————————————————————————
  // Consistency check findAll vs. findActive (without expiries)
  // ————————————————————————————————————————————————————————————————
  @Test
  void test005_findAll_equals_findActive_withoutExpiries()
      throws IOException {
    client.createMapping(HTTP_SVENRUPPERT_COM);

    var all = client.listAll();
    var active = client.listActive();

    assertNotNull(all);
    assertNotNull(active);
    assertEquals(all.size(), active.size(),
                 "Without expiry support, findAll and findActive should have equal size");
    assertFalse(all.isEmpty(), "There should be at least one mapping");
  }


  @Test
  void delete_existingMapping_returnsTrue_andRemovesIt()
      throws IOException {
    // Arrange: Create a mapping and verify its existence
    var created = client.createMapping(HTTP_SVENRUPPERT_COM);
    assertNotNull(created);
    var before = client.listAll();
    assertEquals(1, before.size());

    // Act: delete
    boolean removed = client.delete(created.shortCode());

    // Assert: true returns, list empty, second deletion attempt -> false
    assertTrue(removed, "delete(...) must return true for HTTP 204");
    var after = client.listAll();
    assertEquals(0, after.size(), "After deletion, the list must be empty");

    boolean removedAgain = client.delete(created.shortCode());
    assertFalse(removedAgain, "Second deletion attempt on the same resource must return false (404 → false)");
  }

  @Test
  void delete_nonExistingMapping_returnsFalse()
      throws IOException {
    boolean removed = client.delete("NoSuch123");
    assertFalse(removed, "Non-existent shortcode must return false (HTTP 404)");
  }

  @Test
  void deleteOrThrow_nonExistingMapping_throwsIOException() {
    assertThrows(IOException.class, () -> client.deleteOrThrow("NoSuch999"),
                 "deleteOrThrow(...) must throw an IOException on 404");
  }

  @Test
  void delete_isIdempotent_and_doesNotAffectOtherEntries()
      throws IOException {
    // Arrange: Zwei Mappings anlegen
    var m1 = client.createMapping(HTTP_SVENRUPPERT_COM);
    var m2 = client.createMapping("https://example.org/path");
    assertNotNull(m1);
    assertNotNull(m2);

    var allBefore = client.listAll();
    assertEquals(2, allBefore.size(), "Before deleting, 2 entries must exist");

    // Act: m1 löschen (zweimal, um Idempotenz zu prüfen)
    assertTrue(client.delete(m1.shortCode()), "First delete operation must return true");
    assertFalse(client.delete(m1.shortCode()), "Second deletion must return false (already deleted)");

    // Assert: m2 weiterhin vorhanden, m1 entfernt
    var allAfter = client.listAll();
    assertEquals(1, allAfter.size(), "After deletion, exactly 1 entry must remain");
    assertEquals(m2.shortCode(), allAfter.getFirst().shortCode(),
                 "The remaining entry must be m2");
  }

  @Test
  void delete_then_listActive_reflectsRemoval()
      throws IOException {
    // Arrange
    var m = client.createMapping(HTTP_SVENRUPPERT_COM);
    assertNotNull(m);
    assertEquals(1, client.listActive().size(), "An active entry is expected");

    // Act
    assertTrue(client.delete(m.shortCode()));

    // Assert
    assertTrue(client.listActive().isEmpty(), "No active entry may remain after deletion");
  }

  @Test
  void checkTheActiveAttribute()
      throws IOException {
    // Arrange
    var m = client.createCustomMapping("sru01", HTTP_SVENRUPPERT_COM, null, false);
    logger().info("Mapping As Json {}", toJson(m));
    assertNotNull(m);

    assertEquals(1, client.listAll().size(), "One entry is expected");
    assertEquals(0, client.listActive().size(), "NO active entry is expected");

    var m2 = client.createCustomMapping("sru02", HTTP_SVENRUPPERT_COM, null, true);
    logger().info("Mapping m2 As Json {}", toJson(m2));
    assertNotNull(m2);
    assertEquals(2, client.listAll().size(), "Two entries are expected");
    assertEquals(1, client.listActive().size(), "One active entry is expected");
    assertEquals(1, client.listInActive().size(), "One INactive entry is expected");

    // Act
    assertTrue(client.delete(m.shortCode()));
    assertEquals(1, client.listAll().size(), "One entry is expected");
    assertEquals(1, client.listActive().size(), "One active entry is expected");
  }

  @Test
  void resolve_nonExisting_returnsNull() throws IOException {
    assertNull(client.resolveShortcode("no-such-code"));
  }

  @Test
  void createCustomMapping_duplicateShortCode_throws() throws IOException {
    client.createCustomMapping("dup", HTTP_SVENRUPPERT_COM, null, true);

    assertThrows(IllegalArgumentException.class, () ->
        client.createCustomMapping("dup", "https://example.org", null, true)
    );
  }


  @Test
  void createCustomMapping_invalidUrl_throws_andDoesNotPersist() throws IOException {
    assertThrows(IllegalArgumentException.class, () ->
        client.createCustomMapping("bad01", "http://svende", null, true)
    );

    assertTrue(client.listAll().isEmpty(), "Invalid create must not persist anything");
  }

  @Test
  void toggleActive_switches_between_active_and_inactive() throws IOException {
    var m = client.createCustomMapping("tog01", HTTP_SVENRUPPERT_COM, null, true);
    assertNotNull(m);

    assertEquals(1, client.listActive().size());
    assertEquals(0, client.listInActive().size());

    assertTrue(client.toggleActive("tog01", false));
    assertEquals(0, client.listActive().size());
    assertEquals(1, client.listInActive().size());

    assertTrue(client.toggleActive("tog01", true));
    assertEquals(1, client.listActive().size());
    assertEquals(0, client.listInActive().size());
  }

  @Test
  void toggleActive_nonExisting_returnsFalse() throws IOException {
    Assertions.assertThrows(IOException.class, ()->{
      var condition = client.toggleActive("no-such", true);
    });
  }

  @Test
  void edit_existing_changesTargetUrl() throws IOException {
    var m = client.createCustomMapping("ed01", HTTP_SVENRUPPERT_COM, null, true);
    assertNotNull(m);

    assertTrue(client.edit("ed01", "https://example.org/new", null, true));
    assertEquals("https://example.org/new", client.resolveShortcode("ed01"));
  }

  @Test
  void edit_nonExisting_returnsFalse() throws IOException {
    assertFalse(client.edit("no-such", "https://example.org", null, true));
  }

  @Test @Disabled
  void listCount_initiallyZero_thenIncreases() throws IOException {

    var builder = UrlMappingListRequest.builder();
    var req = builder.build();
    assertEquals(0, client.listCount(req));

    client.createMapping(HTTP_SVENRUPPERT_COM);
    assertEquals(1, client.listCount(req));

    client.createMapping("https://example.org");
    assertEquals(2, client.listCount(req));
  }

  @Test
  void listAllAsJson_containsModeCountAndItemsArray() throws IOException {
    var raw = client.listAllAsJson();
    assertNotNull(raw);
    assertTrue(raw.trim().startsWith("{"));
    assertTrue(raw.contains("\"mode\""));
    assertTrue(raw.contains("\"count\""));
    assertTrue(raw.contains("\"items\":["));
  }

  @Test
  void delete_then_resolve_returnsNull() throws IOException {
    var created = client.createMapping(HTTP_SVENRUPPERT_COM);
    assertNotNull(created);

    assertEquals(HTTP_SVENRUPPERT_COM, client.resolveShortcode(created.shortCode()));

    assertTrue(client.delete(created.shortCode()));
    assertNull(client.resolveShortcode(created.shortCode()));
  }


  @Test
  void put_returnsFalse_on404_via_edit() {
    Assertions.assertThrows(IOException.class, ()-> {
      client.edit("does-not-exist", "https://example.org", null, true);
    });
  }

  @Test
  void put_returnsTrue_on2xx_via_toggleActive() throws IOException {
    var code = "tog-" + System.nanoTime();
    client.createCustomMapping(code, HTTP_SVENRUPPERT_COM, null, true);

    assertTrue(client.toggleActive(code, false));
    assertTrue(client.toggleActive(code, true));
  }

  @Test
  void put_returnsTrue_on2xx_via_edit() throws IOException {
    var code = "ed-" + System.nanoTime();
    client.createCustomMapping(code, HTTP_SVENRUPPERT_COM, null, true);

    assertTrue(client.edit(code, "https://example.org/new", null, true));
    assertEquals("https://example.org/new", client.resolveShortcode(code));
  }

  @Test
  void put_throwsIOException_on400_via_edit_invalidUrl() throws IOException {
    var code = "bad-" + System.nanoTime();
    client.createCustomMapping(code, HTTP_SVENRUPPERT_COM, null, true);

    IOException ex = assertThrows(IOException.class, () ->
        client.edit(code, "http://svende", null, true)
    );

    assertTrue(ex.getMessage().contains("Unexpected HTTP"), "must include status marker");
    assertTrue(ex.getMessage().contains("PUT"), "must include method");
  }


}