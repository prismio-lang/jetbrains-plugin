package io.prismio.psi;

import com.intellij.psi.tree.IElementType;

/**
 * The Prismio token vocabulary.
 *
 * <p>Hand-maintained, and deliberately so. This used to be generated from
 * {@code Prismio.flex} by JFlex, which meant the token set could only describe
 * what a regular expression can decide. Prismio has two things that are not
 * regular: contextual keywords such as {@code private} and {@code dyn}, which
 * are ordinary identifiers everywhere except one position, and type names whose
 * meaning depends on nothing but a fixed table. Both are trivial for a
 * hand-written lexer and impossible for a lexical grammar alone.
 *
 * <p>The names here follow {@code src/lexer/token.psm} in the compiler, which is
 * the authority. When the language gains a keyword, that file changes first and
 * this one follows.
 */
public interface PrismioTypes {

  // Literals.
  IElementType INTEGER = new PrismioTokenType("INTEGER");
  IElementType FLOAT = new PrismioTokenType("FLOAT");
  IElementType STRING_LITERAL = new PrismioTokenType("STRING_LITERAL");
  IElementType CHARACTER_LITERAL = new PrismioTokenType("CHARACTER_LITERAL");
  IElementType BOOLEAN = new PrismioTokenType("BOOLEAN");

  // Words.
  IElementType IDENTIFIER = new PrismioTokenType("IDENTIFIER");
  IElementType KEYWORD = new PrismioTokenType("KEYWORD");

  /**
   * A word the parser gives meaning to in one position and treats as an
   * identifier everywhere else: {@code private}, {@code internal}, {@code dyn},
   * {@code spawn}, {@code type}, {@code pin}, {@code unique}, {@code Self}, and
   * the FFI contract names. Highlighted, because a reader wants to see them, but
   * kept distinct from a real keyword because a variable may legally be called
   * {@code pin}.
   */
  IElementType CONTEXTUAL_KEYWORD = new PrismioTokenType("CONTEXTUAL_KEYWORD");

  /** {@code Int}, {@code String}, {@code U64} — the compiler's own type table. */
  IElementType BUILTIN_TYPE = new PrismioTokenType("BUILTIN_TYPE");

  /** {@code List}, {@code Result}, {@code Box} — shipped in {@code std}, not built in. */
  IElementType STDLIB_TYPE = new PrismioTokenType("STDLIB_TYPE");

  // Comments.
  IElementType LINE_COMMENT = new PrismioTokenType("LINE_COMMENT");

  /**
   * A `/* ... *&#47;` comment, which nests: {@code lexerSkipBlockComment} in
   * {@code src/lexer/scanner.psm} counts depth rather than scanning for the
   * first close. One token covers the whole nest, so the editor colours a
   * commented-out region the way the compiler skips it.
   */
  IElementType BLOCK_COMMENT = new PrismioTokenType("BLOCK_COMMENT");

  /** A `///` line. Not special to the compiler, which sees an ordinary comment. */
  IElementType DOC_COMMENT = new PrismioTokenType("DOC_COMMENT");

  // Operators.
  IElementType ARROW = new PrismioTokenType("ARROW");
  IElementType FAT_ARROW = new PrismioTokenType("FAT_ARROW");
  IElementType RANGE = new PrismioTokenType("RANGE");
  IElementType RELATIONAL_OP = new PrismioTokenType("RELATIONAL_OP");
  IElementType ASSIGNMENT_OP = new PrismioTokenType("ASSIGNMENT_OP");
  IElementType LOGICAL_OP = new PrismioTokenType("LOGICAL_OP");
  IElementType ARITHMETIC_OP = new PrismioTokenType("ARITHMETIC_OP");
  IElementType BITWISE_OP = new PrismioTokenType("BITWISE_OP");

  /**
   * `?`, the nullable-type suffix. A separator rather than an operator in
   * `src/common/text.psm`, and the comment there is worth repeating: there is no
   * ternary and no try operator, so it needs no precedence anywhere.
   */
  IElementType OPTIONAL = new PrismioTokenType("OPTIONAL");

  /**
   * `@`, the loop-label mark: `outer@ for ...` names a loop and `break@outer`
   * leaves it. A separator in `src/common/text.psm`; nothing else uses `@`.
   */
  IElementType AT = new PrismioTokenType("AT");
  IElementType SHIFT_OP = new PrismioTokenType("SHIFT_OP");
  IElementType NEGATION = new PrismioTokenType("NEGATION");

  // Separators.
  IElementType LPAREN = new PrismioTokenType("LPAREN");
  IElementType RPAREN = new PrismioTokenType("RPAREN");
  IElementType LBRACE = new PrismioTokenType("LBRACE");
  IElementType RBRACE = new PrismioTokenType("RBRACE");
  IElementType LBRACKET = new PrismioTokenType("LBRACKET");
  IElementType RBRACKET = new PrismioTokenType("RBRACKET");
  IElementType COMMA = new PrismioTokenType("COMMA");
  IElementType COLON = new PrismioTokenType("COLON");
  IElementType DOT = new PrismioTokenType("DOT");
  // No SEMICOLON. `;` is in neither isSeparator nor isOperator, so the compiler
  // rejects it outright: `let x = 1;` is "unexpected character `;`". Tokenising
  // it here would colour a file the compiler will not read.
}
