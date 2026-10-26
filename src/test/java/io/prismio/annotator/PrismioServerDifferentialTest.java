package io.prismio.annotator;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import junit.framework.TestCase;

/**
 * The language server's diagnostics against the external annotator's, file by file.
 *
 * <p>The annotator ran {@code prismio check} through {@link PrismioCheckTarget}'s idea of the program;
 * the server does it with the manifest and with every unsaved buffer. Until the annotator is deleted
 * they must agree on what is wrong with a file: the same codes, on the same lines, with the same
 * messages. The corpus is every negative fixture of the checkout (which are wrong on purpose, so
 * there is something to disagree about) and a sample of the programs that compile; a test that
 * compared two empty lists would pass against a server that did nothing, so it first asserts that
 * the negative fixtures produced diagnostics. The server may say more than the annotator did, and
 * only type errors (P4xxx): it checks with {@code --partial}, which type checks past a syntax error.
 *
 * <p>Skipped unless {@code -Dprismio.checkout} and {@code -Dprismio.compiler} are given.
 */
public class PrismioServerDifferentialTest extends TestCase {

  public void testServerAndAnnotatorAgree() throws Exception {
    String checkout = System.getProperty("prismio.checkout");
    String compilerProperty = System.getProperty("prismio.compiler");
    if (checkout == null || compilerProperty == null || !Files.isDirectory(Path.of(checkout))) {
      return;
    }
    Path root = Path.of(checkout).toRealPath();
    Path compiler = Path.of(compilerProperty);
    int limit = Integer.getInteger("prismio.differentialLimit", 400);

    List<Path> corpus = new ArrayList<>();
    try (Stream<Path> tests = Files.list(root.resolve("tests"))) {
      tests.filter(p -> p.getFileName().toString().startsWith("neg_") && p.toString().endsWith(".psm"))
          .sorted().forEach(corpus::add);
    }
    int negatives = corpus.size();
    try (Stream<Path> tests = Files.list(root.resolve("tests"))) {
      tests.filter(p -> p.getFileName().toString().startsWith("test_") && p.toString().endsWith(".psm"))
          .sorted().limit(40).forEach(corpus::add);
    }
    if (corpus.size() > limit) {
      corpus = corpus.subList(0, limit);
    }

    List<String> disagreements = new ArrayList<>();
    int withErrors = 0;
    Path scratch = Files.createTempDirectory("prismio-differential");
    try (LspProbeHolder holder = new LspProbeHolder(compiler.toString(), scratch, root)) {
      for (Path source : corpus) {
        Path file = source.toRealPath();
        String text = Files.readString(file);
        TreeSet<String> old = oldAnswer(compiler, file, text, root);
        List<JsonObject> published = holder.probe.diagnosticsFor(file, text, 60);
        if (published == null) {
          disagreements.add(root.relativize(file) + ": the server published nothing");
          continue;
        }
        TreeSet<String> current = new TreeSet<>();
        for (JsonObject d : published) {
          current.add(describe(d.getAsJsonObject("range").getAsJsonObject("start").get("line").getAsInt() + 1,
              d.has("code") ? d.get("code").getAsString() : "", d.get("message").getAsString()));
        }
        if (!old.isEmpty()) {
          withErrors++;
        }
        // The server checks with `--partial`: type checking goes on past a syntax error, so it may say
        // more -- type errors the annotator never saw -- and never less, and never a different thing.
        TreeSet<String> missing = new TreeSet<>(old);
        missing.removeAll(current);
        TreeSet<String> extra = new TreeSet<>(current);
        extra.removeAll(old);
        extra.removeIf(d -> d.split(" ", 3)[1].startsWith("P4"));
        if (!missing.isEmpty() || !extra.isEmpty()) {
          disagreements.add(root.relativize(file) + "\n    only the annotator: " + missing
              + "\n    only the server (beyond type errors): " + extra);
        }
      }
    } finally {
      Files.deleteIfExists(scratch);
    }
    assertTrue("the negative fixtures produced diagnostics (" + withErrors + " of " + negatives + "), so the comparison means something",
        withErrors > negatives / 2);
    assertTrue(corpus.size() + " files; the server and the annotator disagree on:\n" + String.join("\n", disagreements),
        disagreements.isEmpty());
  }

  /** What the annotator shows for the file: line, code and first line of the message. */
  private static TreeSet<String> oldAnswer(Path compiler, Path file, String text, Path root) throws IOException {
    TreeSet<String> shown = new TreeSet<>();
    PrismioExternalAnnotator.Checked checked = PrismioExternalAnnotator.checkThroughProgram(compiler, file, text, file, root);
    if (checked == null) {
      return shown;
    }
    for (PrismioDiagnostic d : PrismioExternalAnnotator.forFile(checked, file)) {
      if (d.file() == null || "P1075".equals(d.code())) {
        continue;
      }
      shown.add(describe(d.line(), d.code() == null ? "" : d.code(), d.message()));
    }
    return shown;
  }

  private static String describe(int line, String code, String message) {
    return line + " " + code + " " + message.split("\n", 2)[0];
  }

  /** Owns the probe so try-with-resources closes it; the server's working directory is a scratch one. */
  private static final class LspProbeHolder implements AutoCloseable {
    final io.prismio.lsp.LspProbeAccess probe;

    LspProbeHolder(String compiler, Path cwd, Path root) throws Exception {
      probe = io.prismio.lsp.LspProbeAccess.start(compiler, cwd, root);
    }

    @Override
    public void close() {
      probe.close();
    }
  }
}
