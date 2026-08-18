package junit.com.svenruppert.urlshortener.api.utils;

import com.sun.net.httpserver.HttpServer;
import com.svenruppert.urlshortener.api.utils.JsonWriter;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonWriterTest {

  @Test
  void writeJson_shouldNotDoubleEncodeJsonObjectString()
      throws Exception {
    String body = callEndpoint("{\"count\":0}");
    assertEquals("{\"count\":0}", body);
  }

  @Test
  void writeJson_shouldSerializePlainStringAsJsonString()
      throws Exception {
    String body = callEndpoint("hello");
    assertEquals("\"hello\"", body);
  }

  @Test
  void writeJson_shouldSerializeMapAsJsonObject()
      throws Exception {
    String body = callEndpoint(Map.of("count", 0));
    assertEquals("{\"count\":0}", body);
  }

  private String callEndpoint(Object responseBody)
      throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/test", exchange -> {
      JsonWriter.writeJson(exchange, 200, responseBody);
    });
    server.start();
    try {
      int port = server.getAddress().getPort();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:" + port + "/test"))
          .GET()
          .build();
      HttpResponse<String> response = HttpClient.newHttpClient()
          .send(request, HttpResponse.BodyHandlers.ofString());
      return response.body();
    } finally {
      server.stop(0);
    }
  }
}
