package io.prismio;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Reformatting must not change what a file means.
 *
 * <p>That is the whole bar for a formatter, and it is easy to miss with a
 * spacing table: a rule written for one token pair silently applies to every
 * pair that matches it first. `and` and `or` are keywords used as infix
 * operators, so a blanket "no space after an identifier" rule turned
 * `a and b` into `aand b`.
 */
public class PrismioFormatterTest extends BasePlatformTestCase {

  private String reformat(String source) {
    PsiFile file = myFixture.configureByText(PrismioFileType.INSTANCE, source);
    WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () ->
        CodeStyleManager.getInstance(getProject()).reformat(file));
    return file.getText();
  }

  /** Reformatting is idempotent and preserves every token, in order. */
  private void assertTokensPreserved(String source) {
    String formatted = reformat(source);
    assertEquals("reformatting must not change the token stream",
        tokenText(source), tokenText(formatted));
  }

  private static String tokenText(String source) {
    io.prismio.lexer.PrismioLexer lexer = new io.prismio.lexer.PrismioLexer();
    lexer.start(source, 0, source.length(), 0);
    StringBuilder out = new StringBuilder();
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() != com.intellij.psi.TokenType.WHITE_SPACE) {
        out.append(source, lexer.getTokenStart(), lexer.getTokenEnd()).append('');
      }
      lexer.advance();
    }
    return out.toString();
  }

  public void testWordOperatorsKeepTheirSpaces() {
    assertEquals("let ok = a and b", reformat("let ok = a and b"));
    assertEquals("let ok = a or b", reformat("let ok = a or b"));
    assertEquals("let ok = a and b or c", reformat("let ok = a and b or c"));
  }

  /** Every keyword that can follow an expression, not just the two operators. */
  public void testKeywordsAfterAnIdentifierKeepTheirSpaces() {
    assertEquals("let n = value as Int", reformat("let n = value as Int"));
    assertEquals("for item in items { }", reformat("for item in items { }"));
  }

  /** `default` is a keyword, and also the name of a type's own function. */
  public void testDefaultAsAFunctionNameIsACall() {
    assertEquals("let c = Config.default()", reformat("let c = Config.default()"));
    assertEquals("let t = T.default()", reformat("let t = T.default()"));
    assertEquals("fn default() -> Self { }", reformat("fn default() -> Self { }"));
    assertEquals("let c: Config = default", reformat("let c: Config = default"));
  }

  public void testLoopLabelsHugTheirName() {
    assertEquals("outer@ for i in 0..<n { break@outer }", reformat("outer@ for i in 0..<n { break@outer }"));
    assertEquals("outer@ while (x) { continue@outer }", reformat("outer@ while (x) { continue@outer }"));
    assertEquals("top@ repeat(5) { }", reformat("top@ repeat(5) { }"));
  }

  public void testRangesStepAndRepeat() {
    assertEquals("for i in 10..0 step 2 { }", reformat("for i in 10..0 step 2 { }"));
    assertEquals("repeat(3) { }", reformat("repeat(3) { }"));
    assertEquals("let s = \"ab\".repeat(3)", reformat("let s = \"ab\".repeat(3)"));
    assertEquals("import {io, string} from std", reformat("import {io, string} from std"));
  }

  /** Each of these was a change Reformat Code made to the compiler's own source. */
  public void testOperandsKeepTheirSpaceBeforeABracket() {
    assertEquals("let p = power * (2 as U64)", reformat("let p = power * (2 as U64)"));
    assertEquals("let v = [1, 2]", reformat("let v = [1, 2]"));
    assertEquals("fn f() -> [Int] { }", reformat("fn f() -> [Int] { }"));
    assertEquals("let xs = listOf<Int>(1, 2)", reformat("let xs = listOf<Int>(1, 2)"));
    assertEquals("let x = xs[i] + f(y)", reformat("let x = xs[i] + f(y)"));
  }

  public void testASignHugsItsOperand() {
    assertEquals("f(-1, 0)", reformat("f(-1, 0)"));
    assertEquals("let x = -y", reformat("let x = -y"));
    assertEquals("return -1", reformat("return -1"));
    assertEquals("let d = a - b", reformat("let d = a - b"));
    assertEquals("let d = f(a) - 1", reformat("let d = f(a) - 1"));
  }

  public void testAWordBeforePunctuationClosesUp() {
    assertEquals("extern fn f(name: String borrow) -> Int", reformat("extern fn f(name: String borrow) -> Int"));
    assertEquals("f(current.type, none)", reformat("f(current.type, none)"));
    assertEquals("struct S { type: Int }", reformat("struct S { type: Int }"));
  }

  public void testContinuationLinesKeepTheirIndentation() {
    String condition = "fn f() {\n    if (a\n        or b) {\n        g()\n    }\n}";
    assertEquals(condition, reformat(condition));
    String aligned = "fn f() {\n    let s = join(a,\n                 b)\n}";
    assertEquals(aligned, reformat(aligned));
    // Too shallow to read as a continuation: moved one step inside its block.
    assertEquals("fn f() {\n    let s = a +\n        b\n}", reformat("fn f() {\n    let s = a +\nb\n}"));
  }

  public void testLinesThatAreNotContinuations() {
    assertEquals("import ir.*\nimport std.io", reformat("import ir.*\nimport std.io"));
    String sum = "fn f() -> Int {\n    return a\n        + b\n}";
    assertEquals(sum, reformat("fn f() -> Int {\n    return a\n+ b\n}"));
    String literal = "fn f() -> P {\n    return P { x: 1,\n               y: 2 }\n}";
    assertEquals(literal, reformat(literal));
    String variants = "enum E {\n    A,\n    B\n}";
    assertEquals(variants, reformat(variants));
  }

  public void testBlocksInsideBracketsAreBlocks() {
    String closure = "fn f() {\n    xs.sortBy(fn(a: Int, b: Int) -> Bool {\n        return a < b\n    })\n}";
    assertEquals(closure, reformat(closure));
    String fields = "struct S {\n    items: Vec<Int>\n}";
    assertEquals(fields, reformat(fields));
  }

  public void testGenericArgumentsAreNotSpacedApart() {
    // `<` and `>` lex as relational operators, so a blanket "space around
    // relational" rule turns a type argument list into a comparison.
    assertEquals("let items: List<Int> = list_new()",
        reformat("let items: List<Int> = list_new()"));
    assertEquals("fn first<T>(values: List<T>) -> T { }",
        reformat("fn first<T>(values: List<T>) -> T { }"));
  }

  public void testAnIntegerWidthIsATypeArgument() {
    // `Int<24>` is a type, not `Int < 24 >`; `a < 24` stays a comparison.
    assertEquals("let a: Int<24> = 5", reformat("let a: Int<24> = 5"));
    assertEquals("let b: UInt<5> = 5", reformat("let b: UInt<5> = 5"));
    assertEquals("let v: Vec<Int<24>> = []", reformat("let v: Vec<Int<24>> = []"));
    assertEquals("fn pack(p: UInt<5>) -> UInt<16> { }", reformat("fn pack(p: UInt<5>) -> UInt<16> { }"));
    assertEquals("if (a < 24 and b > 3) { }", reformat("if (a<24 and b>3) { }"));
  }

  public void testAnArrayLengthIsATypeArgument() {
    // benchmarks/prismio/compute.psm came back as `Array < U32, 64 >`, with the
    // loop under it indented as though the `>` had left the line unfinished.
    String sha = "fn f() {\n    let mut w: Array<U32, 64>\n\n    for _ in 0..<n {\n        g()\n    }\n}";
    assertEquals(sha, reformat(sha));
    assertEquals("let grid: Array<Array<U8, 4>, 4> = default",
        reformat("let grid: Array<Array<U8, 4>, 4> = default"));
    assertEquals("if (a < 5 and b > 3) { }", reformat("if (a<5 and b>3) { }"));
  }

  public void testBitwiseNotHugsItsOperand() {
    assertEquals("let ch = (e & f) ^ ((~e) & g)", reformat("let ch = (e & f) ^ ((~e) & g)"));
    assertEquals("let m = a & ~b", reformat("let m = a & ~ b"));
  }

  public void testComparisonsStillGetSpaces() {
    assertEquals("if (a < b) { }", reformat("if (a<b) { }"));
    assertEquals("if (a >= b) { }", reformat("if (a>=b) { }"));
  }

  public void testIndexingAndOptionalStayTight() {
    assertEquals("let x = values[0]", reformat("let x = values[0]"));
    assertEquals("let maybe: Int? = none", reformat("let maybe: Int? = none"));
  }

  public void testNoneCoalescingGetsSpaces() {
    assertEquals("let n = a ?? 0", reformat("let n = a??0"));
    assertEquals("let n = a ?? b ?? 0", reformat("let n = a ?? b??0"));
  }

  public void testCallsAndFieldAccessStayTight() {
    assertEquals("let n = point.x", reformat("let n = point.x"));
    assertEquals("println(value)", reformat("println(value)"));
    assertEquals("let n = strFromInt(point.x)", reformat("let n = strFromInt(point.x)"));
  }

  public void testRangeStaysTight() {
    assertEquals("for i in 0..10 { }", reformat("for i in 0..10 { }"));
  }

  /**
   * Reformatting every real source file in a checkout must preserve its tokens.
   *
   * <p>This is the property Cmd+Option+L is judged on, and the one that was
   * broken: a formatter that changes the token stream changes the program.
   * Skipped without `-Dprismio.checkout`.
   */
  public void testReformattingACheckoutPreservesEveryToken() throws Exception {
    String checkout = System.getProperty("prismio.checkout");
    if (checkout == null || !java.nio.file.Files.isDirectory(java.nio.file.Path.of(checkout))) {
      return;
    }

    java.util.List<java.nio.file.Path> sources;
    try (var walk = java.nio.file.Files.walk(java.nio.file.Path.of(checkout))) {
      sources = walk.filter(p -> p.toString().endsWith(".psm"))
          .filter(p -> !p.getFileName().toString().startsWith("neg_"))
          .sorted()
          .toList();
    }

    java.util.List<String> changed = new java.util.ArrayList<>();
    for (java.nio.file.Path source : sources) {
      String text = java.nio.file.Files.readString(source);
      String formatted = reformat(text);
      if (!tokenText(text).equals(tokenText(formatted))) {
        changed.add(source.getFileName().toString());
      }
    }

    assertTrue("reformatting changed the token stream of " + changed.size() + " file(s): "
        + changed.subList(0, Math.min(changed.size(), 10)), changed.isEmpty());
    assertTrue("the checkout should contain sources", !sources.isEmpty());
  }

  public void testReformattingARealisticFilePreservesEveryToken() {
    assertTokensPreserved("""
        import std.io

        struct Point { x: Int, y: Int }

        fn classify<T: Ord>(values: List<T>, limit: Int) -> Bool {
            let mut total = 0
            for i in 0..10 {
                if (i > 3 and i != 7 or total < limit) {
                    total += i
                }
            }
            let maybe: Int? = none
            return total >= limit
        }
        """);
  }

  /** `Ptr<mut T>` is a type argument list, though `mut` is a keyword: it must not become `Ptr < mut T >`. */
  public void testAKeywordInsideTypeArgumentsStillClosesUp() {
    assertEquals("fn at(p: Ptr<mut T>) { }", reformat("fn at(p: Ptr<mut T>) { }"));
    assertEquals("let one = Ptr<mut T>.atUnchecked(4096)", reformat("let one = Ptr<mut T>.atUnchecked(4096)"));
    assertEquals("fn f<T>(block: Ptr<mut T>, count: Int) -> Ptr<mut T> { }",
        reformat("fn f<T>(block: Ptr<mut T>, count: Int) -> Ptr<mut T> { }"));
  }

  /** An `if` with an `else` is written on several lines; one with none may stay on one. */
  public void testIfWithElseIsSplitOverLines() {
    assertEquals("""
        fn f() {
            if (a) {
                b()
            } else {
                c()
            }
        }""", reformat("fn f() {\n    if (a) { b() } else { c() }\n}"));
  }

  /** The line from `std/io.psm` that was reported: one-line if/else with calls in both branches. */
  public void testTheReportedOneLineIfElse() {
    assertEquals("""
        fn emit() {
            if (value == none) {
                prismioStdIoEmitNone(fd, terminator)
            } else {
                prismioStdIoEmit(fd, expect(value), terminator)
            }
        }""", reformat("fn emit() {\n    if (value == none) { prismioStdIoEmitNone(fd, terminator) } else { prismioStdIoEmit(fd, expect(value), terminator) }\n}"));
  }

  public void testEveryBranchOfAnElseIfChainIsSplit() {
    assertEquals("""
        fn f() {
            if (a) {
                b()
            } else if (c) {
                d()
            } else {
                e()
            }
        }""", reformat("fn f() {\n    if (a) { b() } else if (c) { d() } else { e() }\n}"));
    // Ending on an `else if` is still a chain with an else.
    assertEquals("""
        fn f() {
            if (a) {
                b()
            } else if (c) {
                d()
            }
        }""", reformat("fn f() {\n    if (a) { b() } else if (c) { d() }\n}"));
  }

  public void testALoneIfKeepsItsLines() {
    assertEquals("fn f() {\n    if (a) { b() }\n}", reformat("fn f() {\n    if (a) { b() }\n}"));
    assertEquals("fn f() {\n    if (a) {\n        b()\n    }\n}",
        reformat("fn f() {\n    if (a) {\n        b()\n    }\n}"));
  }

  public void testEmptyBranchesStayClosed() {
    assertEquals("fn f() {\n    if (a) {\n        b()\n    } else { }\n}",
        reformat("fn f() {\n    if (a) { b() } else { }\n}"));
  }

  public void testSplitIfElseIsIdempotentAndKeepsItsTokens() {
    String source = "fn f() {\n    if (a) { b() } else { if (c) { d() } else { e() } }\n}";
    assertTokensPreserved(source);
    String once = reformat(source);
    assertEquals(once, reformat(once));
  }

  public void testIfElseSplitInsideANestedBlockIndentsFromItsOwnLevel() {
    assertEquals("""
        fn f() {
            for i in 0..3 {
                if (a) {
                    b()
                } else {
                    c()
                }
            }
        }""", reformat("fn f() {\n    for i in 0..3 {\n        if (a) { b() } else { c() }\n    }\n}"));
  }
}
