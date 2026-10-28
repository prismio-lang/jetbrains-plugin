package io.prismio.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import io.prismio.highlighter.PrismioSyntaxHighlighter;
import io.prismio.psi.PrismioTypes;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Semantic annotator for Prismio
 * Provides context-aware highlighting beyond lexical analysis
 */
public final class PrismioAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    IElementType elementType = element.getNode().getElementType();

    if (elementType == PrismioTypes.IDENTIFIER) {
      // Declarations
      if (isInFunctionDeclaration(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.FUNCTION_DECLARATION);
        return;
      }
      if (isInStructDeclaration(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.STRUCT_NAME);
        return;
      }
      if (isInEnumDeclaration(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.ENUM_NAME);
        return;
      }
      if (isInTraitDeclaration(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.TRAIT_NAME);
        return;
      }

      // Usages and bindings
      if (isImportPath(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.IMPORT_PATH);
        return;
      }
      if (isMethodCall(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.METHOD_CALL);
        return;
      }
      if (isFunctionCall(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.FUNCTION_CALL);
        return;
      }
      if (isVariableDeclaration(element)) {
        TextAttributesKey key = isConstantName(element.getText())
            ? PrismioSyntaxHighlighter.CONSTANT
            : isMutableVariableDeclaration(element) ? PrismioSyntaxHighlighter.MUTABLE_VARIABLE
                                                    : PrismioSyntaxHighlighter.LOCAL_VARIABLE;
        highlightElement(element, holder, key);
        return;
      }
      if (isParameter(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.PARAMETER);
        return;
      }
      if (isFieldAccess(element) || isStructField(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.FIELD);
        return;
      }
      if (isEnumVariant(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.ENUM_VARIANT);
        return;
      }
      if (isConstantName(element.getText())) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.CONSTANT);
        return;
      }
      if (isTypeReference(element)) {
        highlightElement(element, holder, PrismioSyntaxHighlighter.TYPE_REFERENCE);
        return;
      }
    }

  }

  private void highlightElement(@NotNull PsiElement element, @NotNull AnnotationHolder holder,
      @NotNull TextAttributesKey key) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(element.getTextRange())
        .textAttributes(key)
        .create();
  }

  private boolean isInFunctionDeclaration(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent == null)
      return false;

    // Check if previous sibling is 'fn' keyword
    PsiElement prevSibling = element.getPrevSibling();
    while (prevSibling != null && prevSibling.getText().trim().isEmpty()) {
      prevSibling = prevSibling.getPrevSibling();
    }

    return isDeclarationWord(prevSibling);
  }

  /**
   * Detects method calls in builder pattern: .method()
   * Pattern: DOT IDENTIFIER LPAREN
   */
  private boolean isMethodCall(@NotNull PsiElement element) {
    if (element.getNode().getElementType() != PrismioTypes.IDENTIFIER) {
      return false;
    }

    // Check if preceded by a DOT
    PsiElement prevSibling = element.getPrevSibling();
    while (prevSibling != null && isWhitespaceElement(prevSibling)) {
      prevSibling = prevSibling.getPrevSibling();
    }
    if (prevSibling == null || prevSibling.getNode().getElementType() != PrismioTypes.DOT) {
      return false;
    }

    // Check if followed by LPAREN
    PsiElement nextSibling = element.getNextSibling();
    while (nextSibling != null && isWhitespaceElement(nextSibling)) {
      nextSibling = nextSibling.getNextSibling();
    }
    if (nextSibling == null) {
      return false;
    }
    IElementType nextType = nextSibling.getNode().getElementType();
    return nextType == PrismioTypes.LPAREN || "(".equals(nextSibling.getText());
  }

  private boolean isFunctionCall(@NotNull PsiElement element) {
    if (element.getNode().getElementType() != PrismioTypes.IDENTIFIER) {
      return false;
    }

    // First, check if this is a function declaration (identifier after 'fn')
    // If so, don't mark it as a function call
    PsiElement prevSibling = element.getPrevSibling();
    while (prevSibling != null && isWhitespaceElement(prevSibling)) {
      prevSibling = prevSibling.getPrevSibling();
    }
    if (isDeclarationWord(prevSibling)) {
      return false; // This is a function declaration, not a call
    }

    // Also check for 'extern' fn declarations
    if (prevSibling != null && prevSibling.getNode().getElementType() == PrismioTypes.KEYWORD
        && "extern".equals(prevSibling.getText())) {
      return false; // This is an extern function declaration
    }

    // Skip method calls (preceded by DOT) — those are handled by isMethodCall
    if (prevSibling != null && prevSibling.getNode().getElementType() == PrismioTypes.DOT) {
      return false;
    }

    // Check if next non-whitespace token is '('
    PsiElement nextSibling = element.getNextSibling();
    while (nextSibling != null && isWhitespaceElement(nextSibling)) {
      nextSibling = nextSibling.getNextSibling();
    }

    if (nextSibling == null) {
      return false;
    }

    // Check for opening parenthesis
    IElementType nextType = nextSibling.getNode().getElementType();
    if (nextType == PrismioTypes.LPAREN) {
      return true;
    }

    // Also check the text directly in case token type doesn't match
    String nextText = nextSibling.getText();
    if (nextText != null && nextText.startsWith("(")) {
      return true;
    }

    return false;
  }

  /**
   * Check if an element is whitespace (either empty text or WHITE_SPACE token
   * type)
   */
  private boolean isWhitespaceElement(PsiElement element) {
    if (element == null)
      return false;

    // Check token type
    IElementType type = element.getNode().getElementType();
    if (element instanceof PsiWhiteSpace || type == com.intellij.psi.TokenType.WHITE_SPACE) {
      return true;
    }

    // Also check if text is only whitespace
    String text = element.getText();
    return text != null && text.trim().isEmpty();
  }

  private boolean isParameter(@NotNull PsiElement element) {
    if (element.getNode().getElementType() != PrismioTypes.IDENTIFIER) {
      return false;
    }

    PsiElement parent = element.getParent();
    if (parent == null)
      return false;

    PsiElement nextSibling = element.getNextSibling();
    while (nextSibling != null && nextSibling.getText().trim().isEmpty()) {
      nextSibling = nextSibling.getNextSibling();
    }

    if (nextSibling == null) {
      return false;
    }

    IElementType nextType = nextSibling.getNode().getElementType();
    boolean followedByColon = nextType == PrismioTypes.COLON;
    return followedByColon && isInsideFunctionParameters(element);
  }

  private boolean isInStructDeclaration(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent == null)
      return false;

    PsiElement prevSibling = element.getPrevSibling();
    while (prevSibling != null && prevSibling.getText().trim().isEmpty()) {
      prevSibling = prevSibling.getPrevSibling();
    }

    return prevSibling != null && prevSibling.getNode().getElementType() == PrismioTypes.KEYWORD
        && "struct".equals(prevSibling.getText());
  }

  private boolean isInEnumDeclaration(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent == null)
      return false;

    PsiElement prevSibling = element.getPrevSibling();
    while (prevSibling != null && prevSibling.getText().trim().isEmpty()) {
      prevSibling = prevSibling.getPrevSibling();
    }

    // A `codeset` names its entries as an enum does its variants, and is coloured the same way.
    return isKeyword(prevSibling, "enum") || isKeyword(prevSibling, "codeset");
  }

  private boolean isInTraitDeclaration(@NotNull PsiElement element) {
    PsiElement previous = previousSignificantSibling(element);
    return previous != null && previous.getNode().getElementType() == PrismioTypes.KEYWORD
        && "trait".equals(previous.getText());
  }

  private boolean isVariableDeclaration(@NotNull PsiElement element) {
    PsiElement previous = previousSignificantSibling(element);
    if (isKeyword(previous, "let")) {
      return true;
    }
    return isKeyword(previous, "mut") && isKeyword(previousSignificantSibling(previous), "let");
  }

  private boolean isMutableVariableDeclaration(@NotNull PsiElement element) {
    return isKeyword(previousSignificantSibling(element), "mut");
  }

  private boolean isFieldAccess(@NotNull PsiElement element) {
    PsiElement previous = previousSignificantSibling(element);
    return previous != null && previous.getNode().getElementType() == PrismioTypes.DOT;
  }

  private boolean isStructField(@NotNull PsiElement element) {
    PsiElement next = nextSignificantSibling(element);
    return next != null && next.getNode().getElementType() == PrismioTypes.COLON
        && isInsideDeclarationBody(element, "struct");
  }

  private boolean isEnumVariant(@NotNull PsiElement element) {
    return isInsideDeclarationBody(element, "enum") || isInsideDeclarationBody(element, "codeset");
  }

  private boolean isTypeReference(@NotNull PsiElement element) {
    PsiElement previous = previousSignificantSibling(element);
    if (previous != null) {
      IElementType previousType = previous.getNode().getElementType();
      if (previousType == PrismioTypes.COLON || previousType == PrismioTypes.ARROW) {
        return true;
      }
    }

    String text = element.getText();
    return text != null && !text.isEmpty() && Character.isUpperCase(text.codePointAt(0));
  }

  private boolean isImportPath(@NotNull PsiElement element) {
    for (PsiElement current = element.getPrevSibling(); current != null;
        current = current.getPrevSibling()) {
      if (current instanceof PsiWhiteSpace && current.getText().contains("\n")) {
        return false;
      }
      if (isKeyword(current, "import")) {
        return true;
      }
      IElementType type = current.getNode().getElementType();
      if (type == PrismioTypes.LBRACE
          || type == PrismioTypes.RBRACE) {
        return false;
      }
    }
    return false;
  }

  private boolean isInsideFunctionParameters(@NotNull PsiElement element) {
    int nestedParentheses = 0;
    for (PsiElement current = element.getPrevSibling(); current != null;
        current = current.getPrevSibling()) {
      IElementType type = current.getNode().getElementType();
      if (type == PrismioTypes.RPAREN) {
        nestedParentheses++;
      } else if (type == PrismioTypes.LPAREN) {
        if (nestedParentheses > 0) {
          nestedParentheses--;
        } else {
          PsiElement functionName = previousSignificantSibling(current);
          return functionName != null
              && functionName.getNode().getElementType() == PrismioTypes.IDENTIFIER
              && isDeclarationWord(previousSignificantSibling(functionName));
        }
      } else if (nestedParentheses == 0
          && (type == PrismioTypes.LBRACE || type == PrismioTypes.RBRACE)) {
        return false;
      }
    }
    return false;
  }

  private boolean isInsideDeclarationBody(
      @NotNull PsiElement element, @NotNull String declarationKeyword) {
    int nestedBraces = 0;
    for (PsiElement current = element.getPrevSibling(); current != null;
        current = current.getPrevSibling()) {
      IElementType type = current.getNode().getElementType();
      if (type == PrismioTypes.RBRACE) {
        nestedBraces++;
      } else if (type == PrismioTypes.LBRACE) {
        if (nestedBraces > 0) {
          nestedBraces--;
        } else {
          PsiElement declarationName = previousSignificantSibling(current);
          return declarationName != null
              && declarationName.getNode().getElementType() == PrismioTypes.IDENTIFIER
              && isKeyword(previousSignificantSibling(declarationName), declarationKeyword);
        }
      }
    }
    return false;
  }

  private PsiElement previousSignificantSibling(@NotNull PsiElement element) {
    PsiElement current = element.getPrevSibling();
    while (current != null && isWhitespaceElement(current)) {
      current = current.getPrevSibling();
    }
    return current;
  }

  private PsiElement nextSignificantSibling(@NotNull PsiElement element) {
    PsiElement current = element.getNextSibling();
    while (current != null && isWhitespaceElement(current)) {
      current = current.getNextSibling();
    }
    return current;
  }

  /** A keyword by its spelling: a word such as `codeset` is a keyword only in a declaration, so it lexes as contextual. */
  private boolean isKeyword(PsiElement element, @NotNull String keyword) {
    if (element == null) {
      return false;
    }
    IElementType type = element.getNode().getElementType();
    return (type == PrismioTypes.KEYWORD || type == PrismioTypes.CONTEXTUAL_KEYWORD)
        && keyword.equals(element.getText());
  }

  private boolean isConstantName(String text) {
    if (text == null || text.isEmpty() || !text.equals(text.toUpperCase(Locale.ROOT))) {
      return false;
    }
    return text.codePoints().anyMatch(Character::isLetter);
  }

  /**
   * {@code fn}, or the contextual {@code prop} that declares a property the same
   * way ({@code src/parse/decl.psm}): the word before a function's name.
   */
  private static boolean isDeclarationWord(PsiElement element) {
    if (element == null) {
      return false;
    }
    IElementType type = element.getNode().getElementType();
    return (type == PrismioTypes.KEYWORD && "fn".equals(element.getText()))
        || (type == PrismioTypes.CONTEXTUAL_KEYWORD && "prop".equals(element.getText()));
  }
}
