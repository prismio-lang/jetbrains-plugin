package io.prismio.navigation;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.prismio.lexer.PrismioLexer;
import io.prismio.psi.PrismioFile;
import io.prismio.psi.PrismioTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves symbols to their lexical declarations:
 * - Function parameters
 * - Local variables (`let [mut] ...` and `for ... in`)
 * - Struct fields
 * - Enum variants
 */
public final class PrismioScopeResolver {

  private PrismioScopeResolver() {}

  /**
   * Checks if an identifier is at a declaration site (so it does not resolve to itself).
   */
  public static boolean isDeclarationSite(@NotNull PsiElement element) {
    if (element.getNode() == null || element.getNode().getElementType() != PrismioTypes.IDENTIFIER) {
      return false;
    }

    // 1. Top-level declarations
    if (DeclarationScanner.findDeclaration(element) != null) {
      return true;
    }

    // 2. Variable declarations: `let name` or `let mut name`
    PsiElement prev = previousSignificant(element);
    if (isKeyword(prev, "let") || isConstWord(prev)) {
      return true;
    }
    if (isKeyword(prev, "mut") && isKeyword(previousSignificant(prev), "let")) {
      return true;
    }

    // 3. Function parameters: inside `fn ... (...)`, followed by `:`
    PsiElement next = nextSignificant(element);
    if (isToken(next, PrismioTypes.COLON)) {
      if (isInsideFunctionParams(element)) {
        return true;
      }
      if (isInsideStructBody(element)) {
        return true;
      }
    }

    // 4. Enum variants: inside `enum Name { ... }`
    if (isInsideEnumBody(element)) {
      return true;
    }

    return false;
  }

  /**
   * Resolves an identifier to its declaration in lexical scope:
   * 1. Struct fields or enum variants if preceded by a dot
   * 2. Local variables in enclosing blocks
   * 3. Function parameters
   */
  public static @Nullable PsiElement resolveLexical(@NotNull PsiElement source) {
    if (source.getNode() == null || source.getNode().getElementType() != PrismioTypes.IDENTIFIER) {
      return null;
    }
    PsiFile file = source.getContainingFile();
    if (!(file instanceof PrismioFile)) {
      return null;
    }

    String name = source.getText();
    if (name == null || name.isEmpty()) {
      return null;
    }

    int offset = source.getTextOffset();

    // Case 1: Member access (`point.x` or `Direction.North`)
    PsiElement prev = previousSignificant(source);
    if (isToken(prev, PrismioTypes.DOT)) {
      return resolveMember(file, name);
    }

    // Case 2: Local variables and parameters within enclosing function
    ScopeInfo scopeInfo = scanFileScopes(file, offset);
    for (LocalDecl decl : scopeInfo.localVariables) {
      if (name.equals(decl.name) && decl.scopeRange.containsOffset(offset) && decl.defOffset < offset) {
        return decl.element;
      }
    }

    for (LocalDecl param : scopeInfo.parameters) {
      if (name.equals(param.name) && param.scopeRange.containsOffset(offset)) {
        return param.element;
      }
    }

    return null;
  }

  /**
   * Collects all declarations visible at the given element (locals, params, and top-level symbols).
   */
  public static @NotNull List<Declaration> collectVisibleDeclarations(@NotNull PsiElement context) {
    PsiFile file = context.getContainingFile();
    if (!(file instanceof PrismioFile)) {
      return Collections.emptyList();
    }

    int offset = context.getTextOffset();
    List<Declaration> visible = new ArrayList<>();

    // 1. Locals and parameters
    ScopeInfo scopeInfo = scanFileScopes(file, offset);
    for (LocalDecl decl : scopeInfo.localVariables) {
      if (decl.scopeRange.containsOffset(offset) && decl.defOffset < offset) {
        visible.add(new Declaration(decl.element, decl.name, DeclarationKind.VARIABLE));
      }
    }
    for (LocalDecl param : scopeInfo.parameters) {
      if (param.scopeRange.containsOffset(offset)) {
        visible.add(new Declaration(param.element, param.name, DeclarationKind.PARAMETER));
      }
    }

    // 2. File-level declarations
    visible.addAll(DeclarationScanner.collectCached(file));

    return visible;
  }

