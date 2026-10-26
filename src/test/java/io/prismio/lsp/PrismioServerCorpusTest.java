package io.prismio.lsp;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
      "freestanding");

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

    List<String> problems = new ArrayList<>();
    int answered = 0;
    Path scratch = Files.createTempDirectory("prismio-corpus");
    try (LspProbe probe = new LspProbe(compiler, scratch)) {
      probe.initialize(root);
      for (Path source : sources) {
        Path file = source.toRealPath();
        List<JsonObject> published = probe.diagnosticsFor(file, Files.readString(file), 120);
        if (published == null) {
          problems.add(root.relativize(file) + ": the server published nothing");
          continue;
        }
        answered++;
        for (JsonObject d : published) {
          if (d.has("severity") && d.get("severity").getAsInt() == 1) {
            problems.add(root.relativize(file) + ":" + (d.getAsJsonObject("range").getAsJsonObject("start")
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
    assertTrue("files were checked: " + answered + " of " + sources.size(), answered > sources.size() / 2);
    assertTrue(sources.size() + " files; the editor shows errors on:\n" + String.join("\n", problems), problems.isEmpty());
  }
}
