package com.svenruppert.urlshortener.api.handler.urlmapping.imports;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipImportReader {
  private ZipImportReader() {
  }

  public static InputStream extractExportJson(byte[] zipBytes,
                                              String expectedEntryName,
                                              long maxUncompressedBytes)
      throws IOException {

    ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
    ZipEntry entry;

    while ((entry = zis.getNextEntry()) != null) {
      String name = entry.getName();

      // Zip Slip Guard
      if (name.contains("..") || name.contains("\\") || name.startsWith("/")) {
        throw new IOException("Invalid zip entry name: " + name);
      }

      if (expectedEntryName.equals(name)) {
        return new BoundedInputStream(zis, maxUncompressedBytes);
      }
    }

    throw new IOException("Expected entry not found: " + expectedEntryName);
  }

  /**
   * Prevents zip bombs by limiting uncompressed bytes.
   */
  private static final class BoundedInputStream
      extends InputStream {
    private final InputStream delegate;
    private final long maxBytes;
    private long count = 0;

    private BoundedInputStream(InputStream delegate, long maxBytes) {
      this.delegate = delegate;
      this.maxBytes = maxBytes;
    }

    @Override
    public int read()
        throws IOException {
      int r = delegate.read();
      if (r != -1) {
        count++;
        if (count > maxBytes) {
          throw new IOException("Uncompressed content exceeds limit=" + maxBytes);
        }
      }
      return r;
    }

    @Override
    public int read(byte[] b, int off, int len)
        throws IOException {
      int r = delegate.read(b, off, len);
      if (r > 0) {
        count += r;
        if (count > maxBytes) {
          throw new IOException("Uncompressed content exceeds limit=" + maxBytes);
        }
      }
      return r;
    }
  }
}
