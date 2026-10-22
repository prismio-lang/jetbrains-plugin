package io.prismio.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.prismio.lang.PrismioWords;
import io.prismio.psi.PrismioTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Prismio lexer, written by hand to follow {@code src/lexer/scanner.psm}.
 *
 * <p>The decision order below is that file's {@code lexerNextToken}, including
 * the one part of it that is easy to get wrong: {@code '.'} is a separator but
 * {@code ".."} is a range, so the range check sits between the operator check
 * and the separator check. Scanning {@code 0..n} the other way produces the
 * float {@code 0.} followed by {@code .n}.
 *
 * <p>An unterminated string or character literal ends at the newline rather than
 * running to the end of the file. The compiler reports it as a fatal error and
 * stops; an editor has to keep colouring the rest of the file, and a literal
 * that swallows everything after it makes a missing quote look like a fault
 * hundreds of lines further down.
 */
public final class PrismioLexer extends LexerBase {

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
    // Every token is decided from its own first characters, so there is no state
    // to carry between them. Comments are single-line and strings cannot span a
    // newline, which is what makes that true.
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

    if (c == '/' && peekIs(1, '/')) {
      return lineComment();
    }
    if (c == '/' && peekIs(1, '*')) {
      return blockComment();
    }
    if (identifierStartsAt(tokenStart)) {
      return word();
    }
    if (isDigit(c)) {
      return number();
    }
    if (c == '"') {
      if (peekIs(1, '"') && peekIs(2, '"')) {
        return tripleQuoted();
      }
      return quoted('"', PrismioTypes.STRING_LITERAL);
    }
    if (c == '\'') {
      return quoted('\'', PrismioTypes.CHARACTER_LITERAL);
    }
    if (isOperatorChar(c)) {
      return operator();
    }
    // Between the operator and separator checks: see the class comment. `..<` is
    // one token, as the compiler scans it; read as `..` and a `<`, the `<` is a
    // comparison to everything downstream, and Reformat Code spaced `0..< n`.
    if (c == '.' && peekIs(1, '.')) {
      tokenEnd = peekIs(2, '<') ? tokenStart + 3 : tokenStart + 2;
      return PrismioTypes.RANGE;
    }
    // `??`, none-coalescing: one token, as the compiler scans it, so the formatter
    // spaces it as the binary operator it is and a lone `?` stays the optional suffix.
    if (c == '?' && peekIs(1, '?')) {
      tokenEnd = tokenStart + 2;
      return PrismioTypes.ARITHMETIC_OP;
    }
    IElementType separator = separator(c);
    if (separator != null) {
      tokenEnd = tokenStart + 1;
      return separator;
    }

