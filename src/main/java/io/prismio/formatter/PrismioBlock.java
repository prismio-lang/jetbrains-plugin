package io.prismio.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import io.prismio.psi.PrismioTokenSets;
import io.prismio.psi.PrismioTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The indentation of a Prismio line, which the platform asks for while the author types and when no language server
 * is running. Uses brace counting since the PSI tree is flat (no block nodes). It does not space tokens: that is
 * `prismio fmt`, which Reformat Code runs through the language server.
 */
final class PrismioBlock extends AbstractBlock {
  private final CodeStyleSettings settings;
  /** The column a token that begins its line is indented to. */
  private final int indentLevel;
  private @Nullable Set<Integer> typeArgumentAngles;

  public PrismioBlock(@NotNull ASTNode node, @Nullable Wrap wrap, @Nullable Alignment alignment,
      CodeStyleSettings settings) {
    this(node, wrap, alignment, settings, 0);
  }

  public PrismioBlock(@NotNull ASTNode node, @Nullable Wrap wrap, @Nullable Alignment alignment,
      CodeStyleSettings settings, int indentLevel) {
    super(node, wrap, alignment);
    this.settings = settings;
    this.indentLevel = indentLevel;
  }

  private static final TokenSet OPERATORS = TokenSet.create(
      PrismioTypes.ARITHMETIC_OP, PrismioTypes.RELATIONAL_OP, PrismioTypes.ASSIGNMENT_OP,
      PrismioTypes.LOGICAL_OP, PrismioTypes.BITWISE_OP, PrismioTypes.SHIFT_OP,
      PrismioTypes.ARROW, PrismioTypes.FAT_ARROW);

  private static final TokenSet OPENING_BRACKETS = TokenSet.create(PrismioTypes.LPAREN, PrismioTypes.LBRACKET);

  /**
   * Whether this `<`, `>` or `>>` delimits a type-argument list.
   *
   * <p>They lex as relational and shift operators, so the rule that puts air
   * around a comparison would otherwise turn `List<Int>` into `List < Int >`.
   * Told apart the way the compiler's parser tells them apart: scan from the `<`
   * to its matching `>` and require everything between to be something a type
   * can be made of. `a < b` has no closing `>`; `if (a < b)` hits a `)`; and
   * `x < 5` hits an integer. `List<Map<String, Int>>` closes cleanly, with the
   * `>>` counting for two.
   */
  private static Set<Integer> typeArgumentAngles(ASTNode fileNode) {
    List<ASTNode> tokens = new ArrayList<>();
    for (ASTNode child : fileNode.getChildren(null)) {
      IElementType type = child.getElementType();
      if (type != TokenType.WHITE_SPACE && !PrismioTokenSets.COMMENTS.contains(type)) {
        tokens.add(child);
      }
    }

    Set<Integer> angles = new HashSet<>();
    for (int i = 0; i < tokens.size(); i++) {
      if (!isAngleOpen(tokens.get(i))) {
        continue;
      }
      // Only a name can introduce type arguments. A `<` after `)` or a literal
      // is a comparison whatever follows it.
      IElementType before = i == 0 ? null : tokens.get(i - 1).getElementType();
      if (before != PrismioTypes.IDENTIFIER && before != PrismioTypes.BUILTIN_TYPE
          && before != PrismioTypes.STDLIB_TYPE
          && before != PrismioTypes.CONTEXTUAL_KEYWORD) {
        continue;
      }

      int depth = 0;
      List<Integer> span = new ArrayList<>();
      for (int j = i; j < tokens.size(); j++) {
        ASTNode token = tokens.get(j);
        if (isAngleOpen(token)) {
          depth++;
          span.add(j);
        } else if (token.getElementType() == PrismioTypes.SHIFT_OP
            && ">>".equals(token.getText())) {
          depth -= 2;
          span.add(j);
        } else if (isAngleClose(token)) {
          depth--;
          span.add(j);
        } else if (!allowedInsideTypeArguments(token) && !isArrayLength(tokens, j)) {
          depth = -1;
          break;
        }
        if (depth <= 0) {
          break;
        }
      }
      if (depth == 0) {
        for (int index : span) {
          angles.add(tokens.get(index).getStartOffset());
        }
      }
    }
    return angles;
  }

  private static boolean isAngleOpen(ASTNode node) {
    return node.getElementType() == PrismioTypes.RELATIONAL_OP && "<".equals(node.getText());
  }

  private static boolean isAngleClose(ASTNode node) {
    return node.getElementType() == PrismioTypes.RELATIONAL_OP && ">".equals(node.getText());
  }

