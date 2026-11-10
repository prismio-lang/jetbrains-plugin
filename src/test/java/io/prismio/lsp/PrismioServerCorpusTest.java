package io.prismio.lsp;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import junit.framework.TestCase;

/**
 * Every {@code .psm} in a real Prismio checkout, through the language server.
 *
 * <p>A checkout compiles, so any error the editor would show on one of its files is the editor's
 * mistake -- which is how "cannot read imported module `lexer.token`" on {@code src/parse/stmt.psm}
 * went unnoticed once: the file is fine, and it was checked as a program of its own. Negative
 * fixtures are wrong on purpose; {@code evidence} and {@code scratch} hold experiments against older
 * versions of the language; {@code sandbox} is work in progress; and {@code tests/freestanding}
 * programs are for another target than the one the server checks for, which the compiler says (an
 * x86 instruction on an arm64 host), not the editor.
 *
 * <p>The first assertion is that files were checked at all: a server that published nothing would
 * pass the second. Skipped unless {@code -Dprismio.checkout=<dir>} and
 * {@code -Dprismio.compiler=<prismio>} are given.
 */
public class PrismioServerCorpusTest extends TestCase {

  private static final List<String> SKIPPED_DIRS = List.of(
      "third_party", "build", ".prismio", "graphify-out", "node_modules", ".git", "evidence", "scratch", "sandbox",
      "freestanding",
      // `.claude/worktrees` holds whole copies of the checkout made by editor sessions: two thirds of the files
      // this test checked on one machine (1,172 of 1,774), each checked again as though it were new, and a file in a
      // copy costs a whole-program check (1.5 s) where the same file in the checkout costs 0.17 s. `dist` is a
      // packaged toolchain, not sources to hold the editor to.
      ".claude", "dist");

  /** What one session found over its share of the files. */
  private static final class Share {
    int answered;
    final List<String> problems = new ArrayList<>();
  }

  private static Share check(Path root, String compiler, List<Path> files) throws Exception {
    Share share = new Share();
    Path scratch = Files.createTempDirectory("prismio-corpus");
    try (LspProbe probe = new LspProbe(compiler, scratch)) {
      probe.initialize(root);
      for (Path source : files) {
        Path file = source.toRealPath();
        List<JsonObject> published = probe.diagnosticsFor(file, Files.readString(file), 120);
        if (published == null) {
          share.problems.add(root.relativize(file) + ": the server published nothing");
          continue;
        }
        share.answered++;
        for (JsonObject d : published) {
          if (d.has("severity") && d.get("severity").getAsInt() == 1) {
            share.problems.add(root.relativize(file) + ":" + (d.getAsJsonObject("range").getAsJsonObject("start")
                .get("line").getAsInt() + 1) + ": " + d.get("message").getAsString());
          }
        }
      }
    } finally {
      try {
        Files.deleteIfExists(scratch);
      } catch (IOException ignored) {
        // Left for the system to clear.
      }
    }
    return share;
  }

  public void testNoFileInACheckoutShowsAnError() throws Exception {
    String checkout = System.getProperty("prismio.checkout");
    String compiler = System.getProperty("prismio.compiler");
    if (checkout == null || compiler == null || !Files.isDirectory(Path.of(checkout))) {
      return;
    }
    Path root = Path.of(checkout).toRealPath();

    List<Path> sources;
    try (Stream<Path> walk = Files.walk(root)) {
      sources = walk.filter(p -> p.toString().endsWith(".psm"))
          .filter(p -> !p.getFileName().toString().startsWith("neg_"))
          .filter(p -> {
            for (Path part : root.relativize(p)) {
              if (SKIPPED_DIRS.contains(part.toString())) {
                return false;
              }
            }
            return true;
          })
          .sorted()
          .toList();
    }

    // Each file is a check of the program it belongs to, so the files are dealt out to several servers that run side by
    // side: one server runs one check at a time, and a check is one core. Four sessions took the test from minutes to a
    // fraction of one on a ten-core machine.
    int sessions = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    List<List<Path>> shares = new ArrayList<>();
    for (int i = 0; i < sessions; i++) {
      shares.add(new ArrayList<>());
    }
    for (int i = 0; i < sources.size(); i++) {
      shares.get(i % sessions).add(sources.get(i));
    }

    List<String> problems = new ArrayList<>();
    int answered = 0;
    ExecutorService pool = Executors.newFixedThreadPool(sessions);
    try {
      List<Future<Share>> running = new ArrayList<>();
      for (List<Path> share : shares) {
        running.add(pool.submit(() -> check(root, compiler, share)));
      }
      for (Future<Share> done : running) {
        Share result = done.get();
        answered += result.answered;
        problems.addAll(result.problems);
      }
    } finally {
      pool.shutdownNow();
    }
    problems.sort(null);
    assertTrue("files were checked: " + answered + " of " + sources.size(), answered > sources.size() / 2);
    assertTrue(sources.size() + " files; the editor shows errors on:\n" + String.join("\n", problems), problems.isEmpty());
  }
}
