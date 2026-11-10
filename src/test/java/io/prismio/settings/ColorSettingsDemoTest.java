package io.prismio.settings;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import io.prismio.CompilerCheck;
import io.prismio.ums.UmsColorSettingsPage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import junit.framework.TestCase;

/**
 * The demo text on the two colour pages is source code a user reads and copies, and the first line of the Prismio one
 * says the compiler accepts it. It was not true once (a {@code match} without parentheses, a pre-0.2 collection API),
 * and nothing noticed. Here the markers come off and the compiler reads the rest. Compiler tests run with
 * {@code -Dprismio.compiler}.
 */
public class ColorSettingsDemoTest extends TestCase {
  /** A highlighting marker: lower-case first letter, which a type argument such as {@code <T>} or {@code <Int>} never has. */
  private static final Pattern MARKER = Pattern.compile("</?([a-z][A-Za-z]*)>");

  private static String strip(String demo) {
    return MARKER.matcher(demo).replaceAll("");
  }

  private static void everyMarkerIsOnThePage(String demo, Map<String, TextAttributesKey> tags) {
    Set<String> unknown = new TreeSet<>();
    Matcher matcher = MARKER.matcher(demo);
    while (matcher.find()) {
      if (!tags.containsKey(matcher.group(1))) {
        unknown.add(matcher.group(1));
      }
    }
    assertTrue("markers the page does not map to a colour, which show up as literal text: " + unknown, unknown.isEmpty());
  }

  public void testThePrismioDemoUsesOnlyMarkersThePageKnows() {
    PrismioColorSettingsPage page = new PrismioColorSettingsPage();
    everyMarkerIsOnThePage(page.getDemoText(), page.getAdditionalHighlightingTagToDescriptorMap());
  }

  public void testTheUmsDemoUsesOnlyMarkersThePageKnows() {
    UmsColorSettingsPage page = new UmsColorSettingsPage();
    everyMarkerIsOnThePage(page.getDemoText(), page.getAdditionalHighlightingTagToDescriptorMap());
  }

  public void testThePrismioDemoIsCodeTheCompilerAccepts() throws Exception {
    if (CompilerCheck.compiler() == null) {
      return;
    }
    String result = CompilerCheck.check(strip(new PrismioColorSettingsPage().getDemoText()));
    assertEquals("the demo text, markers removed, is not valid Prismio:\n" + result, "", result);
  }

  /**
   * The manifest names a host toolchain and native sources that a user's project has; the test supplies stand-ins for
   * every file it names, so what is left to complain about is the manifest.
   */
  public void testTheUmsDemoIsAManifestTheCompilerAccepts() throws Exception {
    String compiler = CompilerCheck.compiler();
    if (compiler == null) {
      return;
    }
    Path root = Files.createTempDirectory("prismio-ums-demo");
    try {
      Path project = Files.createDirectories(root.resolve("project"));
      Files.createDirectories(root.resolve("json"));
      Files.createDirectories(project.resolve("src"));
      Files.createDirectories(project.resolve("c/include"));
      Files.createDirectories(project.resolve("tools"));
      Files.writeString(project.resolve("src/main.psm"), "fn main() -> Int { return 0 }\n");
      Files.writeString(project.resolve("c/codec.c"), "int codec(void) { return 0; }\n");
      Files.writeString(project.resolve("tools/package.py"), "print('packaged')\n");
      Files.writeString(project.resolve("build.ums"), strip(new UmsColorSettingsPage().getDemoText()));

      ProcessBuilder builder = new ProcessBuilder(compiler, "build");
      builder.directory(project.toFile());
      builder.environment().put("PRISMIO_INTERNAL_HOSTED", "1");
      builder.redirectErrorStream(true);
      Process process = builder.start();
      String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertTrue("prismio build did not finish: " + out, process.waitFor(120, TimeUnit.SECONDS));
      assertEquals("the UMS demo text, markers removed, does not build:\n" + out, 0, process.exitValue());
      assertFalse(out, out.contains("error"));
    } finally {
      delete(root);
    }
  }

  private static void delete(Path dir) throws IOException {
    try (var files = Files.walk(dir)) {
      files.map(Path::toFile).sorted((a, b) -> b.compareTo(a)).forEach(File::delete);
    }
  }
}