  /**
   * The length in {@code Array<U32, 64>}: an integer that is the last argument, after a
   * comma and right before the closing {@code >}. Anywhere else an integer is what
   * marks a comparison ({@code x < 5}). Refusing this one too spaced the type apart
   * as {@code Array < U32, 64 >}, and its {@code >} then read as an operator the line
   * ended on, so the next statement was indented as a continuation.
   */
  private static boolean isArrayLength(List<ASTNode> tokens, int index) {
    if (tokens.get(index).getElementType() != PrismioTypes.INTEGER
        || index == 0 || index + 1 >= tokens.size()) {
      return false;
    }
    ASTNode next = tokens.get(index + 1);
    boolean closes = isAngleClose(next)
        || (next.getElementType() == PrismioTypes.SHIFT_OP && ">>".equals(next.getText()));
    if (!closes) {
      return false;
    }
    if (tokens.get(index - 1).getElementType() == PrismioTypes.COMMA) {
      return true;
    }
    // `Int<24>` and `UInt<5>`: the width of an integer type, the only other number a type
    // argument list holds (src/ast/int_width.psm).
    return index >= 2 && isAngleOpen(tokens.get(index - 1))
        && isIntegerWidthConstructor(tokens.get(index - 2).getText());
  }

  private static boolean isIntegerWidthConstructor(String name) {
    return "Int".equals(name) || "UInt".equals(name);
  }

  /** What a type-argument list may contain, including a `+` joining two bounds. */
  private static boolean allowedInsideTypeArguments(ASTNode node) {
    IElementType type = node.getElementType();
    if (type == PrismioTypes.IDENTIFIER || type == PrismioTypes.BUILTIN_TYPE
        || type == PrismioTypes.STDLIB_TYPE || type == PrismioTypes.CONTEXTUAL_KEYWORD
        || type == PrismioTypes.COMMA || type == PrismioTypes.COLON
        || type == PrismioTypes.DOT || type == PrismioTypes.OPTIONAL
        || type == PrismioTypes.LBRACKET || type == PrismioTypes.RBRACKET) {
      return true;
    }
    // `Ptr<mut T>`: `mut` is a keyword, and refusing it spaced the list apart as `Ptr < mut T >`.
    if (type == PrismioTypes.KEYWORD && "mut".equals(node.getText())) {
      return true;
    }
    return type == PrismioTypes.ARITHMETIC_OP && "+".equals(node.getText());
  }

  /** What a line may end with and still not end its statement: `a +`, `x.`, `cond and`. */
  private static final TokenSet CONTINUING = TokenSet.orSet(OPERATORS,
      TokenSet.create(PrismioTypes.DOT, PrismioTypes.RANGE));

  /**
   * Indentation is counted in braces, since the PSI tree is flat -- except on a continuation
   * line: one inside an open `(` or `[`, or after a line that stopped mid-expression. There the
   * author's own indentation is kept, whether one step in or aligned under a bracket, and only
   * moved right when it is shallower than one step inside its block. A formatter cannot tell
   * which alignment was meant; pulling every continuation back to the block's edge, as this
   * did, lined `or b) {` up with the `if` it continues.
   */
  @Override
  protected List<Block> buildChildren() {
    List<Block> blocks = new ArrayList<>();
    ASTNode child = myNode.getFirstChildNode();
    int braceDepth = 0;
    List<IElementType> open = new ArrayList<>();
    IElementType previous = null;
    String previousText = "";
    boolean previousIsTypeArgument = false;
    IElementType beforePrevious = null;

    while (child != null) {
      if (child.getElementType() != TokenType.WHITE_SPACE && child.getTextLength() > 0) {
        IElementType type = child.getElementType();

        if (type == PrismioTypes.RBRACE) {
          braceDepth = Math.max(0, braceDepth - 1);
        }
        int spaces = braceDepth * 4;
        int existing = lineStartColumn(child);
        if (existing >= 0 && type != PrismioTypes.RBRACE && !PrismioTokenSets.COMMENTS.contains(type)) {
          boolean closing = type == PrismioTypes.RPAREN || type == PrismioTypes.RBRACKET;
          IElementType innermost = open.isEmpty() ? null : open.get(open.size() - 1);
          boolean insideBrackets = innermost == PrismioTypes.LPAREN || innermost == PrismioTypes.LBRACKET;
          // `items: Vec<Int>` ends in a `>` that is a bracket, not a comparison, and
          // `import ir.*` in a `*` that is a wildcard, not a multiplication.
          boolean wildcard = previous == PrismioTypes.ARITHMETIC_OP && previousText.equals("*")
              && beforePrevious == PrismioTypes.DOT;
          boolean midExpression = previous != null && !previousIsTypeArgument && !wildcard
              && (CONTINUING.contains(previous)
              || (previous == PrismioTypes.KEYWORD && (previousText.equals("and") || previousText.equals("or"))));
          // A line that opens with an operator continues the one before: `+ b`, `.next()`, `or c`.
          boolean leadingOperator = !isNegativePattern(child) && (type == PrismioTypes.DOT || OPERATORS.contains(type)
              || (type == PrismioTypes.KEYWORD && (child.getText().equals("and") || child.getText().equals("or"))));
          if (insideBrackets || midExpression || leadingOperator) {
            spaces = Math.max(existing, closing ? spaces : spaces + 4);
          } else if (previous == PrismioTypes.COMMA && innermost == PrismioTypes.LBRACE) {
            // The next field of a struct literal or the next variant of an enum: kept
            // where the author aligned it, but never shallower than its block.
            spaces = Math.max(existing, spaces);
          }
        }

        blocks.add(new PrismioBlock(child, Wrap.createWrap(WrapType.NONE, false), null, settings, spaces));

        if (type == PrismioTypes.LBRACE) {
          braceDepth++;
        }
        if (type == PrismioTypes.LPAREN || type == PrismioTypes.LBRACKET || type == PrismioTypes.LBRACE) {
          open.add(type);
        } else if ((type == PrismioTypes.RPAREN || type == PrismioTypes.RBRACKET || type == PrismioTypes.RBRACE)
            && !open.isEmpty()) {
          open.remove(open.size() - 1);
        }
        if (!PrismioTokenSets.COMMENTS.contains(type)) {
          beforePrevious = previous;
          previous = type;
          previousText = child.getText();
          previousIsTypeArgument = typeArgumentAngles().contains(child.getStartOffset());
        }
      }
      child = child.getTreeNext();
    }
    return blocks;
  }

