package junit.com.svenruppert.urlshortener.core;

import com.svenruppert.urlshortener.core.SecurityProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityProfileTest {

  @AfterEach
  void clear() {
    System.clearProperty(SecurityProfile.PROPERTY);
  }

  @Test
  void defaultsToDevelopment_whenUnset() {
    System.clearProperty(SecurityProfile.PROPERTY);
    assertEquals(SecurityProfile.DEVELOPMENT, SecurityProfile.current());
  }

  @Test
  void readsProductionFromProperty_caseInsensitive() {
    System.setProperty(SecurityProfile.PROPERTY, "production");
    assertEquals(SecurityProfile.PRODUCTION, SecurityProfile.current());
    System.setProperty(SecurityProfile.PROPERTY, "STRICT");
    assertEquals(SecurityProfile.STRICT, SecurityProfile.current());
  }

  @Test
  void unknownValue_fallsBackToDevelopment() {
    System.setProperty(SecurityProfile.PROPERTY, "bogus");
    assertEquals(SecurityProfile.DEVELOPMENT, SecurityProfile.current());
  }
}
