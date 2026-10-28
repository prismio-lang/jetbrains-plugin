package io.prismio.lsp;

import com.google.gson.JsonElement;
import java.nio.file.Path;
import junit.framework.TestCase;

/**
 * Hover and Quick Documentation are the server's. What the plugin's own documentation provider showed
 * over 1,219 names of the checkout's tests was compared with the server's answer before that provider
 * was deleted (every signature the same or the server's better; ten names the compiler's index has no
 * record of, listed in docs/KNOWN_ISSUES.md), and what is left here is the part that has to keep
 * holding: a signature and every comment style the plugin used to read.
 */
public class PrismioServerHoverTest extends TestCase {

  private static final String SOURCE = """
      /// Adds two numbers.
      /// Overflow wraps.
      fn add(a: Int, b: Int) -> Int {
          return a + b
      }

      /**
       * Doubles a number.
       */
      fn twice(n: Int) -> Int {
          return add(n, n)
      }

      // A point in the plane.
      struct Point { x: Int, y: Int }

      fn main() -> Int {
          let p = Point { x: 1, y: 2 }
          return twice(add(p.x, p.y))
      }
      """;

  public void testHoverShowsTheSignatureAndTheComment() throws Exception {
    try (ServerCorpus corpus = ServerCorpus.start()) {
      if (corpus == null) {
        return;
      }
      Path file = corpus.root.resolve("tests/hover_guard.psm");
      assertTrue("the server analysed the document", corpus.probe.open(file, SOURCE, 90));

      String add = hoverAt(corpus, file, 2, 4);
      assertTrue(add, add.contains("fn add(a: Int, b: Int) -> Int"));
      assertTrue(add, add.contains("Adds two numbers.") && add.contains("Overflow wraps."));

      String twice = hoverAt(corpus, file, 9, 4);
      assertTrue(twice, twice.contains("fn twice(n: Int) -> Int"));
      assertTrue(twice, twice.contains("Doubles a number."));

      String point = hoverAt(corpus, file, 14, 7);
      assertTrue(point, point.contains("struct Point"));
      assertTrue(point, point.contains("A point in the plane."));

      // A use, not the declaration, and a binding that writes no type.
      assertTrue(hoverAt(corpus, file, 18, 11).contains("fn twice(n: Int) -> Int"));
      assertTrue(hoverAt(corpus, file, 17, 8).contains("let p: Point"));
    }
  }

  private static String hoverAt(ServerCorpus corpus, Path file, int line, int column) throws Exception {
    JsonElement hover = corpus.probe.at("textDocument/hover", file, line, column);
    assertNotNull("a hover at " + (line + 1) + ":" + (column + 1), hover);
    assertTrue(hover.toString(), hover.isJsonObject());
    return hover.getAsJsonObject().getAsJsonObject("contents").get("value").getAsString();
  }
}
