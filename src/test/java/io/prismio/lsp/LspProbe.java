package io.prismio.lsp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A just-enough LSP client for tests: the wire format and nothing else, so what a test reads is what
 * the server wrote and not what a library made of it. (The platform client is exercised by the IDE.)
 */
final class LspProbe implements AutoCloseable {

  private final Process process;
  private final OutputStream out;
  private final BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();
  private int nextId = 1;

  LspProbe(String compiler, Path cwd) throws IOException {
    ProcessBuilder builder = new ProcessBuilder(compiler, "lsp").directory(cwd.toFile());
    builder.environment().put("PRISMIO_INTERNAL_HOSTED", "1");
    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
    process = builder.start();
    out = process.getOutputStream();
    InputStream in = process.getInputStream();
    Thread reader = new Thread(() -> read(in), "lsp-probe-reader");
    reader.setDaemon(true);
    reader.start();
  }

  private void read(InputStream in) {
    try {
      while (true) {
        int length = -1;
        while (true) {
          String header = line(in);
          if (header == null) {
            return;
          }
          if (header.isEmpty()) {
            break;
          }
          if (header.toLowerCase().startsWith("content-length:")) {
            length = Integer.parseInt(header.substring(15).trim());
          }
        }
        if (length < 0) {
          continue;
        }
        byte[] body = in.readNBytes(length);
        messages.add(JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject());
      }
    } catch (IOException ignored) {
      // The server went away; the test's wait will time out and say so.
    }
  }

  private static String line(InputStream in) throws IOException {
    StringBuilder text = new StringBuilder();
    int b;
    while ((b = in.read()) >= 0) {
      if (b == '\n') {
        return text.toString().replace("\r", "");
      }
      text.append((char) b);
    }
    return null;
  }

  void send(JsonObject message) throws IOException {
    message.addProperty("jsonrpc", "2.0");
    byte[] body = message.toString().getBytes(StandardCharsets.UTF_8);
    out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(body);
    out.flush();
  }

  void notify(String method, JsonObject params) throws IOException {
    JsonObject message = new JsonObject();
    message.addProperty("method", method);
    message.add("params", params);
    send(message);
  }

  JsonObject request(String method, JsonObject params, long seconds) throws IOException, InterruptedException {
    int id = nextId++;
    JsonObject message = new JsonObject();
    message.addProperty("id", id);
    message.addProperty("method", method);
    message.add("params", params);
    send(message);
    return waitFor(m -> m.has("id") && !m.has("method") && m.get("id").getAsInt() == id, seconds);
  }

  interface Matcher {
    boolean test(JsonObject message);
  }

  JsonObject waitFor(Matcher matcher, long seconds) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    while (true) {
      long left = deadline - System.nanoTime();
      if (left <= 0) {
        return null;
      }
      JsonObject message = messages.poll(left, TimeUnit.NANOSECONDS);
      if (message == null) {
        return null;
      }
      if (matcher.test(message)) {
        return message;
      }
    }
  }

  void initialize(Path root) throws IOException, InterruptedException {
    JsonObject params = new JsonObject();
    params.addProperty("processId", ProcessHandle.current().pid());
    params.addProperty("rootUri", root.toUri().toString());
    JsonObject general = new JsonObject();
    JsonArray encodings = new JsonArray();
    encodings.add("utf-16");
    general.add("positionEncodings", encodings);
    JsonObject capabilities = new JsonObject();
    capabilities.add("general", general);
    params.add("capabilities", capabilities);
    request("initialize", params, 30);
    notify("initialized", new JsonObject());
  }

  /** Opens {@code file} with {@code text} and returns the diagnostics the server publishes for it, or null. */
  List<JsonObject> diagnosticsFor(Path file, String text, long seconds) throws IOException, InterruptedException {
    String uri = file.toUri().toString();
    JsonObject item = new JsonObject();
    item.addProperty("uri", uri);
    item.addProperty("languageId", "prismio");
    item.addProperty("version", 1);
    item.addProperty("text", text);
    JsonObject params = new JsonObject();
    params.add("textDocument", item);
    notify("textDocument/didOpen", params);
    JsonObject published = waitFor(m -> "textDocument/publishDiagnostics".equals(stringOf(m, "method"))
        && uri.equals(m.getAsJsonObject("params").get("uri").getAsString()), seconds);
    JsonObject close = new JsonObject();
    JsonObject identifier = new JsonObject();
    identifier.addProperty("uri", uri);
    close.add("textDocument", identifier);
    notify("textDocument/didClose", close);
    if (published == null) {
      return null;
    }
    List<JsonObject> found = new ArrayList<>();
    for (JsonElement e : published.getAsJsonObject("params").getAsJsonArray("diagnostics")) {
      found.add(e.getAsJsonObject());
    }
    return found;
  }

  private static String stringOf(JsonObject object, String key) {
    return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
  }

  @Override
  public void close() {
    try {
      request("shutdown", new JsonObject(), 10);
      notify("exit", new JsonObject());
      process.waitFor(10, TimeUnit.SECONDS);
    } catch (IOException | InterruptedException ignored) {
      // Killed below.
    } finally {
      process.destroyForcibly();
    }
  }
}
