package io.prismio.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import io.prismio.psi.PrismioTokenSets;
import io.prismio.PrismioLanguage;
import io.prismio.psi.PrismioTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enhanced code formatter for Prismio with comprehensive spacing rules.
 * Uses brace counting for indentation since PSI tree is flat (no block nodes).
 */
final class PrismioBlock extends AbstractBlock {
  private final CodeStyleSettings settings;
  private final SpacingBuilder spacingBuilder;
  /** The column a token that begins its line is indented to. */
  private final int indentLevel;
  private @Nullable Set<Integer> typeArgumentAngles;
  private @Nullable Set<Integer> branchLineBreaks;

  public PrismioBlock(@NotNull ASTNode node, @Nullable Wrap wrap, @Nullable Alignment alignment,
      CodeStyleSettings settings) {
    this(node, wrap, alignment, settings, 0);
  }

  public PrismioBlock(@NotNull ASTNode node, @Nullable Wrap wrap, @Nullable Alignment alignment,
      CodeStyleSettings settings, int indentLevel) {
    super(node, wrap, alignment);
    this.settings = settings;
    this.spacingBuilder = createSpacingBuilder(settings);
    this.indentLevel = indentLevel;
  }

  /**
   * Spacing, most specific rule first — {@link SpacingBuilder} takes the first
   * match, so order is the whole design.
   *
   * <p>There is deliberately no blanket "no space after an identifier" rule.
   * One used to sit above these, and because `and`, `or`, `as` and `in` are
   * keywords used after an expression, it collapsed `a and b` into `aand b` and
   * `for i in xs` into `for iin xs` — reformatting produced source the compiler
   * rejects. Every case it was there for (a call's parenthesis, an index
   * bracket, a field dot) has its own rule below.
   */
  private static SpacingBuilder createSpacingBuilder(CodeStyleSettings settings) {
    return new SpacingBuilder(settings, PrismioLanguage.INSTANCE)
        // Tightest first: these bind harder than anything they sit between.
        .around(PrismioTypes.DOT).spaces(0)
        .around(PrismioTypes.RANGE).spaces(0)
        .before(PrismioTypes.OPTIONAL).spaces(0)
        // A label: `outer@ for`, `break@outer`. The mark hugs the name on both
        // sides; only the loop keyword after a label's declaration keeps a space.
        .before(PrismioTypes.AT).spaces(0)
        .between(PrismioTypes.AT, PrismioTypes.KEYWORD).spaces(1)
        .between(PrismioTypes.AT, PrismioTypes.CONTEXTUAL_KEYWORD).spaces(1)
        .after(PrismioTypes.AT).spaces(0)

        // Punctuation that closes up on its neighbour, above the keyword rules:
        // `(none)`, `name: String borrow)`, `x.type)` and a field named `type:`
        // are a keyword beside a bracket, and the bracket wins.
        .before(PrismioTypes.COMMA).spaces(0)
        .before(PrismioTypes.COLON).spaces(0)
        .before(PrismioTypes.RPAREN).spaces(0)
        .before(PrismioTypes.RBRACKET).spaces(0)
        .after(PrismioTypes.LPAREN).spaces(0)
        .after(PrismioTypes.LBRACKET).spaces(0)
        // An operator before a bracket is an operand's bracket, not a call or an
        // index: `a * (b + c)`, `let v = [1, 2]`, `-> [Int]`.
        .between(OPERATORS, OPENING_BRACKETS).spaces(1)

        // A keyword always has air around it. Above the parenthesis rules, so
        // `if (a)` keeps its space while `println(a)` does not.
        .after(PrismioTypes.KEYWORD).spaces(1)
        .before(PrismioTypes.KEYWORD).spaces(1)
        // `repeat(3)` and `produce(free)` are calls in shape; a contextual word
        // before anything else is a keyword and keeps its air.
        .between(PrismioTypes.CONTEXTUAL_KEYWORD, PrismioTypes.LPAREN).spaces(0)
        .after(PrismioTypes.CONTEXTUAL_KEYWORD).spaces(1)

        .after(PrismioTypes.COMMA).spaces(1)
        .after(PrismioTypes.COLON).spaces(1)

        .before(PrismioTypes.LPAREN).spaces(0)
        .before(PrismioTypes.LBRACKET).spaces(0)

        .around(PrismioTypes.ARROW).spaces(1)
        .around(PrismioTypes.FAT_ARROW).spaces(1)
        .around(PrismioTypes.ARITHMETIC_OP).spaces(1)
        .around(PrismioTypes.RELATIONAL_OP).spaces(1)
        .around(PrismioTypes.ASSIGNMENT_OP).spaces(1)
        .around(PrismioTypes.LOGICAL_OP).spaces(1)
        .around(PrismioTypes.BITWISE_OP).spaces(1)
        .around(PrismioTypes.SHIFT_OP).spaces(1)

        .before(PrismioTypes.LBRACE).spaces(1);
  }

