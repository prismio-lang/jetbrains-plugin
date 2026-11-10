package io.prismio;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.platform.backend.documentation.InlineDocumentation;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.debugger.PrismioLineBreakpointType;
import io.prismio.documentation.PrismioInlineDocumentationProvider;
import io.prismio.folding.PrismioFoldingBuilder;
import io.prismio.lexer.PrismioLexer;
import java.util.Collection;

public class PrismioCodeInsightTest extends BasePlatformTestCase {
  public void testLineCommentRoundTrip() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "<caret>let value: Int = 1");
    CommentByLineCommentAction action = new CommentByLineCommentAction();

    action.actionPerformedImpl(getProject(), myFixture.getEditor());
    myFixture.checkResult("//let value: Int = 1");

    action.actionPerformedImpl(getProject(), myFixture.getEditor());
    myFixture.checkResult("let value: Int = 1");
  }

  public void testBlockCommentIsAutoClosed() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "<caret>");

    myFixture.type("/*");

    myFixture.checkResult("/*<caret>*/");
  }

  public void testEnterExpandsAndMaintainsBlockCommentIndentation() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "<caret>");
    myFixture.type("/*");

    myFixture.type('\n');
    myFixture.checkResult("/*\n   <caret>\n*/");

    myFixture.type('\n');
    myFixture.checkResult("/*\n   \n   <caret>\n*/");
  }

  public void testEnterInsideDocumentationCommentKeepsItsIndentation() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "fn main() {\n    /**<caret>*/\n}");

    myFixture.type('\n');

    myFixture.checkResult("fn main() {\n    /**\n       <caret>\n    */\n}");
  }

  /** The document as it is, with the caret marked, so a failure shows both texts. */
  private String textWithCaret() {
    String text = myFixture.getEditor().getDocument().getText();
    int caret = myFixture.getCaretOffset();
    return text.substring(0, caret) + "<caret>" + text.substring(caret);
  }

  /**
   * Enter between `{` and the code after it on the same line moves that code to the next line, one
   * level in. IntelliJ has already indented the new line when the handler runs, so the handler must
   * set the indent and not add to it: it used to insert a second copy, and the code landed twice as
   * far in as it should (`pressing Enter at `{ p`` put `p` 16 columns in instead of 8).
   */
  public void testEnterAfterAnOpeningBraceMidLineIndentsOneLevel() {
    myFixture.configureByText(PrismioFileType.INSTANCE,
        "fn main() {\n    if (value == none) { <caret>emit(fd) } else { other(fd) }\n}");

    myFixture.type('\n');

    assertEquals(
        "fn main() {\n    if (value == none) {\n        <caret>emit(fd) } else { other(fd) }\n}", textWithCaret());
  }

  public void testEnterAfterAnOpeningBraceMidLineAtTheLeftEdge() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "if (value == none) { <caret>emit(fd) }");

    myFixture.type('\n');

    assertEquals("if (value == none) {\n    <caret>emit(fd) }", textWithCaret());
  }

  public void testEnterAtTheEndOfAnOpeningBraceLineIndentsOneLevel() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "fn main() {\n    if (a) {<caret>\n    }\n}");

    myFixture.type('\n');

    assertEquals("fn main() {\n    if (a) {\n        <caret>\n    }\n}", textWithCaret());
  }

  public void testPlainEnterKeepsTheIndentOnceNotTwice() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "fn main() {\n    let x = 1<caret>\n}");

    myFixture.type('\n');

    assertEquals("fn main() {\n    let x = 1\n    <caret>\n}", textWithCaret());
  }

  public void testBlockCommentIndentationIsRelativeToOpeningLine() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "fn main() {\n    /*<caret>*/\n}");

    myFixture.type('\n');

    myFixture.checkResult("fn main() {\n    /*\n       <caret>\n    */\n}");
  }

  public void testDocumentationCommentsCanBeRenderedInline() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        /**
         * Computes the final value.
         * Safe for repeated calls.
         */
        fn compute() {}
        """);

    Collection<InlineDocumentation> items =
        new PrismioInlineDocumentationProvider().inlineDocumentationItems(myFixture.getFile());

    assertTrue(com.intellij.platform.backend.documentation.InlineDocumentationProvider.EP_NAME
            .getExtensionList()
            .stream()
            .anyMatch(PrismioInlineDocumentationProvider.class ::isInstance));
    assertEquals(1, items.size());
    String rendered = items.iterator().next().renderText();
    assertTrue(rendered.contains("Computes the final value."));
    assertTrue(rendered.contains("Safe for repeated calls."));
    assertFalse(rendered.contains("/**"));
  }

  public void testBlockCommentIsNotAutoClosedInsideString() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "\"before <caret> after\"");

    myFixture.type("/*");

    myFixture.checkResult("\"before /*<caret> after\"");
  }

  public void testMultilineFunctionBodyIsFoldable() {
    String source = """
        fn main() {
          let text = "{ not a block }"
          if (true) {
            println(text)
          }
        }

        fn oneLine() { return }
        """;
    myFixture.configureByText(PrismioFileType.INSTANCE, source);

    FoldingDescriptor[] regions = new PrismioFoldingBuilder().buildFoldRegions(
        myFixture.getFile(), myFixture.getEditor().getDocument(), false);

    assertEquals(1, regions.length);
    String foldedText = regions[0].getRange().substring(source);
    assertTrue(foldedText.contains("println(text)"));
    assertFalse(foldedText.contains("fn oneLine"));
  }

  public void testBreakpointsAreAvailableOnMeaningfulPrismioLines() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        fn main() {
          // not executable
          let value = 42
        }
        """);
    PrismioLineBreakpointType breakpointType = new PrismioLineBreakpointType();

    assertTrue(breakpointType.canPutAt(myFixture.getFile().getVirtualFile(), 0, getProject()));
    assertFalse(breakpointType.canPutAt(myFixture.getFile().getVirtualFile(), 1, getProject()));
    assertTrue(breakpointType.canPutAt(myFixture.getFile().getVirtualFile(), 2, getProject()));
    assertFalse(breakpointType.canPutAt(myFixture.getFile().getVirtualFile(), 3, getProject()));
  }

  public void testLexerAcceptsRepresentativeProgram() {
    PrismioLexer lexer = new PrismioLexer();
    lexer.start("fn main() -> Int { // result\n let value: Int? = ~40 + 2\n return value }");

    while (lexer.getTokenType() != null) {
      assertNotSame(TokenType.BAD_CHARACTER, lexer.getTokenType());
      lexer.advance();
    }
  }

  /**
   * `;` is in neither `isSeparator` nor `isOperator`, so the compiler rejects it
   * with "unexpected character". The editor says so too — this fixture used to
   * contain one, and the lexer used to call it a token.
   */
  public void testSemicolonIsNotPartOfTheLanguage() {
    PrismioLexer lexer = new PrismioLexer();
    String source = "let value = 1;";
    lexer.start(source);

    boolean flagged = false;
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() == TokenType.BAD_CHARACTER) {
        assertEquals(";", source.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
        flagged = true;
      }
      lexer.advance();
    }
    assertTrue("a semicolon should be reported as a bad character", flagged);
  }
}
