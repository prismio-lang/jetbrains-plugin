package io.prismio.lsp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import junit.framework.TestCase;

/**
 * Go to declaration, find usages and rename, asked of the language server.
 *
 * <p>These were the plugin's own navigation tests (a call resolves to its function, a type name to its struct, an
 * import to its module, a method through its receiver's type, an overload chosen by its argument, usages without the
 * declaration, a rename that rewrites call sites and not a similar name). The plugin's scope resolver was deleted once
 * every one of them held of the server. Where the plugin resolved a name from a file the document does not import, the
 * test now imports it: the compiler needs the import, and the server answers what the compiler resolves.
 *
 * <p>Needs a toolchain with the compiler repository's navigation commit; skipped without {@code -Dprismio.checkout}
 * and {@code -Dprismio.compiler}.
 */
public class PrismioServerNavigationTest extends TestCase {

  private ServerCorpus corpus;
  private Path directory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    corpus = ServerCorpus.start();
    directory = Files.createTempDirectory("prismio-navigation");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (corpus != null) {
        corpus.close();
      }
      try (java.util.stream.Stream<Path> walk = Files.walk(directory)) {
        walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    } finally {
      super.tearDown();
    }
  }

  /** The document under test: its text without the caret, and where the caret was. */
  private final class Doc {
    final Path file;
    final String text;
    final int line;
    final int column;

    Doc(String source, Map<String, String> others) throws IOException, InterruptedException {
      int caret = source.indexOf("<caret>");
      text = source.replace("<caret>", "");
      file = directory.resolve("main.psm");
      for (Map.Entry<String, String> other : others.entrySet()) {
        Path path = directory.resolve(other.getKey());
        Files.createDirectories(path.getParent());
        Files.writeString(path, other.getValue());
      }
      Files.writeString(file, text);
      line = (int) text.substring(0, Math.max(caret, 0)).chars().filter(c -> c == '\n').count();
      column = caret - (text.lastIndexOf('\n', caret - 1) + 1);
      assertTrue("the server analysed the document", corpus.probe.open(file, text, 90));
    }

    /** The targets of Go to Declaration as {@code file name: line text}, or an empty list. */
    List<String> definition() throws Exception {
      return locations(corpus.probe.at("textDocument/definition", file, line, column));
    }

    List<String> usages(boolean includeDeclaration) throws Exception {
      JsonObject params = new JsonObject();
      JsonObject document = new JsonObject();
      document.addProperty("uri", file.toUri().toString());
      params.add("textDocument", document);
      JsonObject position = new JsonObject();
      position.addProperty("line", line);
      position.addProperty("character", column);
      params.add("position", position);
      JsonObject context = new JsonObject();
      context.addProperty("includeDeclaration", includeDeclaration);
      params.add("context", context);
      JsonObject reply = corpus.probe.request("textDocument/references", params, 60);
      return locations(reply == null ? null : reply.get("result"));
    }

    /** The names of the outline (every level), as {@code name} strings in document order. */
    List<String> outline() throws Exception {
      JsonObject params = new JsonObject();
      JsonObject document = new JsonObject();
      document.addProperty("uri", file.toUri().toString());
      params.add("textDocument", document);
      JsonObject reply = corpus.probe.request("textDocument/documentSymbol", params, 60);
      List<String> names = new ArrayList<>();
      if (reply != null && reply.has("result") && reply.get("result").isJsonArray()) {
        collect(reply.getAsJsonArray("result"), names);
      }
      return names;
    }

    private void collect(JsonArray symbols, List<String> names) {
      for (JsonElement e : symbols) {
        JsonObject symbol = e.getAsJsonObject();
        names.add(symbol.get("name").getAsString());
        if (symbol.has("children")) {
          collect(symbol.getAsJsonArray("children"), names);
        }
      }
    }

    /** The names {@code workspace/symbol} offers for a query. */
    List<String> workspaceSymbols(String query) throws Exception {
      JsonObject params = new JsonObject();
      params.addProperty("query", query);
      JsonObject reply = corpus.probe.request("workspace/symbol", params, 60);
      List<String> names = new ArrayList<>();
      if (reply != null && reply.has("result") && reply.get("result").isJsonArray()) {
        for (JsonElement e : reply.getAsJsonArray("result")) {
          names.add(e.getAsJsonObject().get("name").getAsString());
        }
      }
      return names;
    }

    /** How many places the document highlights for the name at the caret. */
    int highlights() throws Exception {
      JsonObject params = new JsonObject();
      JsonObject document = new JsonObject();
      document.addProperty("uri", file.toUri().toString());
      params.add("textDocument", document);
      JsonObject position = new JsonObject();
      position.addProperty("line", line);
      position.addProperty("character", column);
      params.add("position", position);
      JsonObject reply = corpus.probe.request("textDocument/documentHighlight", params, 60);
      return reply != null && reply.has("result") && reply.get("result").isJsonArray()
          ? reply.getAsJsonArray("result").size() : 0;
    }

    /** The document's text after renaming the name at the caret, or null when the server refuses. */
    String renamed(String to) throws Exception {
      JsonObject params = new JsonObject();
      JsonObject document = new JsonObject();
      document.addProperty("uri", file.toUri().toString());
      params.add("textDocument", document);
      JsonObject position = new JsonObject();
      position.addProperty("line", line);
      position.addProperty("character", column);
      params.add("position", position);
      params.addProperty("newName", to);
      JsonObject reply = corpus.probe.request("textDocument/rename", params, 60);
      if (reply == null || !reply.has("result") || !reply.get("result").isJsonObject()) {
        return null;
      }
      JsonObject changes = reply.getAsJsonObject("result").getAsJsonObject("changes");
      JsonArray edits = changes == null ? null : changes.getAsJsonArray(file.toUri().toString());
      if (edits == null) {
        return text;
      }
      List<JsonObject> sorted = new ArrayList<>();
      for (JsonElement e : edits) {
        sorted.add(e.getAsJsonObject());
      }
      sorted.sort(Comparator.comparingInt((JsonObject e) -> start(e, "line")).thenComparingInt(e -> start(e, "character")).reversed());
      String[] lines = text.split("\n", -1);
      for (JsonObject edit : sorted) {
        JsonObject range = edit.getAsJsonObject("range");
        int l = range.getAsJsonObject("start").get("line").getAsInt();
        int from = range.getAsJsonObject("start").get("character").getAsInt();
        int to2 = range.getAsJsonObject("end").get("character").getAsInt();
        lines[l] = lines[l].substring(0, from) + edit.get("newText").getAsString() + lines[l].substring(to2);
      }
      return String.join("\n", lines);
    }

    private int start(JsonObject edit, String field) {
      return edit.getAsJsonObject("range").getAsJsonObject("start").get(field).getAsInt();
    }
  }

  private List<String> locations(JsonElement result) throws IOException {
    List<String> found = new ArrayList<>();
    if (result == null || result.isJsonNull()) {
      return found;
    }
    JsonArray array = result.isJsonArray() ? result.getAsJsonArray() : new JsonArray();
    for (JsonElement e : array) {
      JsonObject location = e.getAsJsonObject();
      Path file = Path.of(java.net.URI.create(location.get("uri").getAsString()));
      int line = location.getAsJsonObject("range").getAsJsonObject("start").get("line").getAsInt();
      List<String> text = Files.readAllLines(file);
      found.add(file.getFileName() + ": " + text.get(line).strip());
    }
    return found;
  }

  public void testACallResolvesToItsFunction() throws Exception {
    if (corpus == null) {
      return;
    }
    Doc doc = new Doc("""
        fn helper(value: Int) -> Int {
            return value * 2
        }

        fn main() -> Int {
            return hel<caret>per(21)
        }
        """, Map.of());
    assertEquals(List.of("main.psm: fn helper(value: Int) -> Int {"), doc.definition());
  }

  public void testAStructNameResolvesAndAKeywordOrUnknownNameDoesNot() throws Exception {
    if (corpus == null) {
      return;
    }
    String source = """
        struct Point { x: Int }

        fn use() -> Int {
            let p: Po<caret>int = Point { x: 1 }
            return p.x
        }
        """;
    assertEquals(List.of("main.psm: struct Point { x: Int }"), new Doc(source, Map.of()).definition());
    assertEquals(List.of(), new Doc("""
        fn main() -> Int {
            ret<caret>urn 0
        }
        """, Map.of()).definition());
    assertEquals(List.of(), new Doc("""
        fn main() -> Int {
            return absent<caret>Name(1)
        }
        """, Map.of()).definition());
  }

  public void testAnImportNavigatesToItsModuleFromAnySegment() throws Exception {
    if (corpus == null) {
      return;
    }
    List<String> io = new Doc("""
        import std.i<caret>o

        fn main() -> Int { return 0 }
        """, Map.of()).definition();
    assertEquals(io.toString(), 1, io.size());
    assertTrue(io.toString(), io.get(0).startsWith("io.psm:"));
    Map<String, String> project = Map.of("ir/expr.psm", "public fn emit() { }\n");
    List<String> whole = new Doc("import i<caret>r.expr\n\nfn main() -> Int { return 0 }\n", project).definition();
    List<String> leaf = new Doc("import ir.ex<caret>pr\n\nfn main() -> Int { return 0 }\n", project).definition();
    assertEquals(whole.toString(), 1, whole.size());
    assertTrue(whole.toString(), whole.get(0).startsWith("expr.psm:"));
    assertEquals(whole, leaf);
    assertEquals(List.of(), new Doc("import no.such.mod<caret>ule\n\nfn main() -> Int { return 0 }\n", Map.of()).definition());
  }

  public void testResolutionCrossesFilesThroughTheImportAndOverloadsResolveByArgument() throws Exception {
    if (corpus == null) {
      return;
    }
    Map<String, String> other = Map.of("other.psm", "public fn shared(value: Int) -> Int {\n    return value + 1\n}\n");
    Doc across = new Doc("""
        import other

        fn main() -> Int {
            return sha<caret>red(1)
        }
        """, other);
    assertEquals(List.of("other.psm: public fn shared(value: Int) -> Int {"), across.definition());

    // The plugin's scope resolver let the current file win over an import. The compiler does not: a function of the
    // same name and parameter types in both is "duplicate definition" (P4001), and one that differs in its parameter
    // types is an overload, chosen by the arguments.
    Doc overload = new Doc("""
        import other

        fn shared(text: String) -> Int {
            return 2
        }

        fn main() -> Int {
            return sha<caret>red(1) + shared("x")
        }
        """, other);
    assertEquals(List.of("other.psm: public fn shared(value: Int) -> Int {"), overload.definition());
  }

  public void testAMethodCallResolvesThroughTheReceiversType() throws Exception {
    if (corpus == null) {
      return;
    }
    List<String> targets = new Doc("""
        import std.vec

        fn main() -> Int {
            let xs: Vec<Int> = [1, 2, 3]
            xs.cont<caret>ains(1)
            return 0
        }
        """, Map.of()).definition();
    assertEquals(targets.toString(), 1, targets.size());
    assertTrue(targets.toString(), targets.get(0).startsWith("vec.psm: public fn contains<T: Eq>"));
  }

  public void testUsagesAreTheCallSitesAndNotTheDeclaration() throws Exception {
    if (corpus == null) {
      return;
    }
    Doc doc = new Doc("""
        fn help<caret>er(value: Int) -> Int { return value }

        fn main() -> Int { return helper(1) + helper(2) }
        """, Map.of());
    assertEquals(doc.usages(false).toString(), 2, doc.usages(false).size());
    assertEquals(3, doc.usages(true).size());
  }

  public void testRenameRewritesCallSitesAndNotASimilarName() throws Exception {
    if (corpus == null) {
      return;
    }
    assertEquals("""
        fn renamed(value: Int) -> Int { return value }

        fn main() -> Int { return renamed(1) + renamed(2) }
        """, new Doc("""
        fn help<caret>er(value: Int) -> Int { return value }

        fn main() -> Int { return helper(1) + helper(2) }
        """, Map.of()).renamed("renamed"));
    assertEquals("""
        fn renamed(value: Int) -> Int { return value }

        fn other() -> Int { return helperish(1) }
        """, new Doc("""
        fn help<caret>er(value: Int) -> Int { return value }

        fn other() -> Int { return helperish(1) }
        """, Map.of()).renamed("renamed"));
  }

  public void testALocalAParameterAndAFieldResolve() throws Exception {
    if (corpus == null) {
      return;
    }
    assertEquals(List.of("main.psm: let myLocal = 42"), new Doc("""
        fn test() -> Int {
            let myLocal = 42
            return myLo<caret>cal + 1
        }
        """, Map.of()).definition());
    assertEquals(List.of("main.psm: fn calculate(myParam: Int) -> Int {"), new Doc("""
        fn calculate(myParam: Int) -> Int {
            return myPa<caret>ram * 2
        }
        """, Map.of()).definition());
    assertEquals(List.of("main.psm: x: Int,"), new Doc("""
        struct Point {
            x: Int,
            y: Int
        }

        fn getX(p: Point) -> Int {
            return p.<caret>x
        }
        """, Map.of()).definition());
  }

  /** The outline lists what the file declares, and not what a function declares inside itself. */
  public void testTheOutlineListsDeclarationsAndSkipsLocals() throws Exception {
    if (corpus == null) {
      return;
    }
    List<String> names = new Doc("""
        let mut VERSION = 1
        struct Point { x: Int }
        enum Direction { North, South }
        trait Drawable { fn draw() }
        impl Point {
          fn distance() {
            let local = 1
          }
        }
        codeset Status: U16 { Ok = 200, NotFound = 404 }
        public codeset Delta { Back = -1, Stay }
        fn main() {}
        """, Map.of()).outline();
    for (String expected : List.of("VERSION", "Point", "Direction", "Drawable", "draw", "distance", "Status", "Delta", "main")) {
      assertTrue(expected + " is in the outline: " + names, names.contains(expected));
    }
    assertFalse("a local is not in the outline: " + names, names.contains("local"));
  }

  public void testGoToSymbolFindsADeclarationByPartOfItsName() throws Exception {
    if (corpus == null) {
      return;
    }
    Doc doc = new Doc("""
        struct PointCloud { x: Int }

        fn pointOf(c: PointCloud) -> Int { return c.x }

        fn main() -> Int { return 0 }
        """, Map.of());
    List<String> found = doc.workspaceSymbols("Point");
    assertTrue(found.toString(), found.contains("PointCloud"));
    assertFalse(found.toString(), found.contains("main"));
  }

  public void testHighlightsMarkEveryUseOfTheNameUnderTheCaret() throws Exception {
    if (corpus == null) {
      return;
    }
    assertEquals(3, new Doc("""
        fn help<caret>er(value: Int) -> Int { return value }

        fn main() -> Int { return helper(1) + helper(2) }
        """, Map.of()).highlights());
  }
}
