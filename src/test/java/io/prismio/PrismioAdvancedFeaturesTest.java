package io.prismio;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.annotator.PrismioDiagnostic;
import io.prismio.annotator.PrismioDiagnosticParser;
import io.prismio.execution.PrismioConsoleFilter;
import io.prismio.execution.PrismioRunLineMarkerContributor;
import java.util.List;

public class PrismioAdvancedFeaturesTest extends BasePlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    io.prismio.toolchain.PrismioProjectSettings.getInstance(getProject()).getState().stdlibPath =
        io.prismio.lsp.PrismioServerCompletionTest.STDLIB;
  }


  public void testDiagnosticParser() {
    String jsonOutput = """
        {"kind":"diagnostic","schemaVersion":1,"severity":"error","code":"P4001","file":"src/main.psm","line":12,"column":5,"length":4,"message":"unknown name `item`"}
        {"kind":"summary","schemaVersion":1,"errors":1,"warnings":0}
        """;
    List<PrismioDiagnostic> diagnostics = PrismioDiagnosticParser.parseOutput(jsonOutput);
    assertEquals(1, diagnostics.size());
    PrismioDiagnostic diag = diagnostics.get(0);
    assertEquals("error", diag.severity());
    assertEquals("P4001", diag.code());
    assertEquals("src/main.psm", diag.file());
    assertEquals(12, diag.line());
    assertEquals(5, diag.column());
    assertEquals(4, diag.length());
    assertEquals("unknown name `item`", diag.message());
  }

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