  private static @Nullable PsiElement resolveMember(PsiFile file, String name) {
    CharSequence text = file.getViewProvider().getContents();
    Lexer lexer = new PrismioLexer();
    lexer.start(text);

    boolean inStruct = false;
    boolean inEnum = false;
    int braceDepth = 0;

    while (lexer.getTokenType() != null) {
      IElementType tokenType = lexer.getTokenType();
      String tokenText = text.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();

      if (tokenType == PrismioTypes.KEYWORD) {
        if ("struct".equals(tokenText) && braceDepth == 0) {
          inStruct = true;
        } else if ("enum".equals(tokenText) && braceDepth == 0) {
          inEnum = true;
        }
      } else if (tokenType == PrismioTypes.LBRACE) {
        braceDepth++;
      } else if (tokenType == PrismioTypes.RBRACE) {
        if (braceDepth > 0) braceDepth--;
        if (braceDepth == 0) {
          inStruct = false;
          inEnum = false;
        }
      } else if (tokenType == PrismioTypes.IDENTIFIER && (inStruct || inEnum) && braceDepth == 1) {
        if (name.equals(tokenText)) {
          PsiElement element = file.findElementAt(lexer.getTokenStart());
          if (element != null) {
            return element;
          }
        }
      }

      lexer.advance();
    }
    return null;
  }

  private record LocalDecl(String name, PsiElement element, int defOffset, TextRange scopeRange) {}

  private static class ScopeInfo {
    final List<LocalDecl> localVariables = new ArrayList<>();
    final List<LocalDecl> parameters = new ArrayList<>();
  }

  private static ScopeInfo scanFileScopes(PsiFile file, int targetOffset) {
    ScopeInfo info = new ScopeInfo();
    CharSequence text = file.getViewProvider().getContents();
    Lexer lexer = new PrismioLexer();
    lexer.start(text);

    int braceDepth = 0;
    boolean inFn = false;
    int fnStartOffset = -1;
    boolean inFnParams = false;
    int fnParamParenDepth = 0;
    List<LocalDecl> pendingParams = new ArrayList<>();

    // Stack of block start offsets
    List<Integer> blockStarts = new ArrayList<>();
    // Track pending let declaration
    boolean pendingLet = false;

    while (lexer.getTokenType() != null) {
      IElementType tokenType = lexer.getTokenType();
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();
      String tokenText = text.subSequence(start, end).toString();

      if (tokenType == PrismioTypes.KEYWORD) {
        if ("fn".equals(tokenText)) {
          inFn = true;
          fnStartOffset = start;
          pendingParams.clear();
          pendingLet = false;
        } else if ("let".equals(tokenText)) {
          pendingLet = true;
        }
      } else if (tokenType == PrismioTypes.CONTEXTUAL_KEYWORD && "const".equals(tokenText)) {
        // `const name = value` declares a local as `let name` does.
        pendingLet = true;
      } else if (tokenType == PrismioTypes.LPAREN) {
        if (inFn && !inFnParams && braceDepth == 0) {
          inFnParams = true;
          fnParamParenDepth = 1;
        } else if (inFnParams) {
          fnParamParenDepth++;
        }
      } else if (tokenType == PrismioTypes.RPAREN) {
        if (inFnParams) {
          fnParamParenDepth--;
          if (fnParamParenDepth == 0) {
            inFnParams = false;
          }
        }
      } else if (tokenType == PrismioTypes.LBRACE) {
        braceDepth++;
        blockStarts.add(start);
      } else if (tokenType == PrismioTypes.RBRACE) {
        if (!blockStarts.isEmpty()) {
          int blockStart = blockStarts.remove(blockStarts.size() - 1);
          TextRange blockRange = new TextRange(blockStart, end);

          // If this closes the function body
          if (inFn && blockStarts.isEmpty()) {
            for (LocalDecl param : pendingParams) {
              info.parameters.add(new LocalDecl(param.name, param.element, param.defOffset, blockRange));
            }
            pendingParams.clear();
            inFn = false;
          }

          // Assign block scope to locals declared in this block
          for (int i = 0; i < info.localVariables.size(); i++) {
            LocalDecl ld = info.localVariables.get(i);
            if (ld.scopeRange == null && ld.defOffset >= blockStart && ld.defOffset <= end) {
              info.localVariables.set(i, new LocalDecl(ld.name, ld.element, ld.defOffset, blockRange));
            }
          }
        }
        if (braceDepth > 0) braceDepth--;
      } else if (tokenType == PrismioTypes.IDENTIFIER) {
        if (inFnParams && fnParamParenDepth == 1) {
          // Look ahead to check if followed by COLON (parameter)
          int nextOffset = end;
          while (nextOffset < text.length() && Character.isWhitespace(text.charAt(nextOffset))) {
            nextOffset++;
          }
          if (nextOffset < text.length() && text.charAt(nextOffset) == ':') {
            PsiElement elem = file.findElementAt(start);
            if (elem != null) {
              pendingParams.add(new LocalDecl(tokenText, elem, start, null));
            }
          }
        } else if (pendingLet && braceDepth > 0) {
          PsiElement elem = file.findElementAt(start);
          if (elem != null) {
            info.localVariables.add(new LocalDecl(tokenText, elem, end, null));
          }
          pendingLet = false;
        }
      } else if (tokenType != TokenType.WHITE_SPACE && tokenType != PrismioTypes.LINE_COMMENT
          && tokenType != PrismioTypes.BLOCK_COMMENT && tokenType != PrismioTypes.DOC_COMMENT) {
        if (!"mut".equals(tokenText)) {
          pendingLet = false;
        }
      }

      lexer.advance();
    }

    // Fix up any remaining locals without closed block
    int textLength = text.length();
    for (int i = 0; i < info.localVariables.size(); i++) {
      LocalDecl ld = info.localVariables.get(i);
      if (ld.scopeRange == null) {
        info.localVariables.set(i, new LocalDecl(ld.name, ld.element, ld.defOffset, new TextRange(ld.defOffset, textLength)));
      }
    }
    for (LocalDecl param : pendingParams) {
      info.parameters.add(new LocalDecl(param.name, param.element, param.defOffset, new TextRange(fnStartOffset >= 0 ? fnStartOffset : 0, textLength)));
    }

    return info;
  }

