package io.prismio.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * What the differential tests share: the checkout and compiler named on the command line, a server
 * session over them, and the files to ask about.
 *
 * <p>Each differential puts one question to the plugin's own Java and to {@code prismio lsp} over the
 * same files, and the server may say more, never less or different. They are skipped unless
 * {@code -Dprismio.checkout} and {@code -Dprismio.compiler} are given, and each first asserts that
 * the two sides answered at all: a comparison of two empty answers passes against a server that did
 * nothing.
 */
final class ServerCorpus implements AutoCloseable {

  final Path root;
  final LspProbe probe;
  private final Path scratch;

  private ServerCorpus(Path root, LspProbe probe, Path scratch) {
    this.root = root;
    this.probe = probe;
    this.scratch = scratch;
  }

  /** A session over the checkout, or null when the properties that name one are missing. */
  static ServerCorpus start() throws IOException, InterruptedException {
    String checkout = System.getProperty("prismio.checkout");
    String compiler = System.getProperty("prismio.compiler");
    if (checkout == null || compiler == null || !Files.isDirectory(Path.of(checkout))) {
      return null;
    }
    Path root = Path.of(checkout).toRealPath();
    Path scratch = Files.createTempDirectory("prismio-differential");
    LspProbe probe = new LspProbe(compiler, scratch);
    probe.initialize(root);
    return new ServerCorpus(root, probe, scratch);
  }

  /** The checkout's standard library sources: what the Java side reads as the toolchain's library. */
  Path stdlib() {
    return root.resolve("std");
  }

  /**
   * Programs that compile and import only the library, in a stable order: the positive tests, then
   * the compiler's own sources when {@code withSources} is set. {@code limit} bounds the count.
   */
  List<Path> files(int limit, boolean withSources) throws IOException {
    List<Path> found = new ArrayList<>();
    try (Stream<Path> tests = Files.list(root.resolve("tests"))) {
      tests.filter(p -> p.getFileName().toString().startsWith("test_") && p.toString().endsWith(".psm"))
          .sorted().forEach(found::add);
    }
    if (withSources) {
      try (Stream<Path> walk = Files.walk(root.resolve("lsp"))) {
        walk.filter(p -> p.toString().endsWith(".psm")).sorted().forEach(found::add);
      }
    }
    int stride = Math.max(1, found.size() / Math.max(1, limit));
    List<Path> chosen = new ArrayList<>();
    for (int i = 0; i < found.size() && chosen.size() < limit; i += stride) {
      chosen.add(found.get(i).toRealPath());
    }
    return chosen;
  }

  @Override
  public void close() {
    probe.close();
    try {
      Files.deleteIfExists(scratch);
    } catch (IOException ignored) {
      // Left for the system to clear.
    }
  }
}
