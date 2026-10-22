package io.prismio.navigation;

import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import io.prismio.lexer.PrismioLexer;
import io.prismio.psi.PrismioTypes;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeclarationScanner {
  private DeclarationScanner() {}

  private static final Key<CachedValue<List<Declaration>>> DECLARATIONS =
      Key.create("prismio.declarations");

  /**
   * {@link #collect} re-lexes the whole file, which is fine once and quadratic
   * when every identifier in a file asks for it — which is exactly what
   * reference resolution does on each highlighting pass. Cached against the
   * file's modification count, so an edit invalidates it and nothing else does.
   */
  public static @NotNull List<Declaration> collectCached(@NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, DECLARATIONS,
        () -> CachedValueProvider.Result.create(collect(file), file));
  }

  public static @NotNull List<Declaration> collect(@NotNull PsiFile file) {
    List<Declaration> declarations = new ArrayList<>();
    CharSequence text = file.getViewProvider().getContents();
    Lexer lexer = new PrismioLexer();
    lexer.start(text);

    int braceDepth = 0;
    DeclarationKind pendingKind = null;
    while (lexer.getTokenType() != null) {
      IElementType tokenType = lexer.getTokenType();
      String tokenText = text.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();

      if (tokenType == PrismioTypes.KEYWORD) {
        DeclarationKind declarationKind = DeclarationKind.fromKeyword(tokenText, braceDepth);
        if (declarationKind != null) {
          pendingKind = declarationKind;
        } else if (!"mut".equals(tokenText)) {
          pendingKind = null;
        }
      } else if (tokenType == PrismioTypes.CONTEXTUAL_KEYWORD && "const".equals(tokenText)) {
        // `const CELLS = area(8)`: a constant, as `let` makes one. `const fn` is overridden by `fn`.
        pendingKind = braceDepth == 0 ? DeclarationKind.CONSTANT : null;
      } else if (tokenType == PrismioTypes.IDENTIFIER && pendingKind != null) {
        PsiElement element = file.findElementAt(lexer.getTokenStart());
        if (element != null) {
          declarations.add(new Declaration(element, tokenText, pendingKind));
        }
        pendingKind = null;
      } else if (tokenType != TokenType.WHITE_SPACE && tokenType != PrismioTypes.LINE_COMMENT
          && tokenType != PrismioTypes.BLOCK_COMMENT && tokenType != PrismioTypes.DOC_COMMENT) {
        pendingKind = null;
      }

      if (tokenType == PrismioTypes.LBRACE) {
        braceDepth++;
      } else if (tokenType == PrismioTypes.RBRACE && braceDepth > 0) {
        braceDepth--;
      }
      lexer.advance();
    }
    return declarations;
  }

  public static @Nullable Declaration findDeclaration(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return null;
    }
    int offset = element.getTextOffset();
    return collectCached(file)
        .stream()
        .filter(declaration -> {
          PsiElement declarationElement = declaration.getElement();
          return declarationElement != null && declarationElement.getTextOffset() == offset;
        })
        .findFirst()
        .orElse(null);
  }
}