  /** A `-` that begins a match arm (`-5 => { }`): a sign on the pattern, not an operator continuing the line above. */
  private static boolean isNegativePattern(@NotNull ASTNode minus) {
    if (minus.getElementType() != PrismioTypes.ARITHMETIC_OP || !minus.getText().equals("-")) {
      return false;
    }
    ASTNode number = nextToken(minus);
    if (number == null || (number.getElementType() != PrismioTypes.INTEGER && number.getElementType() != PrismioTypes.FLOAT)) {
      return false;
    }
    ASTNode after = nextToken(number);
    return after != null && (after.getElementType() == PrismioTypes.FAT_ARROW || after.getElementType() == PrismioTypes.COMMA);
  }

  private static @Nullable ASTNode nextToken(@NotNull ASTNode token) {
    ASTNode next = token.getTreeNext();
    while (next != null && next.getElementType() == TokenType.WHITE_SPACE) {
      next = next.getTreeNext();
    }
    return next;
  }

  /** The column this token's line is indented to, when the token begins its line; -1 otherwise. */
  private static int lineStartColumn(@NotNull ASTNode token) {
    ASTNode before = token.getTreePrev();
    if (before == null) {
      return 0;
    }
    if (before.getElementType() != TokenType.WHITE_SPACE) {
      return -1;
    }
    String space = before.getText();
    int newline = space.lastIndexOf('\n');
    if (newline < 0) {
      return before.getTreePrev() == null ? space.length() : -1;
    }
    int column = 0;
    for (int i = newline + 1; i < space.length(); i++) {
      column += space.charAt(i) == '\t' ? 4 : 1;
    }
    return column;
  }

  @Override
  public Indent getIndent() {
    // Every token sits directly under the file node, so an indent is an absolute column.
    if (indentLevel > 0) {
      return Indent.getSpaceIndent(indentLevel);
    }
    return Indent.getNoneIndent();
  }

  /** Computed once per file, because the scan is over the whole token list. */
  private Set<Integer> typeArgumentAngles() {
    if (typeArgumentAngles == null) {
      typeArgumentAngles = typeArgumentAngles(myNode);
    }
    return typeArgumentAngles;
  }

  /**
   * Spacing is the compiler's: `prismio fmt`, which the language server runs for Reformat Code. A block that knew some
   * of its rules disagreed with it on one file in eleven and changed the tokens of one, so this one leaves the white
   * space between two tokens as the author wrote it and only indents the lines.
   */
  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return null;
  }

  @Override
  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }

  @NotNull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
    // Get the indent level for new children based on brace context
    List<Block> children = getSubBlocks();
    int braceDepth = 0;

    for (int i = 0; i < newChildIndex && i < children.size(); i++) {
      Block block = children.get(i);
      if (block instanceof PrismioBlock) {
        IElementType type = ((PrismioBlock) block).myNode.getElementType();
        if (type == PrismioTypes.LBRACE) {
          braceDepth++;
        } else if (type == PrismioTypes.RBRACE) {
          braceDepth = Math.max(0, braceDepth - 1);
        }
      }
    }

    if (braceDepth > 0) {
      return new ChildAttributes(Indent.getSpaceIndent(braceDepth * 4), null);
    }
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }
}
