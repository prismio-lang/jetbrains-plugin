package io.prismio.lsp;

import com.intellij.platform.lsp.api.customization.LspCustomization;
import com.intellij.platform.lsp.api.customization.LspCallHierarchyCustomizer;
import com.intellij.platform.lsp.api.customization.LspCallHierarchyDisabled;
import com.intellij.platform.lsp.api.customization.LspCallHierarchySupport;
import com.intellij.platform.lsp.api.customization.LspCodeActionsCustomizer;
import com.intellij.platform.lsp.api.customization.LspCodeActionsDisabled;
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport;
import com.intellij.platform.lsp.api.customization.LspCodeLensCustomizer;
import com.intellij.platform.lsp.api.customization.LspCodeLensDisabled;
import com.intellij.platform.lsp.api.customization.LspCodeLensSupport;
import com.intellij.platform.lsp.api.customization.LspCommandsCustomizer;
import com.intellij.platform.lsp.api.customization.LspCommandsDisabled;
import com.intellij.platform.lsp.api.customization.LspCommandsSupport;
import com.intellij.platform.lsp.api.customization.LspCompletionCustomizer;
import com.intellij.platform.lsp.api.customization.LspCompletionDisabled;
import com.intellij.platform.lsp.api.customization.LspCompletionSupport;
import com.intellij.platform.lsp.api.customization.LspDiagnosticsCustomizer;
import com.intellij.platform.lsp.api.customization.LspDiagnosticsDisabled;
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport;
import com.intellij.platform.lsp.api.customization.LspDocumentColorCustomizer;
import com.intellij.platform.lsp.api.customization.LspDocumentColorDisabled;
import com.intellij.platform.lsp.api.customization.LspDocumentColorSupport;
import com.intellij.platform.lsp.api.customization.LspDocumentHighlightsCustomizer;
import com.intellij.platform.lsp.api.customization.LspDocumentHighlightsDisabled;
import com.intellij.platform.lsp.api.customization.LspDocumentHighlightsSupport;
import com.intellij.platform.lsp.api.customization.LspDocumentLinkCustomizer;
import com.intellij.platform.lsp.api.customization.LspDocumentLinkDisabled;
import com.intellij.platform.lsp.api.customization.LspDocumentLinkSupport;
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolCustomizer;
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolDisabled;
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolSupport;
import com.intellij.platform.lsp.api.customization.LspFindReferencesCustomizer;
import com.intellij.platform.lsp.api.customization.LspFindReferencesDisabled;
import com.intellij.platform.lsp.api.customization.LspFindReferencesSupport;
import com.intellij.platform.lsp.api.customization.LspFoldingRangeCustomizer;
import com.intellij.platform.lsp.api.customization.LspFoldingRangeDisabled;
import com.intellij.platform.lsp.api.customization.LspFoldingRangeSupport;
import com.intellij.platform.lsp.api.customization.LspFormattingCustomizer;
import com.intellij.platform.lsp.api.customization.LspFormattingDisabled;
import com.intellij.platform.lsp.api.customization.LspFormattingSupport;
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionCustomizer;
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionDisabled;
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionSupport;
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionCustomizer;
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionDisabled;
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionSupport;
import com.intellij.platform.lsp.api.customization.LspHoverCustomizer;
import com.intellij.platform.lsp.api.customization.LspHoverDisabled;
import com.intellij.platform.lsp.api.customization.LspHoverSupport;
import com.intellij.platform.lsp.api.customization.LspInlayHintCustomizer;
import com.intellij.platform.lsp.api.customization.LspInlayHintDisabled;
import com.intellij.platform.lsp.api.customization.LspInlayHintSupport;
import com.intellij.platform.lsp.api.customization.LspOnTypeFormattingCustomizer;
import com.intellij.platform.lsp.api.customization.LspOnTypeFormattingDisabled;
import com.intellij.platform.lsp.api.customization.LspOnTypeFormattingSupport;
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsCustomizer;
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsDisabled;
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsSupport;
import com.intellij.platform.lsp.api.customization.LspRenameCustomizer;
import com.intellij.platform.lsp.api.customization.LspRenameDisabled;
import com.intellij.platform.lsp.api.customization.LspRenameSupport;
import com.intellij.platform.lsp.api.customization.LspSelectionRangeCustomizer;
import com.intellij.platform.lsp.api.customization.LspSelectionRangeDisabled;
import com.intellij.platform.lsp.api.customization.LspSelectionRangeSupport;
import com.intellij.platform.lsp.api.customization.LspSemanticTokensCustomizer;
import com.intellij.platform.lsp.api.customization.LspSemanticTokensDisabled;
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport;
import com.intellij.platform.lsp.api.customization.LspSignatureHelpCustomizer;
import com.intellij.platform.lsp.api.customization.LspSignatureHelpDisabled;
import com.intellij.platform.lsp.api.customization.LspSignatureHelpSupport;
import com.intellij.platform.lsp.api.customization.LspTypeHierarchyCustomizer;
import com.intellij.platform.lsp.api.customization.LspTypeHierarchyDisabled;
import com.intellij.platform.lsp.api.customization.LspTypeHierarchySupport;
import com.intellij.platform.lsp.api.customization.LspWorkspaceSymbolCustomizer;
import com.intellij.platform.lsp.api.customization.LspWorkspaceSymbolDisabled;
import com.intellij.platform.lsp.api.customization.LspWorkspaceSymbolSupport;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Which of the server's features the editor uses.
 *
 * <p>The platform client turns on everything the server advertises. The plugin's own implementations of
 * the same features are still here until each is deleted, and two answers to one question are worse than
 * one. So a feature is added to {@link #SERVED} in the commit that deletes the Java that used to answer it,
 * and every other one is switched off. The server advertises more than {@code SERVED} lists; that is the
 * work still to do, not a limit of the server.
 */
final class PrismioLspFeatures {

  /** The features the server answers, by the platform's name for them. */
  static final Set<String> SERVED = Set.of("diagnostics", "codeActions", "hover");

  private PrismioLspFeatures() {}

  static boolean served(@NotNull String feature) {
    return SERVED.contains(feature);
  }

  static @NotNull LspCustomization customization() {
    return new LspCustomization() {
      @Override
      public @NotNull LspGoToDefinitionCustomizer getGoToDefinitionCustomizer() {
        return served("goToDefinition") ? new LspGoToDefinitionSupport() : LspGoToDefinitionDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspGoToTypeDefinitionCustomizer getGoToTypeDefinitionCustomizer() {
        return served("goToTypeDefinition") ? new LspGoToTypeDefinitionSupport() : LspGoToTypeDefinitionDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspHoverCustomizer getHoverCustomizer() {
        return served("hover") ? new LspHoverSupport() : LspHoverDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspCompletionCustomizer getCompletionCustomizer() {
        return served("completion") ? new LspCompletionSupport() : LspCompletionDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspSemanticTokensCustomizer getSemanticTokensCustomizer() {
        return served("semanticTokens") ? new LspSemanticTokensSupport() : LspSemanticTokensDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspDiagnosticsCustomizer getDiagnosticsCustomizer() {
        return served("diagnostics") ? new LspDiagnosticsSupport() : LspDiagnosticsDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspCodeActionsCustomizer getCodeActionsCustomizer() {
        return served("codeActions") ? new LspCodeActionsSupport() : LspCodeActionsDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspCommandsCustomizer getCommandsCustomizer() {
        return served("commands") ? new LspCommandsSupport() : LspCommandsDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspFormattingCustomizer getFormattingCustomizer() {
        return served("formatting") ? new LspFormattingSupport() : LspFormattingDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspOnTypeFormattingCustomizer getOnTypeFormattingCustomizer() {
        return served("onTypeFormatting") ? new LspOnTypeFormattingSupport() : LspOnTypeFormattingDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspFindReferencesCustomizer getFindReferencesCustomizer() {
        return served("findReferences") ? new LspFindReferencesSupport() : LspFindReferencesDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspOptimizeImportsCustomizer getOptimizeImportsCustomizer() {
        return served("optimizeImports") ? new LspOptimizeImportsSupport() : LspOptimizeImportsDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspDocumentColorCustomizer getDocumentColorCustomizer() {
        return served("documentColor") ? new LspDocumentColorSupport() : LspDocumentColorDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspDocumentLinkCustomizer getDocumentLinkCustomizer() {
        return served("documentLink") ? new LspDocumentLinkSupport() : LspDocumentLinkDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspFoldingRangeCustomizer getFoldingRangeCustomizer() {
        return served("foldingRange") ? new LspFoldingRangeSupport() : LspFoldingRangeDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspInlayHintCustomizer getInlayHintCustomizer() {
        return served("inlayHint") ? new LspInlayHintSupport() : LspInlayHintDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspDocumentHighlightsCustomizer getDocumentHighlightsCustomizer() {
        return served("documentHighlights") ? new LspDocumentHighlightsSupport() : LspDocumentHighlightsDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspSignatureHelpCustomizer getSignatureHelpCustomizer() {
        return served("signatureHelp") ? new LspSignatureHelpSupport() : LspSignatureHelpDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspDocumentSymbolCustomizer getDocumentSymbolCustomizer() {
        return served("documentSymbol") ? new LspDocumentSymbolSupport() : LspDocumentSymbolDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspWorkspaceSymbolCustomizer getWorkspaceSymbolCustomizer() {
        return served("workspaceSymbol") ? new LspWorkspaceSymbolSupport() : LspWorkspaceSymbolDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspCallHierarchyCustomizer getCallHierarchyCustomizer() {
        return served("callHierarchy") ? new LspCallHierarchySupport() : LspCallHierarchyDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspTypeHierarchyCustomizer getTypeHierarchyCustomizer() {
        return served("typeHierarchy") ? new LspTypeHierarchySupport() : LspTypeHierarchyDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspSelectionRangeCustomizer getSelectionRangeCustomizer() {
        return served("selectionRange") ? new LspSelectionRangeSupport() : LspSelectionRangeDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspCodeLensCustomizer getCodeLensCustomizer() {
        return served("codeLens") ? new LspCodeLensSupport() : LspCodeLensDisabled.INSTANCE;
      }

      @Override
      public @NotNull LspRenameCustomizer getRenameCustomizer() {
        return served("rename") ? new LspRenameSupport() : LspRenameDisabled.INSTANCE;
      }
    };
  }
}
