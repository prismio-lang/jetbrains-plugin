package io.prismio.lsp;

import com.intellij.platform.lsp.api.customization.LspCustomization;
import com.intellij.platform.lsp.api.customization.LspDocumentHighlightsSupport;
import com.intellij.platform.lsp.api.customization.LspFormattingSupport;
import com.intellij.platform.lsp.api.customization.LspRenameSupport;
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.PrismioFileType;
import io.prismio.highlighter.PrismioSyntaxHighlighter;
import java.util.List;

/**
 * What the platform's LSP client does with a Prismio file, which no test that talks to the server directly can see.
 *
 * <p>The client switches rename, usage highlights and server-only formatting on by default for plain text and files the
 * IDE has no formatter for, and nothing else. Rename was greyed out in {@code runIde} until the plugin said "a Prismio
 * file" for each, and Reformat Code would have used the plugin's indenter instead of {@code prismio fmt}.
 */
public class PrismioLspFeaturesTest extends BasePlatformTestCase {

  public void testRenameHighlightsAndFormattingAreOnForAPrismioFile() {
    PsiFile file = myFixture.configureByText(PrismioFileType.INSTANCE, "fn main() -> Int { return 0 }\n");
    LspCustomization customization = PrismioLspFeatures.customization();

    assertTrue(customization.getRenameCustomizer() instanceof LspRenameSupport);
    assertTrue("Rename is available in a .psm file",
        ((LspRenameSupport) customization.getRenameCustomizer()).shouldRunRename(file));

    assertTrue(customization.getDocumentHighlightsCustomizer() instanceof LspDocumentHighlightsSupport);
    assertTrue("usages are highlighted in a .psm file",
        ((LspDocumentHighlightsSupport) customization.getDocumentHighlightsCustomizer()).shouldAskServerForDocumentHighlights(file));

    assertTrue(customization.getFormattingCustomizer() instanceof LspFormattingSupport);
    assertTrue("Reformat Code is the server's for a .psm file",
        ((LspFormattingSupport) customization.getFormattingCustomizer())
            .shouldFormatThisFileExclusivelyByServer(file.getVirtualFile(), true, false));
  }

  public void testSemanticTokensAreAskedForAPrismioFileAndColouredByThePluginsKeys() {
    PsiFile file = myFixture.configureByText(PrismioFileType.INSTANCE, "fn main() -> Int { return 0 }\n");
    LspCustomization customization = PrismioLspFeatures.customization();

    assertTrue(customization.getSemanticTokensCustomizer() instanceof LspSemanticTokensSupport);
    LspSemanticTokensSupport support = (LspSemanticTokensSupport) customization.getSemanticTokensCustomizer();
    assertTrue("names are coloured by the server in a .psm file", support.shouldAskServerForSemanticTokens(file));

    // Every type in the server's legend (lsp/tokens.psm) has a colour of the plugin's own, and a declaration differs from a use.
    for (String type : new String[] {"namespace", "enum", "interface", "struct", "typeParameter", "parameter", "variable", "property",
        "enumMember", "function", "method"}) {
      assertNotNull(type, support.getTextAttributesKey(type, List.of()));
    }
    assertSame(PrismioSyntaxHighlighter.FUNCTION_CALL, support.getTextAttributesKey("function", List.of()));
    assertSame(PrismioSyntaxHighlighter.FUNCTION_DECLARATION, support.getTextAttributesKey("function", List.of("declaration")));
    assertSame(PrismioSyntaxHighlighter.CONSTANT, support.getTextAttributesKey("variable", List.of("readonly")));
    assertSame(PrismioSyntaxHighlighter.LOCAL_VARIABLE, support.getTextAttributesKey("variable", List.of("declaration")));
  }

  public void testEveryFeatureTheEditorUsesIsServed() {
    for (String feature : new String[] {"goToDefinition", "findReferences", "rename", "documentSymbol", "workspaceSymbol",
        "documentHighlights", "hover", "completion", "signatureHelp", "inlayHint", "formatting", "diagnostics", "codeActions", "semanticTokens"}) {
      assertTrue(feature + " is in SERVED", PrismioLspFeatures.served(feature));
    }
  }
}
