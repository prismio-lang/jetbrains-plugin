package io.prismio.ums;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The UMS lexer, following {@code ums/parser/lexer.psm}.
 *
 * <p>Small on purpose. A manifest has identifiers, three literal kinds, two
 * comment spellings and nine pieces of punctuation, and that is the whole
 * grammar — everything that looks like a keyword, {@code const} and {@code enum}
 * included, is an identifier the semantic layer interprets.
 */
public final class UmsLexer extends LexerBase {

  private CharSequence buffer = "";
  private int bufferEnd;
  private int tokenStart;
  private int tokenEnd;
  private @Nullable IElementType tokenType;

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    this.buffer = buffer;
    this.bufferEnd = endOffset;
    this.tokenStart = startOffset;
    this.tokenEnd = startOffset;
    advance();
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public @Nullable IElementType getTokenType() {
    return tokenType;
  }

  @Override
  public int getTokenStart() {
    return tokenStart;
  }

  @Override
  public int getTokenEnd() {
    return tokenEnd;
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return buffer;
  }

  @Override
  public int getBufferEnd() {
    return bufferEnd;
  }

  @Override
  public void advance() {
    tokenStart = tokenEnd;
    if (tokenStart >= bufferEnd) {
      tokenType = null;
      return;
    }
    tokenType = scan();
  }

  private IElementType scan() {
    char c = buffer.charAt(tokenStart);

    if (isWhitespace(c)) {
      tokenEnd = tokenStart;
      while (tokenEnd < bufferEnd && isWhitespace(buffer.charAt(tokenEnd))) {
        tokenEnd++;
      }
      return TokenType.WHITE_SPACE;
    }

    // `#` as well as `//`: a manifest is edited by people who reach for the shell
    // comment, and the lexer in the compiler accepts both.
    if (c == '#' || (c == '/' && tokenStart + 1 < bufferEnd && buffer.charAt(tokenStart + 1) == '/')) {
      tokenEnd = tokenStart;
      while (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) != '\n' && buffer.charAt(tokenEnd) != '\r') {
        tokenEnd++;
      }
      return UmsTypes.COMMENT;
    }

    if (c == '"') {
      return string();
    }
    // Digits only. A leading `-` does not start a number in UMS — the compiler's
    // lexer dispatches on `umsIsDigit` alone — so `-1` is a bad character
    // followed by an integer, and colouring it as one number here would hide a
    // manifest the parser rejects.
    if (isDigit(c)) {
      tokenEnd = tokenStart;
      while (tokenEnd < bufferEnd && isDigit(buffer.charAt(tokenEnd))) {
        tokenEnd++;
      }
      return UmsTypes.INTEGER;
    }
    if (isIdentifierStart(c)) {
      tokenEnd = tokenStart;
      while (tokenEnd < bufferEnd && (isIdentifierPart(buffer.charAt(tokenEnd)) || joinsEnumVariant(tokenEnd))) {
        tokenEnd++;
      }
      String text = buffer.subSequence(tokenStart, tokenEnd).toString();
      // The one pair of words the lexer itself decides, because they are values
      // rather than names: everything else stays an identifier.
      return text.equals("true") || text.equals("false") ? UmsTypes.BOOLEAN : UmsTypes.IDENTIFIER;
    }

    IElementType punctuation = punctuation(c);
    if (punctuation != null) {
      tokenEnd = tokenStart + 1;
      return punctuation;
    }

    tokenEnd = tokenStart + 1;
    return TokenType.BAD_CHARACTER;
  }

  /**
   * A string literal. Ends at a newline when unterminated, so a missing closing
   * quote colours one line rather than the rest of the manifest.
   */
  private IElementType string() {
    tokenEnd = tokenStart + 1;
    while (tokenEnd < bufferEnd) {
      char c = buffer.charAt(tokenEnd);
      if (c == '\n' || c == '\r') {
        return UmsTypes.STRING;
      }
      if (c == '\\' && tokenEnd + 1 < bufferEnd) {
        tokenEnd += 2;
        continue;
      }
      tokenEnd++;
      if (c == '"') {
        return UmsTypes.STRING;
      }
    }
    return UmsTypes.STRING;
  }

  private static @Nullable IElementType punctuation(char c) {
    return switch (c) {
      case '{' -> UmsTypes.LEFT_BRACE;
      case '}' -> UmsTypes.RIGHT_BRACE;
      case '(' -> UmsTypes.LEFT_PAREN;
      case ')' -> UmsTypes.RIGHT_PAREN;
      case '[' -> UmsTypes.LEFT_BRACKET;
      case ']' -> UmsTypes.RIGHT_BRACKET;
      case ',' -> UmsTypes.COMMA;
      case '=' -> UmsTypes.EQUAL;
      case ';' -> UmsTypes.SEMICOLON;
      default -> null;
    };
  }

  /**
   * A {@code .} followed by a letter continues an identifier, so {@code Channel.BETA} is one token, as in
   * {@code lexIdentifier}: the name of an enum variant. Anything else leaves the {@code .} as a bad character.
   */
  private boolean joinsEnumVariant(int at) {
    return buffer.charAt(at) == '.' && at + 1 < bufferEnd && isIdentifierStart(buffer.charAt(at + 1));
  }

  private static boolean isWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isIdentifierStart(char c) {
    return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static boolean isIdentifierPart(char c) {
    // Alphanumeric, `_` and `-`, matching `lexIdentifier` in
    // `ums/parser/lexer.psm`. A `-` continues an identifier but cannot start one,
    // which is what keeps `-1` an integer. Dotted names such as a target called
    // `app.cli` are string *arguments*, not identifiers, so `.` is deliberately
    // absent -- apart from the `Enum.VARIANT` join, which needs a letter after it and is
    // handled where the token is scanned.
    return isIdentifierStart(c) || isDigit(c) || c == '-';
  }
}
