package io.prismio;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.prismio.lexer.PrismioLexer;
import io.prismio.psi.PrismioTypes;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;

/**
 * The lexer against the language it claims to follow.
 *
 * <p>Every case here is one the previous JFlex lexer got wrong: it did not know
 * `and`, `or`, `where` or the contextual keywords, it had no range token, and it
 * accepted `_` digit separators the compiler's scanner has never had.
 */
public class PrismioLexerTest extends TestCase {

  private static List<IElementType> types(String source) {
    PrismioLexer lexer = new PrismioLexer();
    lexer.start(source, 0, source.length(), 0);
    List<IElementType> out = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() != TokenType.WHITE_SPACE) {
        out.add(lexer.getTokenType());
      }
      lexer.advance();
    }
    return out;
  }

  private static List<String> texts(String source) {
    PrismioLexer lexer = new PrismioLexer();
    lexer.start(source, 0, source.length(), 0);
    List<String> out = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() != TokenType.WHITE_SPACE) {
        out.add(source.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
      }
      lexer.advance();
    }
    return out;
  }

  /** The whole file is covered: offsets must be contiguous from 0 to the end. */
  private static void assertCoversInput(String source) {
    PrismioLexer lexer = new PrismioLexer();
    lexer.start(source, 0, source.length(), 0);
    int expected = 0;
    while (lexer.getTokenType() != null) {
      assertEquals("token starts where the previous ended", expected, lexer.getTokenStart());
      assertTrue("token must consume input", lexer.getTokenEnd() > lexer.getTokenStart());
      expected = lexer.getTokenEnd();
      lexer.advance();
    }
    assertEquals("lexer reached the end of the input", source.length(), expected);
  }

  public void testLogicalOperatorsAreWords() {
    // The language spells these `and`/`or`; the old lexer only knew `&&`/`||`
    // and coloured both of these as ordinary identifiers.
    assertEquals(List.of(PrismioTypes.IDENTIFIER, PrismioTypes.KEYWORD, PrismioTypes.IDENTIFIER),
        types("a and b"));
    assertEquals(List.of(PrismioTypes.IDENTIFIER, PrismioTypes.KEYWORD, PrismioTypes.IDENTIFIER),
        types("a or b"));
  }

  /**
   * `is`, `is not` and `not` are the words for `==`, `!=` and `!`, written between operands as in
   * `if x is not none and not done { }`. Each is a keyword, so a condition highlights as it does
   * with the symbols.
   */
  public void testIsAndNotAreKeywords() {
    assertEquals(List.of(PrismioTypes.IDENTIFIER, PrismioTypes.KEYWORD, PrismioTypes.IDENTIFIER),
        types("x is y"));
    assertEquals(List.of(PrismioTypes.IDENTIFIER, PrismioTypes.KEYWORD, PrismioTypes.KEYWORD,
            PrismioTypes.KEYWORD),
        types("x is not none"));
    assertEquals(List.of(PrismioTypes.KEYWORD, PrismioTypes.IDENTIFIER), types("not done"));
  }

  private static IElementType typeOfWord(String source, String word) {
    List<String> texts = texts(source);
    return types(source).get(texts.indexOf(word));
  }

  /**
   * `volatile` is a pointer capability only inside `Ptr<...>`, after any `mut` or `volatile`
   * before it, and only when a type follows (parsePtrQualifiers, src/parse/decl.psm).
   */
  public void testVolatileIsAKeywordOnlyAsAPointerCapability() {
    for (String source : List.of(
        "Ptr<volatile Header>",
        "Ptr<mut volatile Header>",
        "Ptr<volatile mut Header>",
        "Ptr< volatile\n  Header>",
        "Ptr<volatile [U8]>",
        "Ptr<volatile Int>?",
        "let p: Vec<Ptr<volatile Node>> = []",
        "fn f(p: Ptr<mut volatile Ptr<Int>>) {}")) {
      assertEquals(source, PrismioTypes.CONTEXTUAL_KEYWORD, typeOfWord(source, "volatile"));
    }
    assertEquals(PrismioTypes.KEYWORD, typeOfWord("Ptr<volatile mut Header>", "mut"));
  }

  public void testVolatileIsAnIdentifierEverywhereElse() {
    for (String source : List.of(
        "let volatile = 1",
        "volatile + 1",
        "x.volatile",
        "Ptr<volatile>",
        "Ptr<mut volatile>",
        "Vec<volatile Header>",
        "f(volatile Header)",
        "Ptr volatile Header",
        "Ptr<Header> volatile Header",
        "volatile Header")) {
      assertEquals(source, PrismioTypes.IDENTIFIER, typeOfWord(source, "volatile"));
    }
  }

  public void testTypedPointerSourceLexesWithoutAGap() {
    assertCoversInput("fn f(p: Ptr<mut volatile Header>, q: Ptr<volatile Int>?) -> Ptr<Header> {}");
  }

  public void testUnicodeIdentifiersAreOneToken() {
    // UAX #31, as the compiler reads it: a name in any script is one identifier,
    // including a supplementary-plane letter (two UTF-16 units) and a combining
    // mark after the first character.
    assertEquals(List.of("\u03C0", "\u5408\u8A08", "cafe\u0301", "\uD835\uDC00x"),
        texts("\u03C0 \u5408\u8A08 cafe\u0301 \uD835\uDC00x"));
    assertEquals(List.of(PrismioTypes.IDENTIFIER, PrismioTypes.IDENTIFIER),
        types("\u03C0 \u0442\u043E\u0447\u043A\u0430"));
    // A zero width joiner ends the name: the compiler rejects it there.
    assertEquals("a", texts("a\u200Db").get(0));
    assertCoversInput("let \u03C0 = a\u200Db");
  }

  public void testKeywordsTheOldLexerNeverHad() {
    for (String keyword : List.of("where", "as", "inout", "sink", "region", "none", "throw")) {
      assertEquals(keyword + " is a keyword", List.of(PrismioTypes.KEYWORD), types(keyword));
    }
  }

  /** `default` is the zero-value keyword, and after `.` or `fn` a function's name. */
  public void testDefaultIsAKeywordExceptAsAName() {
    assertEquals(List.of(PrismioTypes.KEYWORD), types("default"));
    assertEquals(PrismioTypes.KEYWORD, types("let c: Config = default").get(5));
    assertEquals(PrismioTypes.IDENTIFIER, types("Config.default()").get(2));
    assertEquals(PrismioTypes.IDENTIFIER, types("fn default() -> Self").get(1));
    assertEquals(PrismioTypes.IDENTIFIER, types("import std.default").get(3));
    assertEquals(PrismioTypes.KEYWORD, types("offn = default").get(2));
  }

  public void testContextualKeywordsAreTheirOwnToken() {
    for (String word : List.of("public", "private", "internal", "dyn", "spawn", "pin", "unique",
        "produce", "borrow", "alias", "Self", "type", "step", "inline",
        // Before `fn` and `struct` in a program with no operating system under it.
        "export", "naked", "section", "align", "repr", "packed")) {
      assertEquals(word + " is contextual", List.of(PrismioTypes.CONTEXTUAL_KEYWORD), types(word));
    }
  }

  /**
   * `async`, `await`, `join` and `scope` are also names -- a `scope` variable runs through the
   * compiler's own AIF code and `join` is the std function -- so each is a keyword only where
   * the parser reads it as one (docs/ASYNC_PLAN.md).
   */
  public void testAsyncWordsAreKeywordsOnlyWhereTheyAreRead() {
    assertEquals(PrismioTypes.CONTEXTUAL_KEYWORD, types("async fn f() {}").get(0));
    assertEquals(PrismioTypes.CONTEXTUAL_KEYWORD, types("let x = await fetch(1)").get(3));
    assertEquals(PrismioTypes.CONTEXTUAL_KEYWORD, types("let x = join t").get(3));
    assertEquals(PrismioTypes.CONTEXTUAL_KEYWORD, types("scope { }").get(0));

    assertEquals(PrismioTypes.IDENTIFIER, types("let scope = 1").get(1));
    assertEquals(PrismioTypes.IDENTIFIER, types("aif_scope_new(scope, id)").get(2));
    assertEquals(PrismioTypes.IDENTIFIER, types("let s = join(parts, \",\")").get(3));
    assertEquals(PrismioTypes.IDENTIFIER, types("let async = 2").get(1));
    assertEquals(PrismioTypes.IDENTIFIER, types("let await = 2").get(1));
  }

  /**
   * The function markers of src/parse/modifiers.psm are keywords only before another word, so a
   * variable called {@code hot} or {@code const} stays a name.
   */
  public void testFunctionMarkersAreKeywordsOnlyBeforeAWord() {
    for (String source : List.of("hot fn f() {}", "noreturn fn f() {}", "noinline fn f() {}",
        "const fn f() {}", "const CELLS = 1", "inline(always) fn f() {}", "inline(never) fn f() {}")) {
      assertEquals(source, PrismioTypes.CONTEXTUAL_KEYWORD, types(source).get(0));
    }
    assertEquals(PrismioTypes.IDENTIFIER, types("let hot = 1").get(1));
    assertEquals(PrismioTypes.IDENTIFIER, types("let const = 1").get(1));
    assertEquals(PrismioTypes.IDENTIFIER, types("hot(1)").get(0));
    assertEquals(PrismioTypes.IDENTIFIER, types("x = noreturn + 1").get(2));
  }

  public void testTypesAreSplitByOrigin() {
    assertEquals(List.of(PrismioTypes.BUILTIN_TYPE), types("Usize"));
    assertEquals(List.of(PrismioTypes.BUILTIN_TYPE), types("String"));
    assertEquals(List.of(PrismioTypes.STDLIB_TYPE), types("List"));
    assertEquals(List.of(PrismioTypes.STDLIB_TYPE), types("Result"));
    // `I32` is another spelling of `Int` (the parser renames it), and the floats
    // and the `Int<N>` constructors are types too.
    assertEquals(List.of(PrismioTypes.BUILTIN_TYPE), types("I32"));
    for (String name : List.of("F16", "F32", "F64", "F128", "BF16", "UInt", "Int")) {
      assertEquals(name, List.of(PrismioTypes.BUILTIN_TYPE), types(name));
    }
  }

  public void testRangeIsNotTwoDotsAndNotAFloat() {
    assertEquals(List.of(PrismioTypes.INTEGER, PrismioTypes.RANGE, PrismioTypes.IDENTIFIER),
        types("0..n"));
    assertEquals(List.of("0", "..", "n"), texts("0..n"));
    // `..<` is one token, as the compiler scans it, not `..` and a comparison.
    assertEquals(List.of("0", "..<", "n"), texts("0..<n"));
    assertEquals(List.of(PrismioTypes.INTEGER, PrismioTypes.RANGE, PrismioTypes.IDENTIFIER), types("0..<n"));
    // A '.' continues a number only when a digit follows it.
    assertEquals(List.of(PrismioTypes.FLOAT), types("3.14"));
    assertEquals(List.of(PrismioTypes.IDENTIFIER, PrismioTypes.DOT, PrismioTypes.IDENTIFIER),
        types("point.x"));
  }

  public void testRadixIntegerLiterals() {
    // Hexadecimal literals
    assertEquals(List.of(PrismioTypes.INTEGER), types("0xFF"));
    assertEquals(List.of("0xFF"), texts("0xFF"));
    assertEquals(List.of(PrismioTypes.INTEGER), types("0xff"));
    assertEquals(List.of("0xff"), texts("0xff"));
    assertEquals(List.of(PrismioTypes.INTEGER), types("0XDEADBEEF"));
    assertEquals(List.of("0XDEADBEEF"), texts("0XDEADBEEF"));

    // Octal literals
    assertEquals(List.of(PrismioTypes.INTEGER), types("0o755"));
    assertEquals(List.of("0o755"), texts("0o755"));
    assertEquals(List.of(PrismioTypes.INTEGER), types("0O755"));
    assertEquals(List.of("0O755"), texts("0O755"));

    // Binary literals
    assertEquals(List.of(PrismioTypes.INTEGER), types("0b1010"));
    assertEquals(List.of("0b1010"), texts("0b1010"));
    assertEquals(List.of(PrismioTypes.INTEGER), types("0B1010"));
    assertEquals(List.of("0B1010"), texts("0B1010"));

    // Leading zero is decimal, not octal
    assertEquals(List.of(PrismioTypes.INTEGER), types("010"));
    assertEquals(List.of("010"), texts("010"));

    // Without following digits, prefix stays number 0 followed by identifier
    assertEquals(List.of(PrismioTypes.INTEGER, PrismioTypes.IDENTIFIER), types("0x"));
    assertEquals(List.of("0", "x"), texts("0x"));
    assertEquals(List.of(PrismioTypes.INTEGER, PrismioTypes.IDENTIFIER), types("0o"));
    assertEquals(List.of("0", "o"), texts("0o"));
    assertEquals(List.of(PrismioTypes.INTEGER, PrismioTypes.IDENTIFIER), types("0b"));
    assertEquals(List.of("0", "b"), texts("0b"));
  }

  public void testDigitSeparators() {
    assertEquals(List.of(PrismioTypes.INTEGER), types("1_000_000"));
    assertEquals(List.of("1_00_000"), texts("1_00_000"));
    assertEquals(List.of("0xFF_FF"), texts("0xFF_FF"));
    assertEquals(List.of("0b1010_0001"), texts("0b1010_0001"));
    assertEquals(List.of(PrismioTypes.FLOAT), types("3.141_592e1_0"));
    assertEquals(List.of("3.141_592e1_0"), texts("3.141_592e1_0"));

    // A separator with no digit after it ends the number; the compiler refuses it.
    assertEquals(List.of("1", "_"), texts("1_"));
    assertEquals(List.of("0", "x_FF"), texts("0x_FF"));
  }

  public void testScientificNotation() {
    assertEquals(List.of(PrismioTypes.FLOAT), types("1e9"));
    assertEquals(List.of("1e9"), texts("1e9"));
    assertEquals(List.of(PrismioTypes.FLOAT), types("1.5e-3"));
    assertEquals(List.of("1.5e-3"), texts("1.5e-3"));
    assertEquals(List.of(PrismioTypes.FLOAT), types("6.022e23"));
    assertEquals(List.of("6.022e23"), texts("6.022e23"));

    // Exponent without following digit stays number followed by identifier
    assertEquals(List.of(PrismioTypes.INTEGER, PrismioTypes.IDENTIFIER), types("1e"));
    assertEquals(List.of("1", "e"), texts("1e"));
  }

  public void testBlockCommentsNest() {
    assertEquals(List.of(PrismioTypes.BLOCK_COMMENT), types("/* outer /* inner */ still */"));
    assertEquals(List.of(PrismioTypes.BLOCK_COMMENT, PrismioTypes.KEYWORD),
        types("/* a /* b */ c */ return"));
    // One close is not enough to end a nested comment: the rest of the file is
    // still comment, exactly as the compiler reads it.
    assertEquals(List.of(PrismioTypes.BLOCK_COMMENT), types("/* a /* b */"));
  }

  public void testAnyNumberOfStarsIsStillOneComment() {
    // The depth counter reacts to `/*` and `*/` and to nothing else, so extra
    // stars are ordinary content. Each of these is one token and nothing leaks
    // out of it.
    assertEquals(List.of(PrismioTypes.DOC_COMMENT), types("/**\n\n*/"));
    assertEquals(List.of(PrismioTypes.DOC_COMMENT), types("/** on one line */"));
    assertEquals(List.of(PrismioTypes.DOC_COMMENT), types("/**\n * shaped like javadoc\n */"));

    // Empty ones are not documentation: there is nothing in them to render.
    assertEquals(List.of(PrismioTypes.BLOCK_COMMENT), types("/**/"));
    assertEquals(List.of(PrismioTypes.BLOCK_COMMENT), types("/***/"));
    assertEquals(List.of(PrismioTypes.BLOCK_COMMENT), types("/**** banner ****/"));

    // And they really close -- the trailing `/` must not leak into the code.
    assertEquals(List.of(PrismioTypes.INTEGER, PrismioTypes.BLOCK_COMMENT,
        PrismioTypes.ARITHMETIC_OP, PrismioTypes.INTEGER), types("1 /**/ + 1"));
    assertEquals(List.of(PrismioTypes.INTEGER, PrismioTypes.BLOCK_COMMENT,
        PrismioTypes.ARITHMETIC_OP, PrismioTypes.INTEGER), types("1 /***/ + 1"));
  }

  public void testLineAndDocComments() {
    assertEquals(List.of(PrismioTypes.LINE_COMMENT), types("// ordinary"));
    assertEquals(List.of(PrismioTypes.DOC_COMMENT), types("/// documentation"));
    assertEquals(List.of(PrismioTypes.LINE_COMMENT), types("//// a rule, not documentation"));
  }

  public void testStringsAndCharactersStopAtTheLineEnd() {
    assertEquals(List.of(PrismioTypes.STRING_LITERAL), types("\"escaped \\\" quote\""));
    assertEquals(List.of(PrismioTypes.CHARACTER_LITERAL), types("'p'"));
    assertEquals(List.of(PrismioTypes.CHARACTER_LITERAL), types("'\\n'"));
    // An unterminated literal must not swallow the rest of the file.
    assertEquals(List.of(PrismioTypes.STRING_LITERAL, PrismioTypes.KEYWORD),
        types("\"unterminated\nreturn"));
  }

  public void testTripleQuotedStrings() {
    assertEquals(List.of(PrismioTypes.STRING_LITERAL), types("\"\"\"\"\"\""));
    assertEquals(List.of(PrismioTypes.STRING_LITERAL), types("\"\"\"hello world\"\"\""));
    assertEquals(List.of(PrismioTypes.STRING_LITERAL), types("\"\"\"multiline\nstring\nacross\nlines\"\"\""));
    assertEquals(List.of(PrismioTypes.STRING_LITERAL), types("\"\"\"with \"single\" and \"\"double\"\" quotes\"\"\""));
    assertEquals(List.of(PrismioTypes.STRING_LITERAL), types("\"\"\"unterminated triple quoted"));
  }

  public void testOperatorsAreClassified() {
    assertEquals(List.of(PrismioTypes.ARROW), types("->"));
    assertEquals(List.of(PrismioTypes.FAT_ARROW), types("=>"));
    assertEquals(List.of(PrismioTypes.RELATIONAL_OP), types("!="));
    assertEquals(List.of(PrismioTypes.SHIFT_OP), types(">>"));
    assertEquals(List.of(PrismioTypes.ASSIGNMENT_OP), types("+="));
    assertEquals(List.of(PrismioTypes.ASSIGNMENT_OP), types("="));
    assertEquals(List.of(PrismioTypes.NEGATION), types("!"));
  }

  public void testNoneCoalescingIsOneOperator() {
    // `??` is one token, as the compiler scans it, and a lone `?` stays the
    // optional suffix: `Int? ?? 0` is a type suffix and then the operator.
    assertEquals(List.of(PrismioTypes.IDENTIFIER, PrismioTypes.ARITHMETIC_OP, PrismioTypes.INTEGER),
        types("a ?? 0"));
    assertEquals(List.of("a", "??", "0"), texts("a ?? 0"));
    assertEquals(List.of("a", "??", "b", "??", "0"), texts("a??b??0"));
    assertEquals(List.of("Int", "?", "=", "none"), texts("Int?=none"));
  }

  public void testTildeAndQuestionMark() {
    // Both were bad characters until the corpus test lexed a real checkout:
    // `~` is bitwise NOT and `?` is the nullable-type suffix.
    assertEquals(List.of(PrismioTypes.BITWISE_OP, PrismioTypes.IDENTIFIER), types("~mask"));
    assertEquals(List.of(PrismioTypes.BUILTIN_TYPE, PrismioTypes.OPTIONAL), types("Int?"));
    // `~=` is not a compound assignment; there is no compound bitwise NOT.
    assertEquals(List.of(PrismioTypes.BITWISE_OP, PrismioTypes.ASSIGNMENT_OP), types("~="));
  }

  public void testSemicolonIsRejected() {
    // `;` is in neither isSeparator nor isOperator, so `let x = 1;` does not
    // compile. The editor has to agree, or it hides a build failure.
    assertEquals(List.of(PrismioTypes.INTEGER, TokenType.BAD_CHARACTER), types("1;"));
  }

  public void testLexerCoversEveryByteOfARealisticFile() {
    assertCoversInput("""
        // A file exercising most of the grammar.
        /* including /* nested */ comments */
        import std.io

        extern fn read_file(path: String borrow) -> String produce(free)

        public fn main() -> Int {
            let mut total = 0
            for i in 0..10 {
                if (i > 3 and i != 7) { total += i }
            }
            let text = "a \\"quoted\\" word"
            let c = 'x'
            let flags = ~0
            let maybe: Int? = none
            match total { 0 => return 1, _ => return 0 }
        }
        """);
  }
}
