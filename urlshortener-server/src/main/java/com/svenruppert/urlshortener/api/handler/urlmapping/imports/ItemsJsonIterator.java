package com.svenruppert.urlshortener.api.handler.urlmapping.imports;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterates over JSON objects inside a top-level array field (default: "items")
 * of an export JSON object, e.g.
 * { "formatVersion":"1", ... , "items":[ {..},{..} ] }
 * <p>
 * No JSON libraries; robust for strings/escapes and nested values while seeking.
 */
public final class ItemsJsonIterator
    implements Iterable<String> {

  private final PushbackReader reader;
  private final String arrayFieldName;

  public ItemsJsonIterator(Reader reader) {
    this(reader, "items");
  }

  public ItemsJsonIterator(Reader reader, String arrayFieldName) {
    // Need pushback to not lose delimiters while skipping values
    this.reader = (reader instanceof PushbackReader pr) ? pr : new PushbackReader(reader, 4);
    this.arrayFieldName = arrayFieldName;
  }

  /**
   * Positions reader right AFTER '[' of the given array field.
   */
  private static void seekArrayStart(PushbackReader r, String field)
      throws IOException {
    int ch = skipWs(r);
    if (ch != '{') throw new IOException("Expected JSON object '{' but got: " + (char) ch);

    while (true) {
      ch = skipWs(r);

      if (ch == '}') throw new IOException(field + " array not found");

      if (ch != '"') throw new IOException("Expected JSON string key but got: " + (char) ch);
      String key = readJsonString(r); // opening quote already consumed

      ch = skipWs(r);
      if (ch != ':') throw new IOException("Expected ':' after key \"" + key + "\" but got: " + (char) ch);

      if (field.equals(key)) {
        ch = skipWs(r);
        if (ch != '[') throw new IOException("Expected '[' for array field \"" + field + "\" but got: " + (char) ch);
        return; // positioned after '['
      }

      // skip value of this key (any JSON value)
      skipJsonValue(r);

      // now expect ',' or '}' (or continue)
      ch = skipWs(r);
      if (ch == ',') continue;
      if (ch == '}') throw new IOException(field + " array not found");

      throw new IOException("Expected ',' or '}' but got: " + (char) ch);
    }
  }

  /**
   * Reads next {...} object from current array position.
   * Returns null when ']' is reached.
   */
  private static String readNextArrayObject(PushbackReader r)
      throws IOException {
    int ch;

    // skip whitespace and commas
    do {
      ch = r.read();
      if (ch == -1) return null;
    } while (Character.isWhitespace(ch) || ch == ',');

    if (ch == ']') return null;
    if (ch != '{') throw new IOException("Expected '{' but got: " + (char) ch);

    StringBuilder sb = new StringBuilder(512);
    sb.append('{');

    int depth = 1;
    boolean inString = false;
    boolean escape = false;

    while ((ch = r.read()) != -1) {
      char c = (char) ch;
      sb.append(c);

      if (inString) {
        if (escape) escape = false;
        else if (c == '\\') escape = true;
        else if (c == '"') inString = false;
        continue;
      }

      if (c == '"') {
        inString = true;
        continue;
      }

      if (c == '{') depth++;
      else if (c == '}') {
        depth--;
        if (depth == 0) return sb.toString();
      }
    }

    throw new IOException("Unexpected EOF while reading JSON object");
  }

  private static int skipWs(PushbackReader r)
      throws IOException {
    int ch;
    do {
      ch = r.read();
      if (ch == -1) throw new IOException("Unexpected EOF");
    } while (Character.isWhitespace(ch));
    return ch;
  }

  // ----------------- low-level JSON helpers -----------------

  /**
   * Reads a JSON string; opening quote has already been consumed.
   * Returns raw content (no unicode unescape needed for keys).
   */
  private static String readJsonString(PushbackReader r)
      throws IOException {
    StringBuilder sb = new StringBuilder(32);
    boolean esc = false;
    for (int ch; (ch = r.read()) != -1;) {
      char c = (char) ch;
      if (esc) {
        // For key matching, it's enough to keep the escaped char as-is.
        sb.append(c);
        esc = false;
        continue;
      }
      if (c == '\\') {
        esc = true;
        continue;
      }
      if (c == '"') {
        return sb.toString();
      }
      sb.append(c);
    }
    throw new IOException("Unexpected EOF in JSON string");
  }

  /**
   * Skips any JSON value (string, number, true/false/null, object, array),
   * leaving the reader positioned on the next token AFTER the value.
   */
  private static void skipJsonValue(PushbackReader r)
      throws IOException {
    int ch = skipWs(r);

    if (ch == '"') {
      readJsonString(r);
      return;
    }

    if (ch == '{') {
      skipBalanced(r, '{', '}');
      return;
    }

    if (ch == '[') {
      skipBalanced(r, '[', ']');
      return;
    }

    // primitive: number, true, false, null
    // consume until delimiter, then push delimiter back for caller
    while (ch != -1) {
      if (ch == ',' || ch == '}' || ch == ']') {
        r.unread(ch);
        return;
      }
      ch = r.read();
    }

    throw new IOException("Unexpected EOF while skipping value");
  }

  private static void skipBalanced(PushbackReader r, char open, char close)
      throws IOException {
    int depth = 1;
    boolean inString = false;
    boolean escape = false;

    for (int ch; (ch = r.read()) != -1;) {
      char c = (char) ch;

      if (inString) {
        if (escape) escape = false;
        else if (c == '\\') escape = true;
        else if (c == '"') inString = false;
        continue;
      }

      if (c == '"') {
        inString = true;
        continue;
      }

      if (c == open) depth++;
      else if (c == close) {
        depth--;
        if (depth == 0) return;
      }
    }

    throw new IOException("Unexpected EOF while skipping balanced structure");
  }

  @Override
  public Iterator<String> iterator() {
    return new Iterator<>() {

      private boolean initialised = false;
      private String next;

      @Override
      public boolean hasNext() {
        if (next != null) return true;
        try {
          if (!initialised) {
            seekArrayStart(reader, arrayFieldName);
            initialised = true;
          }
          next = readNextArrayObject(reader);
          return next != null;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public String next() {
        if (!hasNext()) throw new NoSuchElementException();
        String v = next;
        next = null;
        return v;
      }
    };
  }
}
