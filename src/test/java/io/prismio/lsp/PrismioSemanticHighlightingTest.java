package io.prismio.lsp;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.platform.lsp.api.LspClientManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.PrismioFileType;
import io.prismio.highlighter.PrismioSyntaxHighlighter;
import io.prismio.toolchain.PrismioProjectSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The colour of a name in a running editor, with the server attached: the IDE's own markup, not the server's answer.
 *
 * <p>There used to be a second colourer, a heuristic annotator in the plugin. It sat on a lower layer than the
 * platform's semantic tokens, so the server won wherever both coloured a name, and the heuristic showed only where the
 * server said nothing and where it disagreed on something the server's legend cannot say (a {@code let mut} binding:
 * its underline merged into the server's colour). The test holds that the server is now the only one. Needs
 * {@code -Dprismio.compiler}; without it the test returns.
 */
public class PrismioSemanticHighlightingTest extends BasePlatformTestCase {

  private static final String SOURCE = """
      import std.io

      struct Point { x: Int, y: Int }
      enum Dir { North, South }
      const LIMIT = 10

      fn grow(k: Int) -> Int {
          return k
      }

      fn main() -> Int {
          let mut items: Vec<Int> = []
          let p = Point { x: 1, y: 2 }
          let d = Dir.North
          items.push(grow(p.x))
          println(items.length, LIMIT)
          return 0
      }
      """;

  /**
   * This class's own project. The light project the other platform tests share keeps the folder it was created in for
   * the first test only, and the server starts in the project's folder, so a run that reached this test later saw a
   * server that could not start. A server left running in a shared project also changes what Reformat Code does for
   * the tests after it.
   */
  private static final LightProjectDescriptor OWN_PROJECT = new LightProjectDescriptor();

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return OWN_PROJECT;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      LspClientManager.getInstance(getProject()).stopClients(PrismioLspIntegrationProvider.class);
    } finally {
      super.tearDown();
    }
  }

  /** Every highlighter on exactly this range that carries one of the plugin's colour keys, lowest layer first. */
  private List<RangeHighlighter> keyed(int start, int end) {
    List<RangeHighlighter> found = new ArrayList<>();
    for (RangeHighlighter h : DocumentMarkupModel.forDocument(myFixture.getEditor().getDocument(), getProject(), true)
        .getAllHighlighters()) {
      TextAttributesKey key = h.getTextAttributesKey();
      if (h.getStartOffset() == start && h.getEndOffset() == end && key != null && key.getExternalName().startsWith("PRISMIO_")) {
        found.add(h);
      }
    }
    found.sort(Comparator.comparingInt(RangeHighlighter::getLayer));
    return found;
  }

  public void testTheServerIsTheOnlyColourerOfANameAndAMutableBindingHasNoExtraEffect() throws Exception {
    String compiler = System.getProperty("prismio.compiler");
    if (compiler == null) {
      return;
    }
    PrismioProjectSettings.State settings = PrismioProjectSettings.getInstance(getProject()).getState();
    settings.compilerPath = compiler;
    settings.useLanguageServer = true;
    myFixture.configureByText(PrismioFileType.INSTANCE, SOURCE);

    int items = SOURCE.indexOf("items");
    long deadline = System.currentTimeMillis() + 90_000;
    while (keyed(items, items + 5).isEmpty() && System.currentTimeMillis() < deadline) {
      myFixture.doHighlighting();
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      Thread.sleep(250);
    }

    // `items` in `let mut items`: one colour, the server's, and it is the local variable's.
    List<RangeHighlighter> onItems = keyed(items, items + 5);
    assertEquals(onItems.toString(), 1, onItems.size());
    assertEquals(PrismioSyntaxHighlighter.LOCAL_VARIABLE, onItems.get(0).getTextAttributesKey());

    // A function, a type, a field: one colour each, and the one the server's kind maps to.
    assertOnly("grow", SOURCE.indexOf("grow(k"), PrismioSyntaxHighlighter.FUNCTION_DECLARATION);
    assertOnly("Point", SOURCE.indexOf("Point {"), PrismioSyntaxHighlighter.STRUCT_NAME);
    assertOnly("Dir", SOURCE.indexOf("Dir.North"), PrismioSyntaxHighlighter.ENUM_NAME);
    assertOnly("North", SOURCE.indexOf("North\n"), PrismioSyntaxHighlighter.ENUM_VARIANT);
    assertOnly("push", SOURCE.indexOf("push"), PrismioSyntaxHighlighter.METHOD_CALL);
    assertOnly("LIMIT", SOURCE.indexOf("LIMIT"), PrismioSyntaxHighlighter.CONSTANT);

    // The colour a user sees is the scheme's for that one key: the underline the old annotator's key carried is gone.
    TextAttributes seen = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(onItems.get(0).getTextAttributesKey());
    TextAttributes local = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PrismioSyntaxHighlighter.LOCAL_VARIABLE);
    assertEquals(local.getForegroundColor(), seen.getForegroundColor());
  }

  private void assertOnly(String name, int start, TextAttributesKey expected) {
    assertTrue(name, start >= 0);
    List<RangeHighlighter> found = keyed(start, start + name.length());
    assertEquals(name + " " + found, 1, found.size());
    assertEquals(name, expected, found.get(0).getTextAttributesKey());
  }
}
