package io.prismio.annotator;

import java.nio.file.Path;
import java.util.List;
import junit.framework.TestCase;

/** Reading the compiler's JSON (what the language server's own checks print, and what the library probe reads). */
public class PrismioDiagnosticsTest extends TestCase {

  private static final Path FILE = Path.of("/work/src/parse/stmt.psm").toAbsolutePath().normalize();

  private static String record(String severity, String code, String file, int line, String message) {
    return "{\"kind\":\"diagnostic\",\"schemaVersion\":1,\"severity\":\"" + severity + "\",\"code\":"
        + (code == null ? "null" : "\"" + code + "\"") + ",\"file\":"
        + (file == null ? "null" : "\"" + file + "\"") + ",\"line\":" + line
        + ",\"column\":3,\"length\":2,\"message\":\"" + message + "\"}";
  }

  public void testANoteJoinsTheDiagnosticItExplains() {
    String output = String.join("\n",
        record("warning", "P4003", FILE.toString(), 5, "this range counts down"),
        record("note", null, null, 0, "write `start..<n`"),
        "{\"kind\":\"summary\",\"schemaVersion\":1,\"errors\":0,\"warnings\":1}");
    List<PrismioDiagnostic> parsed = PrismioDiagnosticParser.parseOutput(output);
    assertEquals(1, parsed.size());
    assertEquals("this range counts down\nnote: write `start..<n`", parsed.get(0).message());
    assertEquals(5, parsed.get(0).line());
  }

  public void testANoteWithNothingBeforeItIsDropped() {
    assertTrue(PrismioDiagnosticParser.parseOutput(record("note", null, null, 0, "orphan")).isEmpty());
  }

  public void testTheLaunchersTextWarningIsNotParsedAsADiagnostic() {
    String output = String.join("\n",
        "warning[P1077]: the project compiler was not built on this machine, so it will not be run",
        "  note: .prismio/build/debug/prismio",
        record("error", "P4001", FILE.toString(), 12, "unknown type `Parsr`"));
    List<PrismioDiagnostic> parsed = PrismioDiagnosticParser.parseOutput(output);
    assertEquals(1, parsed.size());
    assertEquals("P4001", parsed.get(0).code());
  }

}
