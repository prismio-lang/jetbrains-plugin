package io.prismio.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import io.prismio.lexer.PrismioLexer;
import io.prismio.psi.PrismioTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Provides collapsible regions for multiline Prismio function bodies. */
public final class PrismioFoldingBuilder extends FoldingBuilderEx implements DumbAware {
  private static final Set<String> DECLARATION_BOUNDARIES =
      Set.of("extern", "fn", "struct", "enum", "codeset", "typelist", "trait", "impl", "import");

  @Override
  public FoldingDescriptor @NotNull[] buildFoldRegions(
      @NotNull PsiElement root, @NotNull Document document, boolean quick) {
    List<FoldingDescriptor> descriptors = new ArrayList<>();
    Lexer lexer = new PrismioLexer();
    lexer.start(document.getCharsSequence());

    boolean waitingForBody = false;
    boolean parametersStarted = false;
    boolean parametersClosed = false;
    int parenthesisDepth = 0;
    int bodyDepth = 0;
    int bodyStartOffset = -1;

    while (lexer.getTokenType() != null) {
      IElementType tokenType = lexer.getTokenType();
      int tokenStart = lexer.getTokenStart();
      int tokenEnd = lexer.getTokenEnd();
      String tokenText = document.getCharsSequence().subSequence(tokenStart, tokenEnd).toString();

      if (bodyDepth > 0) {
        if (tokenType == PrismioTypes.LBRACE) {
          bodyDepth++;
        } else if (tokenType == PrismioTypes.RBRACE && --bodyDepth == 0) {
          addFunctionBody(descriptors, root, document, bodyStartOffset, tokenStart);
          bodyStartOffset = -1;
        }
        lexer.advance();
        continue;
      }

      if (tokenType == PrismioTypes.KEYWORD || tokenType == PrismioTypes.CONTEXTUAL_KEYWORD) {
        if ("fn".equals(tokenText)) {
          waitingForBody = true;
          parametersStarted = false;
          parametersClosed = false;
          parenthesisDepth = 0;
        } else if (waitingForBody && parametersClosed
            && DECLARATION_BOUNDARIES.contains(tokenText)) {
          waitingForBody = false;
        }
      }

      if (waitingForBody) {
        if (tokenType == PrismioTypes.LPAREN) {
          parametersStarted = true;
          parenthesisDepth++;
        } else if (tokenType == PrismioTypes.RPAREN && parametersStarted) {
          parenthesisDepth--;
          parametersClosed = parenthesisDepth == 0;
        } else if (tokenType == PrismioTypes.LBRACE && parametersClosed) {
          bodyStartOffset = tokenEnd;
          bodyDepth = 1;
          waitingForBody = false;
        } else if (tokenType == PrismioTypes.RBRACE) {
          waitingForBody = false;
        }
      }

      lexer.advance();
    }

    return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  private static void addFunctionBody(@NotNull List<FoldingDescriptor> descriptors,
      @NotNull PsiElement root, @NotNull Document document, int contentStart, int contentEnd) {
    if (contentStart < 0 || contentEnd <= contentStart
        || document.getLineNumber(contentStart) >= document.getLineNumber(contentEnd)) {
      return;
    }
    descriptors.add(new FoldingDescriptor(root.getNode(), new TextRange(contentStart, contentEnd)));
  }

  @Override
  public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
    return " … ";
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}
