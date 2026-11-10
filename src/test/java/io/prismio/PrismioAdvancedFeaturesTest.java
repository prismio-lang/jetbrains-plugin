package io.prismio;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.execution.PrismioConsoleFilter;
import io.prismio.execution.PrismioRunLineMarkerContributor;

public class PrismioAdvancedFeaturesTest extends BasePlatformTestCase {
  public void testConsoleFilterMatchesLocation() {
    myFixture.addFileToProject("src/main.psm", "fn main() {}\n");
    PrismioConsoleFilter filter = new PrismioConsoleFilter(getProject());
    String line = "src/main.psm:1:1: error[P4001]: unknown name";
    Filter.Result result = filter.applyFilter(line, line.length());
    assertNotNull("console filter should match error line", result);
    assertNotNull("result should include hyperlink", result.getFirstHyperlinkInfo());
  }

  public void testRunLineMarkerOnMainFunction() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        fn helper() -> Int { return 1 }
        fn main() -> Int { return 0 }
        """);

    PrismioRunLineMarkerContributor contributor = new PrismioRunLineMarkerContributor();

    // Caret on 'main'
    int mainOffset = myFixture.getFile().getText().indexOf("main");
    PsiElement mainElement = myFixture.getFile().findElementAt(mainOffset);
    assertNotNull(mainElement);
    RunLineMarkerContributor.Info mainInfo = contributor.getInfo(mainElement);
    assertNotNull("main function should have a run line marker", mainInfo);

    // Caret on 'helper'
    int helperOffset = myFixture.getFile().getText().indexOf("helper");
    PsiElement helperElement = myFixture.getFile().findElementAt(helperOffset);
    assertNotNull(helperElement);
    RunLineMarkerContributor.Info helperInfo = contributor.getInfo(helperElement);
    assertNull("helper function should not have a run line marker", helperInfo);
  }

  public void testBlockCommentFormatting() {
    String original = "/*\nascdc\n   ajkfa\n*/";
    String formatted = io.prismio.formatter.PrismioBlockCommentPostFormatProcessor.formatBlockComment(original, "");
    assertEquals("/*\n   ascdc\n   ajkfa\n*/", formatted);
  }

  public void testIndentedBlockCommentFormatting() {
    String original = "/*\n    ascdc\n       ajkfa\n    */";
    String formatted = io.prismio.formatter.PrismioBlockCommentPostFormatProcessor.formatBlockComment(original, "    ");
    assertEquals("/*\n       ascdc\n       ajkfa\n    */", formatted);
  }

  public void testReformatBlockCommentWithCodeStyleManager() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        /*
        ascdc
           ajkfa
        */
        fn main() -> Int { return 0 }
        """);
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      com.intellij.psi.codeStyle.CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile());
    });
    String expected = """
        /*
           ascdc
           ajkfa
        */
        fn main() -> Int { return 0 }
        """;
    assertEquals(expected.trim(), myFixture.getFile().getText().trim());
  }

  /** A documentation comment is formatted as the block comment it is to the compiler. */
  public void testReformatDocumentationCommentWithCodeStyleManager() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        /**
        ascdc
           ajkfa
        */
        fn main() -> Int { return 0 }
        """);
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      com.intellij.psi.codeStyle.CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile());
    });
    String expected = """
        /**
           ascdc
           ajkfa
        */
        fn main() -> Int { return 0 }
        """;
    assertEquals(expected.trim(), myFixture.getFile().getText().trim());
  }

  public void testIndentedDocumentationCommentFormatting() {
    String original = "/**\n    ascdc\n       ajkfa\n    */";
    assertEquals("/**\n       ascdc\n       ajkfa\n    */",
        io.prismio.formatter.PrismioBlockCommentPostFormatProcessor.formatBlockComment(original, "    "));
  }

  /** Star-per-line comments keep their stars aligned under the opener; the formatter must not flatten them. */
  public void testStarredDocumentationCommentKeepsItsStars() {
    String original = "/**\n * Adds one.\n *   Indented note.\n */";
    assertEquals(original,
        io.prismio.formatter.PrismioBlockCommentPostFormatProcessor.formatBlockComment(original, ""));
    assertEquals("/**\n     * Adds one.\n     */",
        io.prismio.formatter.PrismioBlockCommentPostFormatProcessor.formatBlockComment(
            "/**\n* Adds one.\n  */", "    "));
  }
}
