package io.prismio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Runs {@code prismio check} on a piece of source, for the tests that hold text the plugin ships (live templates, the
 * colour page's demo) to the compiler. The compiler comes from {@code -Dprismio.compiler}; without it {@link
 * #compiler()} is null and the tests fall back to the checks that need no compiler.
 */
final class CompilerCheck {
  private CompilerCheck() {}

  /** The compiler the run was pointed at, or null when {@code -Dprismio.compiler} is not set. */
  static String compiler() {
    return System.getProperty("prismio.compiler");
  }

  /** The output of {@code prismio check} on {@code source}, or an empty string when it is accepted. */
  static String check(String source) throws Exception {
    Path dir = Files.createTempDirectory("prismio-check");
    try {
      Path file = dir.resolve("snippet.psm");
      Files.writeString(file, source, StandardCharsets.UTF_8);
      ProcessBuilder builder = new ProcessBuilder(compiler(), "check", file.getFileName().toString());
      builder.directory(dir.toFile());
      // A compiler named `prismio` that runs inside a project hands the command to the project's own compiler.
      builder.environment().put("PRISMIO_INTERNAL_HOSTED", "1");
      builder.redirectErrorStream(true);
      Process process = builder.start();
      String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new AssertionError("prismio check did not finish: " + out);
      }
      return process.exitValue() == 0 ? "" : "exit " + process.exitValue() + "\n" + out;
    } finally {
      try (var files = Files.walk(dir)) {
        files.map(Path::toFile).sorted((a, b) -> b.compareTo(a)).forEach(File::delete);
      }
    }
  }
}
