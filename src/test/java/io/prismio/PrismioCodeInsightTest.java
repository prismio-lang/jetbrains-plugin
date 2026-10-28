package io.prismio;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.platform.backend.documentation.InlineDocumentation;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.debugger.PrismioLineBreakpointType;
import io.prismio.documentation.PrismioInlineDocumentationProvider;
import io.prismio.folding.PrismioFoldingBuilder;
import io.prismio.highlighter.PrismioSyntaxHighlighter;
import io.prismio.lexer.PrismioLexer;
import io.prismio.navigation.Declaration;
import io.prismio.navigation.DeclarationKind;
import io.prismio.navigation.DeclarationScanner;
import java.util.Collection;
import java.util.List;

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

  public void testSemanticHighlightingUsesDistinctThemeAwareRoles() {
    String source = """
        import prismio.io
        struct Point { x: Int }
        trait Drawable { fn draw() }
        enum Direction { North }
        fn compute(input: Int) {
          let mut value = 1
          let MAX_SIZE = 10
          point.move()
        }
        """;
    myFixture.configureByText(PrismioFileType.INSTANCE, source);

    List<HighlightInfo> highlights = myFixture.doHighlighting();

    assertHighlight(highlights, source, "prismio", PrismioSyntaxHighlighter.IMPORT_PATH);
    assertHighlight(highlights, source, "Point", PrismioSyntaxHighlighter.STRUCT_NAME);
    assertHighlight(highlights, source, "x", PrismioSyntaxHighlighter.FIELD);
    assertHighlight(highlights, source, "Drawable", PrismioSyntaxHighlighter.TRAIT_NAME);
    assertHighlight(highlights, source, "North", PrismioSyntaxHighlighter.ENUM_VARIANT);
    assertHighlight(highlights, source, "compute", PrismioSyntaxHighlighter.FUNCTION_DECLARATION);
    assertHighlight(highlights, source, "input", PrismioSyntaxHighlighter.PARAMETER);
    assertHighlight(highlights, source, "value", PrismioSyntaxHighlighter.MUTABLE_VARIABLE);
    assertHighlight(highlights, source, "MAX_SIZE", PrismioSyntaxHighlighter.CONSTANT);
    assertHighlight(highlights, source, "move", PrismioSyntaxHighlighter.METHOD_CALL);
  }

  public void testFormatterAppliesCoreSpacingRules() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "fn main(){let value:Int=1+2}");

    WriteCommandAction.writeCommandAction(getProject())
        .run(() -> CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile()));

    // `{ let`, not `{let`. A keyword now gets a space on both sides, which is
    // the rule that stopped `a and b` collapsing into `aand b`.
    myFixture.checkResult("fn main() { let value: Int = 1 + 2}");
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

  public void testDeclarationScannerFindsRealPrismioSymbolsAndSkipsLocals() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        let mut VERSION = 1
        struct Point { x: Int }
        enum Direction { North, South }
        trait Drawable { fn draw() }
        impl Point {
          fn distance() {
            let local = 1
          }
        }
        fn main() {}
        """);

    List<Declaration> declarations = DeclarationScanner.collect(myFixture.getFile());

    assertContainsElements(declarations.stream().map(Declaration::getName).toList(), "VERSION",
        "Point", "Direction", "Drawable", "draw", "distance", "main");
    assertFalse(
        declarations.stream().anyMatch(declaration -> "local".equals(declaration.getName())));
    assertTrue(declarations.stream().anyMatch(declaration
        -> declaration.getKind() == DeclarationKind.CONSTANT
            && "VERSION".equals(declaration.getName())));
    assertTrue(declarations.stream().anyMatch(declaration
        -> declaration.getKind() == DeclarationKind.IMPLEMENTATION
            && "Point".equals(declaration.getName())));
  }

  private static void assertHighlight(
      List<HighlightInfo> highlights, String source, String text, TextAttributesKey expectedKey) {
    int startOffset = source.indexOf(text);
    assertTrue("Missing test token: " + text, startOffset >= 0);
    int endOffset = startOffset + text.length();
    assertTrue("Expected " + expectedKey.getExternalName() + " on '" + text + "'",
        highlights.stream().anyMatch(info
            -> info.startOffset == startOffset && info.endOffset == endOffset
                && expectedKey.equals(info.forcedTextAttributesKey)));
  }
}