    tokenEnd = tokenStart + 1;
    return TokenType.BAD_CHARACTER;
  }

  private IElementType lineComment() {
    // `///` is a documentation convention, not compiler syntax: the scanner sees
    // an ordinary `//` line. Told apart here so rendered docs and spellchecking
    // can treat it as prose. `////` and longer are separators in comment art,
    // and are deliberately not documentation.
    boolean documentation = peekIs(2, '/') && !peekIs(3, '/');
    tokenEnd = tokenStart + 2;
    while (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) != '\n' && buffer.charAt(tokenEnd) != '\r') {
      tokenEnd++;
    }
    return documentation ? PrismioTypes.DOC_COMMENT : PrismioTypes.LINE_COMMENT;
  }

  /**
   * A block comment, counting depth exactly as {@code lexerSkipBlockComment} in
   * the compiler does.
   *
   * <p>Depth counts delimiters and nothing else. A {@code //} inside the comment
   * is not a line comment, so it neither hides a close nor protects a stray
   * open — matching the compiler is the point, because an editor that disagreed
   * would colour a region the compiler is reading as code.
   *
   * <p>An unterminated comment runs to the end of the file. The compiler makes
   * that a fatal error; here it has to produce a token, and colouring the
   * remainder as comment is what shows the reader where the unclosed delimiter
   * swallowed their program.
   */
  private IElementType blockComment() {
    // `/**` opens a documentation comment, the same convention `///` follows and
    // the one PrismioInlineDocumentationProvider renders. `/**/` and `/***/` are
    // excluded: both are empty comments that merely happen to start with those
    // three characters, and there is nothing in either to render.
    // `/**` is positions 0..2, so what decides it is position 3: the first
    // character of the comment's body.
    boolean documentation = peekIs(2, '*') && !peekIs(3, '/') && !peekIs(3, '*');

    tokenEnd = tokenStart + 2;
    int depth = 1;
    while (tokenEnd < bufferEnd && depth > 0) {
      char c = buffer.charAt(tokenEnd);
      char next = tokenEnd + 1 < bufferEnd ? buffer.charAt(tokenEnd + 1) : '\0';
      if (c == '/' && next == '*') {
        depth++;
        tokenEnd += 2;
      } else if (c == '*' && next == '/') {
        depth--;
        tokenEnd += 2;
      } else {
        tokenEnd++;
      }
    }
    return documentation ? PrismioTypes.DOC_COMMENT : PrismioTypes.BLOCK_COMMENT;
  }

  private IElementType word() {
    tokenEnd = tokenStart;
    while (tokenEnd < bufferEnd) {
      char c = buffer.charAt(tokenEnd);
      if (c < 128) {
        if (!isIdentifierPart(c)) {
          break;
        }
        tokenEnd++;
        continue;
      }
      int codePoint = Character.codePointAt(buffer, tokenEnd);
      if (!isUnicodeIdentifierContinue(codePoint)) {
        break;
      }
      tokenEnd += Character.charCount(codePoint);
    }
    String text = buffer.subSequence(tokenStart, tokenEnd).toString();

    if (text.equals("true") || text.equals("false")) {
      return PrismioTypes.BOOLEAN;
    }
    if (PrismioWords.KEYWORDS.contains(text) && !isDefaultAsName(text)) {
      return PrismioTypes.KEYWORD;
    }
    if (PrismioWords.BUILTIN_TYPES.contains(text)) {
      return PrismioTypes.BUILTIN_TYPE;
    }
    if (PrismioWords.STDLIB_TYPES.contains(text)) {
      return PrismioTypes.STDLIB_TYPE;
    }
    if (PrismioWords.CONTEXTUAL_KEYWORDS.contains(text) && !isValueNameHere(text)) {
      return PrismioTypes.CONTEXTUAL_KEYWORD;
    }
    return PrismioTypes.IDENTIFIER;
  }

  /**
   * The async words are keywords only where the compiler reads them as one, because each is
   * also a name the compiler's own sources use (a `scope` variable, the std `join` function):
   * {@code async} directly before {@code fn}, {@code scope} before {@code {}, and
   * {@code await} and {@code join} before a name (src/parse/expr.psm, stmt.psm, decl.psm). Anywhere
   * else they stay identifiers, so Find Usages and Go to Declaration still see them.
   */
  private boolean isValueNameHere(String text) {
    if (text.equals("volatile")) {
      return !isPointerQualifier();
    }
    boolean async = text.equals("async");
    boolean scope = text.equals("scope");
    // Words that are markers only when another word follows: `hot fn`, `const CELLS`. A
    // variable called `hot` or `const` is followed by an operator or a bracket.
    boolean awaitOrJoin = text.equals("await") || text.equals("join") || text.equals("hot")
        || text.equals("const") || text.equals("noinline") || text.equals("noreturn");
    if (!async && !scope && !awaitOrJoin) {
      return false;
    }
    int at = tokenEnd;
    while (at < bufferEnd && (buffer.charAt(at) == ' ' || buffer.charAt(at) == '\t')) {
      at++;
    }
    if (at >= bufferEnd) {
      return true;
    }
    char next = buffer.charAt(at);
    if (scope) {
      return next != '{';
    }
    if (at == tokenEnd) {
      return true;
    }
    if (async) {
      return !(buffer.length() >= at + 2 && buffer.charAt(at) == 'f' && buffer.charAt(at + 1) == 'n'
          && (at + 2 == bufferEnd || !isIdentifierPart(buffer.charAt(at + 2))));
    }
    return !(Character.isLetter(next) || next == '_');
  }

  /**
   * {@code volatile} is a pointer capability only inside a {@code Ptr<...>} argument list, after
   * any {@code mut} or {@code volatile} before it, and only when a type follows
   * ({@code parsePtrQualifiers}, src/parse/decl.psm). {@code Ptr<volatile>} and a variable named
   * {@code volatile} stay names.
   */
  private boolean isPointerQualifier() {
    int after = tokenEnd;
    while (after < bufferEnd && isWhitespace(buffer.charAt(after))) {
      after++;
    }
    if (after >= bufferEnd || !(buffer.charAt(after) == '[' || identifierStartsAt(after))) {
      return false;
    }
    int at = tokenStart;
    while (true) {
      at = skipWhitespaceBackward(at);
      if (at == 0) {
        return false;
      }
      if (buffer.charAt(at - 1) == '<') {
        at = skipWhitespaceBackward(at - 1);
        return wordEndingAt(at).equals("Ptr");
      }
      String word = wordEndingAt(at);
      if (!word.equals("mut") && !word.equals("volatile")) {
        return false;
      }
      at -= word.length();
    }
  }

  private int skipWhitespaceBackward(int at) {
    while (at > 0 && isWhitespace(buffer.charAt(at - 1))) {
      at--;
    }
    return at;
  }

  /** The ASCII identifier that ends at {@code end}, or the empty string. */
  private String wordEndingAt(int end) {
    int start = end;
    while (start > 0 && isIdentifierPart(buffer.charAt(start - 1)) && buffer.charAt(start - 1) < 128) {
      start--;
    }
    return buffer.subSequence(start, end).toString();
  }

  private IElementType number() {
    tokenEnd = tokenStart;

    if (buffer.charAt(tokenStart) == '0') {
      int radix = radixAfterZero();
      if (radix != 10) {
        tokenEnd = digitRun(tokenStart + 2, radix);
        return PrismioTypes.INTEGER;
      }
    }

    tokenEnd = digitRun(tokenStart, 10);

    boolean isFloat = false;
    // A '.' continues the number only when a digit follows, which is what keeps
    // `0..n` a range rather than a float.
    if (tokenEnd + 1 < bufferEnd && buffer.charAt(tokenEnd) == '.' && isDigit(buffer.charAt(tokenEnd + 1))) {
      isFloat = true;
      tokenEnd = digitRun(tokenEnd + 1, 10);
    }

    // `6.022e23`, and `1e9` without a dot. Scientific notation is consumed
    // only when a digit actually follows the `e` and its optional sign,
    // so an identifier is never eaten: `1e` is the number 1 followed by `e`.
    if (tokenEnd < bufferEnd && (buffer.charAt(tokenEnd) == 'e' || buffer.charAt(tokenEnd) == 'E')) {
      boolean signed = tokenEnd + 1 < bufferEnd && (buffer.charAt(tokenEnd + 1) == '+' || buffer.charAt(tokenEnd + 1) == '-');
      int digitAt = signed ? tokenEnd + 2 : tokenEnd + 1;
      if (digitAt < bufferEnd && isDigit(buffer.charAt(digitAt))) {
        isFloat = true;
        tokenEnd = digitRun(digitAt, 10);
      }
    }

    return isFloat ? PrismioTypes.FLOAT : PrismioTypes.INTEGER;
  }

  // `0xFF`, `0o755`, `0b1010`: the radix the `0` at tokenStart introduces, or 10
  // when no digit of that radix follows the marker (`0x` alone is `0` then `x`).
  private int radixAfterZero() {
    if (tokenStart + 2 >= bufferEnd) {
      return 10;
    }
    int radix = switch (buffer.charAt(tokenStart + 1)) {
      case 'x', 'X' -> 16;
      case 'o', 'O' -> 8;
      case 'b', 'B' -> 2;
      default -> 10;
    };
    if (radix == 10 || !isRadixDigit(buffer.charAt(tokenStart + 2), radix)) {
      return 10;
    }
    return radix;
  }

  // One run of digits from `from`, where `_` between two digits is a separator
  // (`1_000_000`, `0xFF_FF`) -- src/lexer/scanner.psm lexDigitRun. The compiler
  // refuses a separator with no digit after it; here it simply ends the run, so
  // the editor keeps lexing and the compiler's diagnostic says why.
  private int digitRun(int from, int radix) {
    int end = from;
    while (end < bufferEnd && isRadixDigit(buffer.charAt(end), radix)) {
      end++;
      int ahead = end;
      while (ahead < bufferEnd && buffer.charAt(ahead) == '_') {
        ahead++;
      }
      if (ahead > end && ahead < bufferEnd && isRadixDigit(buffer.charAt(ahead), radix)) {
        end = ahead;
      }
    }
    return end;
  }

  private static boolean isRadixDigit(char c, int radix) {
    return switch (radix) {
      case 16 -> isHexDigit(c);
      case 8 -> isOctalDigit(c);
      case 2 -> isBinaryDigit(c);
      default -> isDigit(c);
    };
  }

  private IElementType quoted(char delimiter, IElementType type) {
    tokenEnd = tokenStart + 1;
    while (tokenEnd < bufferEnd) {
      char c = buffer.charAt(tokenEnd);
      if (c == '\n' || c == '\r') {
        // Unterminated. Stop at the line end so the damage stays on one line.
        return type;
      }
      if (c == '\\' && tokenEnd + 1 < bufferEnd) {
        tokenEnd += 2;
        continue;
      }
      tokenEnd++;
      if (c == delimiter) {
        return type;
      }
    }
    return type;
  }

  private IElementType tripleQuoted() {
    tokenEnd = tokenStart + 3;
    while (tokenEnd < bufferEnd) {
      char c = buffer.charAt(tokenEnd);
      if (c == '"' && tokenEnd + 2 < bufferEnd
          && buffer.charAt(tokenEnd + 1) == '"' && buffer.charAt(tokenEnd + 2) == '"') {
        tokenEnd += 3;
        return PrismioTypes.STRING_LITERAL;
      }
      tokenEnd++;
    }
    return PrismioTypes.STRING_LITERAL;
  }

  private IElementType operator() {
    char first = buffer.charAt(tokenStart);
    char second = tokenStart + 1 < bufferEnd ? buffer.charAt(tokenStart + 1) : '\0';

    if (first == '-' && second == '>') {
      tokenEnd = tokenStart + 2;
      return PrismioTypes.ARROW;
    }
    if (first == '=' && second == '>') {
      tokenEnd = tokenStart + 2;
      return PrismioTypes.FAT_ARROW;
    }
    if ((first == '=' || first == '!' || first == '<' || first == '>') && second == '=') {
      tokenEnd = tokenStart + 2;
      return PrismioTypes.RELATIONAL_OP;
    }
    if ((first == '&' && second == '&') || (first == '|' && second == '|')) {
      tokenEnd = tokenStart + 2;
      return PrismioTypes.LOGICAL_OP;
    }
    // `>>` also closes two nested generic arguments; the compiler's parser splits
    // it back apart when it expects a single '>'. One token here either way.
    if ((first == '<' && second == '<') || (first == '>' && second == '>')) {
      tokenEnd = tokenStart + 2;
      return PrismioTypes.SHIFT_OP;
    }
    if (second == '=' && isCompoundAssignable(first)) {
      tokenEnd = tokenStart + 2;
      return PrismioTypes.ASSIGNMENT_OP;
    }

    tokenEnd = tokenStart + 1;
    if (first == '<' || first == '>') {
      return PrismioTypes.RELATIONAL_OP;
    }
    if (first == '=') {
      return PrismioTypes.ASSIGNMENT_OP;
    }
    if (first == '!') {
      return PrismioTypes.NEGATION;
    }
    if (first == '&' || first == '|' || first == '^' || first == '~') {
      return PrismioTypes.BITWISE_OP;
    }
    return PrismioTypes.ARITHMETIC_OP;
  }

  private static @Nullable IElementType separator(char c) {
    return switch (c) {
      case '(' -> PrismioTypes.LPAREN;
      case ')' -> PrismioTypes.RPAREN;
      case '{' -> PrismioTypes.LBRACE;
      case '}' -> PrismioTypes.RBRACE;
      case '[' -> PrismioTypes.LBRACKET;
      case ']' -> PrismioTypes.RBRACKET;
      case ',' -> PrismioTypes.COMMA;
      case ':' -> PrismioTypes.COLON;
      case '.' -> PrismioTypes.DOT;
      case '?' -> PrismioTypes.OPTIONAL;
      case '@' -> PrismioTypes.AT;
      default -> null;
    };
  }

  /**
   * {@code default} is a keyword -- {@code let c: Config = default} is the zero value -- and
   * also the name of a type's own function, which the parser accepts after a {@code .}
   * ({@code Config.default()}, {@code T.default()}, {@code import std.default}) and after
   * {@code fn}. As a keyword it would be spaced like one: {@code Config.default ()}.
   * Decided by what comes before, which an edit after the word cannot make stale.
   */
  private boolean isDefaultAsName(String text) {
    if (!text.equals("default")) {
      return false;
    }
    int at = tokenStart - 1;
    while (at >= 0 && (buffer.charAt(at) == ' ' || buffer.charAt(at) == '\t')) {
      at--;
    }
    if (at < 0) {
      return false;
    }
    if (buffer.charAt(at) == '.') {
      return true;
    }
    return at >= 1 && buffer.charAt(at) == 'n' && buffer.charAt(at - 1) == 'f'
        && (at < 2 || !isIdentifierPart(buffer.charAt(at - 2)));
  }

  private boolean peekIs(int offset, char expected) {
    int at = tokenStart + offset;
    return at < bufferEnd && buffer.charAt(at) == expected;
  }

  private static boolean isCompoundAssignable(char c) {
    // The set `isTwoCharOperator` accepts before an `=`. `~=` is deliberately
    // absent: there is no compound form of bitwise NOT.
    return c == '+' || c == '-' || c == '*' || c == '/' || c == '%'
        || c == '&' || c == '|' || c == '^';
  }

  /** `isOperator` in `src/common/text.psm`, character for character. */
  private static boolean isOperatorChar(char c) {
    return c == '+' || c == '-' || c == '*' || c == '/' || c == '%'
        || c == '=' || c == '!' || c == '<' || c == '>'
        || c == '&' || c == '|' || c == '^' || c == '~';
  }

  private static boolean isWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isHexDigit(char c) {
    return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static boolean isOctalDigit(char c) {
    return c >= '0' && c <= '7';
  }

  private static boolean isBinaryDigit(char c) {
    return c == '0' || c == '1';
  }

  private static boolean isIdentifierStart(char c) {
    return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  // Identifiers past ASCII follow UAX #31, as src/lexer/scanner.psm reads them:
  // XID_Start, then XID_Continue less U+200C and U+200D, which the compiler
  // excludes because they are invisible. The JDK's sets are ID_Start and
  // ID_Continue at the JDK's Unicode version, not the compiler's pinned 18.0.0,
  // so a character newer than the JDK can colour as an error and still compile:
  // the compiler is the authority, and this only highlights.
  private boolean identifierStartsAt(int at) {
    char c = buffer.charAt(at);
    if (c < 128) {
      return isIdentifierStart(c);
    }
    return Character.isUnicodeIdentifierStart(Character.codePointAt(buffer, at));
  }

  private static boolean isUnicodeIdentifierContinue(int codePoint) {
    return codePoint != 0x200C
        && codePoint != 0x200D
        && Character.isUnicodeIdentifierPart(codePoint)
        && !Character.isIdentifierIgnorable(codePoint);
  }

  private static boolean isIdentifierPart(char c) {
    return isIdentifierStart(c) || isDigit(c);
  }
}