  private static @Nullable PsiElement previousSignificant(@NotNull PsiElement element) {
    PsiElement prev = element.getPrevSibling();
    while (prev != null && isIgnorable(prev)) {
      prev = prev.getPrevSibling();
    }
    return prev;
  }

  private static @Nullable PsiElement nextSignificant(@NotNull PsiElement element) {
    PsiElement next = element.getNextSibling();
    while (next != null && isIgnorable(next)) {
      next = next.getNextSibling();
    }
    return next;
  }

  private static boolean isIgnorable(@NotNull PsiElement element) {
    if (element.getNode() == null) return true;
    IElementType type = element.getNode().getElementType();
    return type == TokenType.WHITE_SPACE
        || type == PrismioTypes.LINE_COMMENT
        || type == PrismioTypes.BLOCK_COMMENT
        || type == PrismioTypes.DOC_COMMENT;
  }

  /** {@code const} before a name declares one, as {@code let} does (src/parse/decl.psm). */
  private static boolean isConstWord(@Nullable PsiElement element) {
    return element != null && element.getNode() != null
        && element.getNode().getElementType() == PrismioTypes.CONTEXTUAL_KEYWORD
        && "const".equals(element.getText());
  }

  private static boolean isKeyword(@Nullable PsiElement element, @NotNull String keyword) {
    return element != null && element.getNode() != null
        && element.getNode().getElementType() == PrismioTypes.KEYWORD
        && keyword.equals(element.getText());
  }

  private static boolean isToken(@Nullable PsiElement element, @NotNull IElementType expected) {
    return element != null && element.getNode() != null
        && element.getNode().getElementType() == expected;
  }

  private static boolean isInsideFunctionParams(@NotNull PsiElement element) {
    int parens = 0;
    for (PsiElement curr = element.getPrevSibling(); curr != null; curr = curr.getPrevSibling()) {
      if (curr.getNode() == null) continue;
      IElementType type = curr.getNode().getElementType();
      if (type == PrismioTypes.RPAREN) parens++;
      else if (type == PrismioTypes.LPAREN) {
        if (parens > 0) {
          parens--;
        } else {
          PsiElement fnName = previousSignificant(curr);
          return fnName != null && fnName.getNode() != null
              && fnName.getNode().getElementType() == PrismioTypes.IDENTIFIER
              && isKeyword(previousSignificant(fnName), "fn");
        }
      } else if (parens == 0 && (type == PrismioTypes.LBRACE || type == PrismioTypes.RBRACE)) {
        return false;
      }
    }
    return false;
  }

  private static boolean isInsideStructBody(@NotNull PsiElement element) {
    return isInsideDeclarationBlock(element, "struct");
  }

  private static boolean isInsideEnumBody(@NotNull PsiElement element) {
    return isInsideDeclarationBlock(element, "enum");
  }

  private static boolean isInsideDeclarationBlock(@NotNull PsiElement element, @NotNull String keyword) {
    int braces = 0;
    for (PsiElement curr = element.getPrevSibling(); curr != null; curr = curr.getPrevSibling()) {
      if (curr.getNode() == null) continue;
      IElementType type = curr.getNode().getElementType();
      if (type == PrismioTypes.RBRACE) braces++;
      else if (type == PrismioTypes.LBRACE) {
        if (braces > 0) {
          braces--;
        } else {
          PsiElement name = previousSignificant(curr);
          return name != null && isKeyword(previousSignificant(name), keyword);
        }
      }
    }
    return false;
  }
}