  private static final TokenSet OPERATORS = TokenSet.create(
      PrismioTypes.ARITHMETIC_OP, PrismioTypes.RELATIONAL_OP, PrismioTypes.ASSIGNMENT_OP,
      PrismioTypes.LOGICAL_OP, PrismioTypes.BITWISE_OP, PrismioTypes.SHIFT_OP,
      PrismioTypes.ARROW, PrismioTypes.FAT_ARROW);

  private static final TokenSet OPENING_BRACKETS = TokenSet.create(PrismioTypes.LPAREN, PrismioTypes.LBRACKET);

  /**
   * What a `-` or `+` directly after makes it a sign rather than an operator: nothing, an
   * opening bracket, a separator, another operator, or a keyword (`return -1`, `in -3..3`).
   */
  private static final TokenSet BEFORE_A_SIGN = TokenSet.create(
      PrismioTypes.LPAREN, PrismioTypes.LBRACKET, PrismioTypes.LBRACE, PrismioTypes.COMMA,
      PrismioTypes.COLON, PrismioTypes.RANGE, PrismioTypes.KEYWORD,
      PrismioTypes.ARITHMETIC_OP, PrismioTypes.RELATIONAL_OP, PrismioTypes.ASSIGNMENT_OP,
      PrismioTypes.LOGICAL_OP, PrismioTypes.BITWISE_OP, PrismioTypes.SHIFT_OP,
      PrismioTypes.ARROW, PrismioTypes.FAT_ARROW);

  /** Whether this `-` or `+` is a sign: `(-1, 0)` and `x = -y` rather than `a - b`. */
  private static boolean isSign(@NotNull ASTNode node) {
    if (node.getElementType() != PrismioTypes.ARITHMETIC_OP) {
      return false;
    }
    String text = node.getText();
    if (!text.equals("-") && !text.equals("+")) {
      return false;
    }
    ASTNode before = node.getTreePrev();
    while (before != null && (before.getElementType() == TokenType.WHITE_SPACE
        || PrismioTokenSets.COMMENTS.contains(before.getElementType()))) {
      before = before.getTreePrev();
    }
    return before == null || BEFORE_A_SIGN.contains(before.getElementType());
  }

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

  /**
   * Where an `if` that has an `else` must start a new line: before the first token inside each of
   * its branches and before each branch's closing brace. {@code if (a) { b() } else { c() }} is
   * written on several lines, one branch to a block; an `if` with no `else` is not touched and stays
   * wherever its author put it. An empty branch stays `{ }`.
   *
   * <p>The PSI tree is flat, so a chain is found the way the compiler's parser finds it: `if`, a
   * parenthesised condition, a braced block, then `else` followed by another `if` or a block.
   * Anything that does not fit that shape is left alone.
   */
  private static Set<Integer> branchLineBreaks(ASTNode fileNode) {
    List<ASTNode> tokens = new ArrayList<>();
    for (ASTNode child : fileNode.getChildren(null)) {
      IElementType type = child.getElementType();
      if (type != TokenType.WHITE_SPACE && !PrismioTokenSets.COMMENTS.contains(type)) {
        tokens.add(child);
      }
    }

    Set<Integer> breaks = new HashSet<>();
    for (int i = 0; i < tokens.size(); i++) {
      if (!isWord(tokens.get(i), "if")) {
        continue;
      }
      List<int[]> branches = new ArrayList<>();
      boolean hasElse = false;
      int at = i;
      while (true) {
        int open = blockAfterCondition(tokens, at + 1);
        int close = open < 0 ? -1 : closingBrace(tokens, open);
        if (close < 0) {
          break;
        }
        branches.add(new int[] {open, close});
        int next = close + 1;
        if (next >= tokens.size() || !isWord(tokens.get(next), "else")) {
          break;
        }
        hasElse = true;
        next++;
        if (next < tokens.size() && isWord(tokens.get(next), "if")) {
          at = next;
          continue;
        }
        if (next < tokens.size() && tokens.get(next).getElementType() == PrismioTypes.LBRACE) {
          int elseClose = closingBrace(tokens, next);
          if (elseClose > 0) {
            branches.add(new int[] {next, elseClose});
          }
        }
        break;
      }
      if (!hasElse) {
        continue;
      }
      for (int[] branch : branches) {
        if (branch[1] > branch[0] + 1) {
          breaks.add(tokens.get(branch[0] + 1).getStartOffset());
          breaks.add(tokens.get(branch[1]).getStartOffset());
        }
      }
    }
    return breaks;
  }

