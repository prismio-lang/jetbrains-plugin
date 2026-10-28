package io.prismio.psi;

import com.intellij.psi.tree.TokenSet;

public interface PrismioTokenSets {
  TokenSet IDENTIFIERS = TokenSet.create(PrismioTypes.IDENTIFIER);

  TokenSet COMMENTS = TokenSet.create(
      PrismioTypes.LINE_COMMENT, PrismioTypes.BLOCK_COMMENT, PrismioTypes.DOC_COMMENT);

  /** Everything a reader reads as a reserved word, whatever the parser calls it. */
  TokenSet KEYWORDS = TokenSet.create(PrismioTypes.KEYWORD, PrismioTypes.CONTEXTUAL_KEYWORD);

  TokenSet TYPES = TokenSet.create(PrismioTypes.BUILTIN_TYPE, PrismioTypes.STDLIB_TYPE);

  TokenSet LITERALS = TokenSet.create(PrismioTypes.STRING_LITERAL, PrismioTypes.CHARACTER_LITERAL,
      PrismioTypes.INTEGER, PrismioTypes.FLOAT, PrismioTypes.BOOLEAN);

  TokenSet OPERATORS = TokenSet.create(PrismioTypes.ARITHMETIC_OP, PrismioTypes.RELATIONAL_OP,
      PrismioTypes.ASSIGNMENT_OP, PrismioTypes.LOGICAL_OP, PrismioTypes.BITWISE_OP,
      PrismioTypes.SHIFT_OP, PrismioTypes.NEGATION, PrismioTypes.ARROW, PrismioTypes.FAT_ARROW,
      PrismioTypes.RANGE, PrismioTypes.ELLIPSIS);

  TokenSet SEPARATORS = TokenSet.create(PrismioTypes.LPAREN, PrismioTypes.RPAREN,
      PrismioTypes.LBRACE, PrismioTypes.RBRACE, PrismioTypes.LBRACKET, PrismioTypes.RBRACKET,
      PrismioTypes.COMMA, PrismioTypes.COLON, PrismioTypes.DOT, PrismioTypes.OPTIONAL,
      PrismioTypes.AT);

  /** Braces only. The services that infer nesting from the flat token stream use this. */
  TokenSet BRACES = TokenSet.create(PrismioTypes.LBRACE, PrismioTypes.RBRACE);
}
