package junit.com.svenruppert.urlshortener.api.extension;

import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.extension.HandlerContribution;
import com.svenruppert.urlshortener.api.extension.ServerContext;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

/**
 * Test plug-in registered via META-INF/services (test scope) — proves the
 * open-core server seam end-to-end without any mock: the contributed handler
 * runs behind the standard security pipeline and reads from the real store
 * handed in via {@link ServerContext}.
 */
public class PingContribution
    implements HandlerContribution {

  public static final String PATH = "/api/extension-ping";

  @Override
  public String path() {
    return PATH;
  }

  @Override
  public HttpHandler create(ServerContext context) {
    return new PingHandler(context);
  }

  @RequiresPermission("listLinks")
  public static final class PingHandler
      implements HttpHandler {

    private final ServerContext context;

    PingHandler(ServerContext context) {
      this.context = context;
    }

    @Override
    public void handle(HttpExchange exchange)
        throws IOException {
      SuccessResponses.okJson(exchange,
          "{\"feature\":\"extension-ping\",\"links\":" + context.urlMappingStore().countAll() + "}");
    }
  }
}
