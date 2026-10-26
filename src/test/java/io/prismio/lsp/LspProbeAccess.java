package io.prismio.lsp;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;

/** The probe, for tests in other packages. */
public final class LspProbeAccess implements AutoCloseable {
  private final LspProbe probe;

  private LspProbeAccess(LspProbe probe) {
    this.probe = probe;
  }

  public static LspProbeAccess start(String compiler, Path cwd, Path root) throws Exception {
    LspProbe probe = new LspProbe(compiler, cwd);
    probe.initialize(root);
    return new LspProbeAccess(probe);
  }

  public List<JsonObject> diagnosticsFor(Path file, String text, long seconds) throws Exception {
    return probe.diagnosticsFor(file, text, seconds);
  }

  @Override
  public void close() {
    probe.close();
  }
}