  private static boolean isWord(ASTNode token, String word) {
    return token.getElementType() == PrismioTypes.KEYWORD && word.equals(token.getText());
  }

  /** The index of the `{` after the parenthesised condition that starts at {@code from}, or -1. */
  private static int blockAfterCondition(List<ASTNode> tokens, int from) {
    if (from >= tokens.size() || tokens.get(from).getElementType() != PrismioTypes.LPAREN) {
      return -1;
    }
    int depth = 0;
    for (int i = from; i < tokens.size(); i++) {
      IElementType type = tokens.get(i).getElementType();
      if (type == PrismioTypes.LPAREN) {
        depth++;
      } else if (type == PrismioTypes.RPAREN && --depth == 0) {
        boolean opens = i + 1 < tokens.size()
            && tokens.get(i + 1).getElementType() == PrismioTypes.LBRACE;
        return opens ? i + 1 : -1;
      }
    }
    return -1;
  }

  /** The index of the `}` that closes the `{` at {@code open}, or -1. */
  private static int closingBrace(List<ASTNode> tokens, int open) {
    int depth = 0;
    for (int i = open; i < tokens.size(); i++) {
      IElementType type = tokens.get(i).getElementType();
      if (type == PrismioTypes.LBRACE) {
        depth++;
      } else if (type == PrismioTypes.RBRACE && --depth == 0) {
        return i;
      }
    }
    return -1;
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
          boolean leadingOperator = type == PrismioTypes.DOT || OPERATORS.contains(type)
              || (type == PrismioTypes.KEYWORD && (child.getText().equals("and") || child.getText().equals("or")));
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

  /** Computed once per file, like the angle brackets: the scan is over the whole token list. */
  private Set<Integer> branchLineBreaks() {
    if (branchLineBreaks == null) {
      branchLineBreaks = branchLineBreaks(myNode);
    }
    return branchLineBreaks;
  }

  private static boolean isOpeningAngle(@Nullable Block block) {
    return block instanceof PrismioBlock prismio && isAngleOpen(prismio.myNode);
  }

  private boolean isTypeArgumentAngle(@Nullable Block block) {
    return block instanceof PrismioBlock prismio
        && typeArgumentAngles().contains(prismio.myNode.getStartOffset());
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    // The branches of an `if` that has an `else` each start on a line of their own.
    if (child2 instanceof PrismioBlock next && branchLineBreaks().contains(next.myNode.getStartOffset())) {
      return Spacing.createSpacing(0, 0, 1, true, 1);
    }
    // `List<Int>` closes up; `a < b` keeps its air. Checked before the builder,
    // because to it both are the same relational-operator token.
    //
    // Only *inside* the brackets. What follows the closing `>` is ordinary code
    // and keeps its ordinary spacing -- `List<Int> = list_new()` needs the space
    // before the `=`, and `Point<Int> { x: 1 }` needs the one before the brace.
    if (isTypeArgumentAngle(child2)
        || (isTypeArgumentAngle(child1) && isOpeningAngle(child1))) {
      return Spacing.createSpacing(0, 0, 0, false, 0);
    }
    // `listOf<Int>(a)` is a call: the closing `>` is not an operator before a bracket.
    if (isTypeArgumentAngle(child1) && child2 instanceof PrismioBlock next
        && next.myNode.getElementType() == PrismioTypes.LPAREN) {
      return Spacing.createSpacing(0, 0, 0, false, 0);
    }
    // An anonymous function is written like a call, `fn(a: Int) -> Int { }`, which is how
    // every one in the compiler and its documentation is spelled.
    if (child1 instanceof PrismioBlock word && word.myNode.getElementType() == PrismioTypes.KEYWORD
        && "fn".equals(word.myNode.getText()) && child2 instanceof PrismioBlock next
        && next.myNode.getElementType() == PrismioTypes.LPAREN) {
      return Spacing.createSpacing(0, 0, 0, false, 0);
    }
    // `~` is only ever prefix, so it hugs its operand: `(~e) & g`, never `(~ e)`. It
    // lexes as a bitwise operator, which the builder spaces on both sides.
    if (child1 instanceof PrismioBlock tilde && tilde.myNode.getElementType() == PrismioTypes.BITWISE_OP
        && "~".equals(tilde.myNode.getText())) {
      return Spacing.createSpacing(0, 0, 0, false, 0);
    }
    // A sign hugs what it signs: `(-1, 0)`, never `(- 1, 0)`.
    if (child1 instanceof PrismioBlock sign && isSign(sign.myNode)) {
      return Spacing.createSpacing(0, 0, 0, false, 0);
    }
    return spacingBuilder.getSpacing(this, child1, child2);
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
