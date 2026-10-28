package io.prismio.highlighter;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.prismio.lexer.PrismioLexer;
import io.prismio.psi.PrismioTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Complete Syntax Highlighter for Prismio Language
 * Provides rich color schemes for all token types
 */

public final class PrismioSyntaxHighlighter extends SyntaxHighlighterBase {
  // Keywords
  public static final TextAttributesKey KEYWORD =
      createTextAttributesKey("PRISMIO_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

  /** `Int`, `String`, `U64` — the compiler's own type table. */
  public static final TextAttributesKey BUILTIN_TYPE =
      createTextAttributesKey("PRISMIO_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME);

  /** `List`, `Result`, `Box` — shipped in `std`, so a reference rather than a built-in. */
  public static final TextAttributesKey STDLIB_TYPE = createTextAttributesKey(
      "PRISMIO_STDLIB_TYPE", DefaultLanguageHighlighterColors.CLASS_REFERENCE);

  /**
   * `private`, `dyn`, `pin`, `produce` — words the parser gives meaning to in one
   * position and treats as identifiers everywhere else. Its own key, defaulting
   * to the keyword colour: a reader wants to see them, and anyone who would
   * rather tell them apart from a reserved word can, without editing two colours.
   */
  public static final TextAttributesKey CONTEXTUAL_KEYWORD = createTextAttributesKey(
      "PRISMIO_CONTEXTUAL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

  // Literals
  public static final TextAttributesKey STRING =
      createTextAttributesKey("PRISMIO_STRING", DefaultLanguageHighlighterColors.STRING);

  public static final TextAttributesKey NUMBER =
      createTextAttributesKey("PRISMIO_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

  public static final TextAttributesKey BOOLEAN =
      createTextAttributesKey("PRISMIO_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey CHARACTER =
      createTextAttributesKey("PRISMIO_CHAR", DefaultLanguageHighlighterColors.STRING);

  // Operators and separators
  public static final TextAttributesKey OPERATOR =
      createTextAttributesKey("PRISMIO_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);

  public static final TextAttributesKey SEPARATOR =
      createTextAttributesKey("PRISMIO_SEPARATOR", DefaultLanguageHighlighterColors.BRACES);

  public static final TextAttributesKey COMMA =
      createTextAttributesKey("PRISMIO_COMMA", DefaultLanguageHighlighterColors.COMMA);

  public static final TextAttributesKey SEMICOLON =
      createTextAttributesKey("PRISMIO_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);

  public static final TextAttributesKey DOT =
      createTextAttributesKey("PRISMIO_DOT", DefaultLanguageHighlighterColors.DOT);

  // Comments
  public static final TextAttributesKey LINE_COMMENT = createTextAttributesKey(
      "PRISMIO_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

  public static final TextAttributesKey BLOCK_COMMENT = createTextAttributesKey(
      "PRISMIO_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);

  /** A `///` line. A convention rather than syntax, so it renders as documentation. */
  public static final TextAttributesKey DOC_COMMENT = createTextAttributesKey(
      "PRISMIO_DOC_COMMENT", DefaultLanguageHighlighterColors.DOC_COMMENT);

  // Identifiers
  public static final TextAttributesKey IDENTIFIER =
      createTextAttributesKey("PRISMIO_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

  public static final TextAttributesKey FUNCTION_DECLARATION = createTextAttributesKey(
      "PRISMIO_FUNCTION_DECL", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);

  public static final TextAttributesKey FUNCTION_CALL = createTextAttributesKey(
      "PRISMIO_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);

  public static final TextAttributesKey METHOD_CALL = createTextAttributesKey(
      "PRISMIO_METHOD_CALL", DefaultLanguageHighlighterColors.INSTANCE_METHOD);

  public static final TextAttributesKey PARAMETER =
      createTextAttributesKey("PRISMIO_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);

  public static final TextAttributesKey LOCAL_VARIABLE = createTextAttributesKey(
      "PRISMIO_LOCAL_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);

  public static final TextAttributesKey MUTABLE_VARIABLE = createTextAttributesKey(
      "PRISMIO_MUTABLE_VARIABLE", DefaultLanguageHighlighterColors.REASSIGNED_LOCAL_VARIABLE);

  public static final TextAttributesKey CONSTANT =
      createTextAttributesKey("PRISMIO_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT);

  public static final TextAttributesKey FIELD =
      createTextAttributesKey("PRISMIO_FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

  public static final TextAttributesKey STRUCT_NAME =
      createTextAttributesKey("PRISMIO_STRUCT", DefaultLanguageHighlighterColors.CLASS_NAME);

  public static final TextAttributesKey ENUM_NAME =
      createTextAttributesKey("PRISMIO_ENUM", DefaultLanguageHighlighterColors.CLASS_NAME);

  public static final TextAttributesKey TRAIT_NAME =
      createTextAttributesKey("PRISMIO_TRAIT", DefaultLanguageHighlighterColors.INTERFACE_NAME);

  public static final TextAttributesKey ENUM_VARIANT = createTextAttributesKey(
      "PRISMIO_ENUM_VARIANT", DefaultLanguageHighlighterColors.STATIC_FIELD);

  public static final TextAttributesKey TYPE_REFERENCE = createTextAttributesKey(
      "PRISMIO_TYPE_REFERENCE", DefaultLanguageHighlighterColors.CLASS_REFERENCE);

  public static final TextAttributesKey IMPORT_PATH =
      createTextAttributesKey("PRISMIO_IMPORT_PATH", DefaultLanguageHighlighterColors.IDENTIFIER);

  // Special
  public static final TextAttributesKey BAD_CHARACTER =
      createTextAttributesKey("PRISMIO_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

  // Key arrays for faster lookup
  private static final TextAttributesKey[] KEYWORD_KEYS = new TextAttributesKey[] {KEYWORD};
  private static final TextAttributesKey[] TYPE_KEYS = new TextAttributesKey[] {BUILTIN_TYPE};
  private static final TextAttributesKey[] STDLIB_TYPE_KEYS =
      new TextAttributesKey[] {STDLIB_TYPE};
  private static final TextAttributesKey[] CONTEXTUAL_KEYWORD_KEYS =
      new TextAttributesKey[] {CONTEXTUAL_KEYWORD};
  private static final TextAttributesKey[] DOC_COMMENT_KEYS =
      new TextAttributesKey[] {DOC_COMMENT};
  private static final TextAttributesKey[] STRING_KEYS = new TextAttributesKey[] {STRING};
  private static final TextAttributesKey[] NUMBER_KEYS = new TextAttributesKey[] {NUMBER};
  private static final TextAttributesKey[] BOOLEAN_KEYS = new TextAttributesKey[] {BOOLEAN};
  private static final TextAttributesKey[] CHAR_KEYS = new TextAttributesKey[] {CHARACTER};
  private static final TextAttributesKey[] OPERATOR_KEYS = new TextAttributesKey[] {OPERATOR};
  private static final TextAttributesKey[] SEPARATOR_KEYS = new TextAttributesKey[] {SEPARATOR};
  private static final TextAttributesKey[] COMMA_KEYS = new TextAttributesKey[] {COMMA};
  private static final TextAttributesKey[] DOT_KEYS = new TextAttributesKey[] {DOT};
  private static final TextAttributesKey[] SEMICOLON_KEYS = new TextAttributesKey[] {SEMICOLON};
  private static final TextAttributesKey[] LINE_COMMENT_KEYS =
      new TextAttributesKey[] {LINE_COMMENT};
  private static final TextAttributesKey[] BLOCK_COMMENT_KEYS =
      new TextAttributesKey[] {BLOCK_COMMENT};
  private static final TextAttributesKey[] IDENTIFIER_KEYS = new TextAttributesKey[] {IDENTIFIER};
  private static final TextAttributesKey[] BAD_CHAR_KEYS = new TextAttributesKey[] {BAD_CHARACTER};
  private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new PrismioLexer();
  }

  @Override
  public TextAttributesKey @NotNull[] getTokenHighlights(IElementType tokenType) {
    // Keywords
    if (tokenType.equals(PrismioTypes.KEYWORD))
      return KEYWORD_KEYS;
    if (tokenType.equals(PrismioTypes.CONTEXTUAL_KEYWORD))
      return CONTEXTUAL_KEYWORD_KEYS;
    if (tokenType.equals(PrismioTypes.BUILTIN_TYPE))
      return TYPE_KEYS;
    if (tokenType.equals(PrismioTypes.STDLIB_TYPE))
      return STDLIB_TYPE_KEYS;

    // Literals
    if (tokenType.equals(PrismioTypes.STRING_LITERAL))
      return STRING_KEYS;
    if (tokenType.equals(PrismioTypes.CHARACTER_LITERAL))
      return CHAR_KEYS;
    if (tokenType.equals(PrismioTypes.INTEGER) || tokenType.equals(PrismioTypes.FLOAT))
      return NUMBER_KEYS;
    if (tokenType.equals(PrismioTypes.BOOLEAN))
      return BOOLEAN_KEYS;

    // Operators
    if (tokenType.equals(PrismioTypes.ARITHMETIC_OP) || tokenType.equals(PrismioTypes.RELATIONAL_OP)
        || tokenType.equals(PrismioTypes.ASSIGNMENT_OP)
        || tokenType.equals(PrismioTypes.LOGICAL_OP) || tokenType.equals(PrismioTypes.BITWISE_OP)
        || tokenType.equals(PrismioTypes.SHIFT_OP) || tokenType.equals(PrismioTypes.NEGATION)
        || tokenType.equals(PrismioTypes.ARROW) || tokenType.equals(PrismioTypes.FAT_ARROW)
        || tokenType.equals(PrismioTypes.RANGE) || tokenType.equals(PrismioTypes.ELLIPSIS)) {
      return OPERATOR_KEYS;
    }

    // Separators
    if (tokenType.equals(PrismioTypes.LPAREN) || tokenType.equals(PrismioTypes.RPAREN)
        || tokenType.equals(PrismioTypes.LBRACE) || tokenType.equals(PrismioTypes.RBRACE)
        || tokenType.equals(PrismioTypes.LBRACKET) || tokenType.equals(PrismioTypes.RBRACKET)) {
      return SEPARATOR_KEYS;
    }
    if (tokenType.equals(PrismioTypes.COMMA))
      return COMMA_KEYS;
    if (tokenType.equals(PrismioTypes.DOT))
      return DOT_KEYS;
    if (tokenType.equals(PrismioTypes.COLON)) {
      return SEMICOLON_KEYS;
    }
    if (tokenType.equals(PrismioTypes.OPTIONAL) || tokenType.equals(PrismioTypes.AT)) {
      return OPERATOR_KEYS;
    }

    // Comments
    if (tokenType.equals(PrismioTypes.LINE_COMMENT))
      return LINE_COMMENT_KEYS;
    if (tokenType.equals(PrismioTypes.BLOCK_COMMENT))
      return BLOCK_COMMENT_KEYS;
    if (tokenType.equals(PrismioTypes.DOC_COMMENT))
      return DOC_COMMENT_KEYS;

    // Identifiers
    if (tokenType.equals(PrismioTypes.IDENTIFIER))
      return IDENTIFIER_KEYS;

    // Bad characters
    if (tokenType.equals(TokenType.BAD_CHARACTER))
      return BAD_CHAR_KEYS;

    return EMPTY_KEYS;
  }
}
