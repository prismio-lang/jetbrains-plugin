package io.prismio.lsp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.PrismioFileType;
import io.prismio.symbols.ProjectSymbols;
import io.prismio.symbols.TypeInference;
import io.prismio.symbols.TypeRef;
import io.prismio.toolchain.PrismioProjectSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Inlay hints: the type {@code let} has without writing one, from the plugin's inference and from the server.
 *
 * <p>Where the plugin shows {@code : T} after a binding the server must show the same, and the server may show
 * more: the parameter names at call sites, and the types the plugin's inference cannot establish.
 */
public class PrismioHintsDifferentialTest extends BasePlatformTestCase {

  public void testServerShowsTheTypeHintsThePluginShows() throws Exception {
    try (ServerCorpus corpus = ServerCorpus.start()) {
      if (corpus == null) {
        return;
      }
      PrismioProjectSettings.getInstance(getProject()).getState().stdlibPath = corpus.stdlib().toString();
      int plugin = 0;
      int server = 0;
      int agreed = 0;
      List<String> disagreements = new ArrayList<>();
      for (Path file : corpus.files(Integer.getInteger("prismio.differentialLimit", 80), false)) {
        String text = Files.readString(file);
        PsiFile psi = myFixture.configureByText(PrismioFileType.INSTANCE, text);
        if (!corpus.probe.open(file, text, 90)) {
          disagreements.add(corpus.root.relativize(file) + ": the server never analysed it");
          continue;
        }
        Document document = psi.getViewProvider().getDocument();
        Map<String, String> mine = new TreeMap<>();
        Map<Integer, TypeRef> types = TypeInference.letTypes(text, ProjectSymbols.scope(psi), ProjectSymbols.summary(psi).imports());
        for (Map.Entry<Integer, TypeRef> e : types.entrySet()) {
          int line = document.getLineNumber(e.getKey());
          mine.put((line + 1) + ":" + (e.getKey() - document.getLineStartOffset(line) + 1), ": " + e.getValue());
        }
        Map<String, String> theirs = new TreeMap<>();
        JsonObject range = new JsonObject();
        range.add("start", position(0, 0));
        range.add("end", position(document.getLineCount() + 1, 0));
        JsonObject params = new JsonObject();
        params.add("range", range);
        JsonElement hints = corpus.probe.ofDocument("textDocument/inlayHint", file, params);
        if (hints != null && hints.isJsonArray()) {
          for (JsonElement e : (JsonArray) hints) {
            JsonObject hint = e.getAsJsonObject();
            if (hint.has("kind") && hint.get("kind").getAsInt() == 1) {
              JsonObject at = hint.getAsJsonObject("position");
              theirs.put((at.get("line").getAsInt() + 1) + ":" + (at.get("character").getAsInt() + 1),
                  hint.get("label").getAsString());
            }
          }
        }
        String relative = corpus.root.relativize(file).toString();
        plugin += mine.size();
        server += theirs.size();
        for (Map.Entry<String, String> e : mine.entrySet()) {
          String other = theirs.get(e.getKey());
          if (other == null) {
            disagreements.add(relative + ":" + e.getKey() + ": the server shows no hint; the plugin shows `" + e.getValue() + "`");
          } else if (!spelling(other).equals(spelling(e.getValue())) && !pluginMisread(e.getValue(), other, lineOf(text, e.getKey()))) {
            disagreements.add(relative + ":" + e.getKey() + "\n    plugin: " + e.getValue() + "\n    server: " + other);
          } else {
            agreed++;
          }
        }
        corpus.probe.closeDocument(file);
      }
      Path dump = Path.of("build/differential");
      Files.createDirectories(dump);
      Files.writeString(dump.resolve("hints.txt"), String.join("\n", disagreements));
      System.out.println("HINTS: plugin " + plugin + ", server " + server + ", agreed " + agreed);
      assertTrue("the plugin showed hints (" + plugin + ")", plugin > 50);
      assertTrue("the server showed hints (" + server + ")", server > 50);
      assertTrue(disagreements.size() + " of " + plugin + " differ:\n" + String.join("\n", disagreements),
          disagreements.isEmpty());
    }
  }

  /**
   * Where the plugin's inference was simply wrong and the server has the compiler's answer: a range slice of a
   * {@code Vec} is a {@code Slice}, and {@code a ?? b} is not optional.
   */
  private static boolean pluginMisread(String plugin, String server, String line) {
    return plugin.startsWith(": Vec<") && server.equals(": Slice" + plugin.substring(": Vec".length()))
        || line.contains("??") && plugin.equals(server + "?");
  }

  private static String lineOf(String text, String key) {
    int line = Integer.parseInt(key.substring(0, key.indexOf(':')));
    return text.split("\n", -1)[line - 1];
  }

  /** The compiler's spelling of a type: {@code [T]} for an array, {@code Int} for its alias {@code I32}. */
  private static String spelling(String hint) {
    return hint.replaceAll("Array<(.*)>", "[$1]").replaceAll("\\bI32\\b", "Int");
  }

  private static JsonObject position(int line, int character) {
    JsonObject p = new JsonObject();
    p.addProperty("line", line);
    p.addProperty("character", character);
    return p;
  }
}
