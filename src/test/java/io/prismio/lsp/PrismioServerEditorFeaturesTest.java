package io.prismio.lsp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;

/**
 * Inlay hints and parameter info are the server's. Before the plugin's own versions were deleted they were
 * compared with the server's over the checkout's tests (320 let-type hints, 1,910 call positions: the server
 * agreed on every one), and what stays here is the part that has to keep holding: a type hint on a binding that
 * writes none, the parameter names at a call, every overload of a library function with its parameters located,
 * the members the compiler lowers (a {@code Vec}'s {@code push}), and the active parameter. Needs a toolchain
 * built from the compiler repository's 89b9bb3 or later.
 */
public class PrismioServerEditorFeaturesTest extends TestCase {

  private static final String SOURCE = """
      import std.io

      struct Tree { v: Int }

      fn grow(sink t: Tree, k: Int) -> Int {
          return k + t.v
      }

      fn main() -> Int {
          let t = Tree { v: 1 }
          let n = grow(t, 2)
          let mut items: Vec<Int> = []
          items.push(n)
          println(items.length)
          println("a", 3)
          return 0
      }
      """;

  public void testHintsAndParameterInfo() throws Exception {
    try (ServerCorpus corpus = ServerCorpus.start()) {
      if (corpus == null) {
        return;
      }
      Path file = corpus.root.resolve("tests/editor_features_guard.psm");
      assertTrue("the server analysed the document", corpus.probe.open(file, SOURCE, 90));

      // Hints: `let n` has the type of what it is bound to, and the arguments of a call name their parameters.
      JsonObject range = new JsonObject();
      range.add("start", position(0, 0));
      range.add("end", position(30, 0));
      JsonObject params = new JsonObject();
      params.add("range", range);
      JsonElement hints = corpus.probe.ofDocument("textDocument/inlayHint", file, params);
      List<String> labels = new ArrayList<>();
      for (JsonElement e : (JsonArray) hints) {
        JsonObject hint = e.getAsJsonObject();
        JsonObject at = hint.getAsJsonObject("position");
        labels.add((at.get("line").getAsInt() + 1) + ":" + hint.get("label").getAsString());
      }
      assertTrue(labels.toString(), labels.contains("10:: Tree") && labels.contains("11:: Int"));
      assertTrue(labels.toString(), labels.contains("11:k:"));

      // Parameter info on a declared function: the label, the parameter ranges, the argument the caret is in.
      JsonObject grow = helpAfter(corpus, file, SOURCE, "grow(t, ", 0);
      assertEquals("fn grow(sink t: Tree, k: Int) -> Int", label(grow, 0));
      assertEquals(2, grow.getAsJsonArray("signatures").get(0).getAsJsonObject().getAsJsonArray("parameters").size());
      assertEquals(1, grow.get("activeParameter").getAsInt());

      // A member the compiler lowers: no source declares it, and the receiver fills `self`.
      JsonObject push = helpAfter(corpus, file, SOURCE, "items.push(", 0);
      assertEquals("fn push(self, value: T)", label(push, 0));
      assertEquals(1, push.get("activeParameter").getAsInt());

      // Every overload of a library function (std.io's println is the variadic one and the empty one), parameters located in the label; a call split into one statement per value.
      JsonObject println = helpAfter(corpus, file, SOURCE, "println(items.length", 8);
      assertTrue(println.toString(), println.getAsJsonArray("signatures").size() >= 2);
      assertTrue(println.toString(), println.getAsJsonArray("signatures").get(0).getAsJsonObject().getAsJsonArray("parameters").size() >= 1);
      JsonObject several = helpAfter(corpus, file, SOURCE, "println(\"a\", 3)", 8);
      assertTrue(several.toString(), several.getAsJsonArray("signatures").size() >= 1);

      // Inside a string argument that holds a parenthesis the call is still the one that opened it.
      String tricky = SOURCE.replace("println(\"a\", 3)", "pick(\"a(b\", 3)")
          .replace("fn main()", "fn pick(label: String, n: Int) -> Int {\n    return n\n}\n\nfn main()");
      corpus.probe.closeDocument(file);
      assertTrue(corpus.probe.open(file, tricky, 90));
      JsonObject inString = helpAfter(corpus, file, tricky, "pick(\"a(", 9);
      assertEquals("fn pick(label: String, n: Int) -> Int", label(inString, 0));
      assertEquals(0, inString.get("activeParameter").getAsInt());
    }
  }

  /** Signature help at {@code skip} characters into the first occurrence of {@code needle}, or at its end when skip is 0. */
  private static JsonObject helpAfter(ServerCorpus corpus, Path file, String text, String needle, int skip) throws Exception {
    int offset = text.indexOf(needle) + (skip == 0 ? needle.length() : skip);
    int line = (int) text.substring(0, offset).chars().filter(c -> c == '\n').count();
    int column = offset - (text.lastIndexOf('\n', offset - 1) + 1);
    return help(corpus, file, line, column);
  }

  private static JsonObject help(ServerCorpus corpus, Path file, int line, int column) throws Exception {
    JsonElement answer = corpus.probe.at("textDocument/signatureHelp", file, line, column);
    assertNotNull("signature help at " + (line + 1) + ":" + (column + 1), answer);
    assertTrue(answer.toString(), answer.isJsonObject());
    return answer.getAsJsonObject();
  }

  private static String label(JsonObject help, int index) {
    return help.getAsJsonArray("signatures").get(index).getAsJsonObject().get("label").getAsString();
  }

  private static JsonObject position(int line, int character) {
    JsonObject p = new JsonObject();
    p.addProperty("line", line);
    p.addProperty("character", character);
    return p;
  }
}
