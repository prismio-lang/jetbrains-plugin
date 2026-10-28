package io.prismio.ums;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Lexical colours for a manifest.
 *
 * <p>Only what the lexer can decide. Whether an identifier is a block the
 * manifest system knows is a semantic question, and {@link UmsAnnotator} answers
 * it — a lexer that guessed would colour `targets` inside a string, or miss it
 * inside a block it does not know about yet.
 */
public final class UmsSyntaxHighlighter extends SyntaxHighlighterBase {

  public static final TextAttributesKey BLOCK = createTextAttributesKey(
      "UMS_BLOCK", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey KEY = createTextAttributesKey(
      "UMS_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

  public static final TextAttributesKey CALL = createTextAttributesKey(
      "UMS_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);

  /** A name declared by `const` or `enum`, and a use of one. */
  public static final TextAttributesKey CONSTANT = createTextAttributesKey(
      "UMS_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT);

  /** `${NAME}` inside a string. */
  public static final TextAttributesKey INTERPOLATION = createTextAttributesKey(
      "UMS_INTERPOLATION", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);

  /** `$$`, a literal dollar sign inside a string. */
  public static final TextAttributesKey ESCAPE = createTextAttributesKey(
      "UMS_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);

  public static final TextAttributesKey IDENTIFIER = createTextAttributesKey(
      "UMS_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

  public static final TextAttributesKey STRING = createTextAttributesKey(
      "UMS_STRING", DefaultLanguageHighlighterColors.STRING);

  public static final TextAttributesKey NUMBER = createTextAttributesKey(
      "UMS_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

  public static final TextAttributesKey BOOLEAN = createTextAttributesKey(
      "UMS_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey COMMENT = createTextAttributesKey(
      "UMS_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

  public static final TextAttributesKey BRACES = createTextAttributesKey(
      "UMS_BRACES", DefaultLanguageHighlighterColors.BRACES);

  public static final TextAttributesKey PARENTHESES = createTextAttributesKey(
      "UMS_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);

  public static final TextAttributesKey BRACKETS = createTextAttributesKey(
      "UMS_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);

  public static final TextAttributesKey COMMA = createTextAttributesKey(
      "UMS_COMMA", DefaultLanguageHighlighterColors.COMMA);

  public static final TextAttributesKey EQUAL = createTextAttributesKey(
      "UMS_EQUAL", DefaultLanguageHighlighterColors.OPERATION_SIGN);

  public static final TextAttributesKey SEMICOLON = createTextAttributesKey(
      "UMS_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);

  /** An identifier the manifest system does not recognise, so it will be rejected. */
  public static final TextAttributesKey UNKNOWN = createTextAttributesKey(
      "UMS_UNKNOWN", HighlighterColors.TEXT);

  public static final TextAttributesKey BAD_CHARACTER = createTextAttributesKey(
      "UMS_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

  private static final TextAttributesKey[] IDENTIFIER_KEYS = {IDENTIFIER};
  private static final TextAttributesKey[] STRING_KEYS = {STRING};
  private static final TextAttributesKey[] NUMBER_KEYS = {NUMBER};
  private static final TextAttributesKey[] BOOLEAN_KEYS = {BOOLEAN};
  private static final TextAttributesKey[] COMMENT_KEYS = {COMMENT};
  private static final TextAttributesKey[] BRACE_KEYS = {BRACES};
  private static final TextAttributesKey[] PAREN_KEYS = {PARENTHESES};
  private static final TextAttributesKey[] BRACKET_KEYS = {BRACKETS};
  private static final TextAttributesKey[] COMMA_KEYS = {COMMA};
  private static final TextAttributesKey[] EQUAL_KEYS = {EQUAL};
  private static final TextAttributesKey[] SEMICOLON_KEYS = {SEMICOLON};
  private static final TextAttributesKey[] BAD_CHARACTER_KEYS = {BAD_CHARACTER};
  private static final TextAttributesKey[] EMPTY = TextAttributesKey.EMPTY_ARRAY;

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new UmsLexer();
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    if (tokenType.equals(UmsTypes.IDENTIFIER)) {
      return IDENTIFIER_KEYS;
    }
    if (tokenType.equals(UmsTypes.STRING)) {
      return STRING_KEYS;
    }
    if (tokenType.equals(UmsTypes.INTEGER)) {
      return NUMBER_KEYS;
    }
    if (tokenType.equals(UmsTypes.BOOLEAN)) {
      return BOOLEAN_KEYS;
    }
    if (tokenType.equals(UmsTypes.COMMENT)) {
      return COMMENT_KEYS;
    }
    if (tokenType.equals(UmsTypes.LEFT_BRACE) || tokenType.equals(UmsTypes.RIGHT_BRACE)) {
      return BRACE_KEYS;
    }
    if (tokenType.equals(UmsTypes.LEFT_PAREN) || tokenType.equals(UmsTypes.RIGHT_PAREN)) {
      return PAREN_KEYS;
    }
    if (tokenType.equals(UmsTypes.LEFT_BRACKET) || tokenType.equals(UmsTypes.RIGHT_BRACKET)) {
      return BRACKET_KEYS;
    }
    if (tokenType.equals(UmsTypes.COMMA)) {
      return COMMA_KEYS;
    }
    if (tokenType.equals(UmsTypes.EQUAL)) {
      return EQUAL_KEYS;
    }
    if (tokenType.equals(UmsTypes.SEMICOLON)) {
      return SEMICOLON_KEYS;
    }
    if (tokenType.equals(TokenType.BAD_CHARACTER)) {
      return BAD_CHARACTER_KEYS;
    }
    return EMPTY;
  }
}
