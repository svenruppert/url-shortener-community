package com.svenruppert.urlshortener.client;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Objects;

final class ConnectionInputStream
    extends FilterInputStream {
  private final HttpURLConnection con;
  private boolean closed;

  ConnectionInputStream(InputStream in, HttpURLConnection con) {
    super(Objects.requireNonNull(in, "in"));
    this.con = Objects.requireNonNull(con, "con");
  }

  @Override
  public void close()
      throws IOException {
    if (closed) return;
    closed = true;
    try {
      super.close();
    } finally {
      con.disconnect();
    }
  }
}
