package io.prismio.symbols;

import junit.framework.TestCase;

/**
 * The words that stand before {@code fn} and {@code struct} in a program with no
 * operating system under it ({@code src/parse/decl.psm}) must not hide the
 * declaration they modify, and must not turn a variable that happens to share
 * their name into one.
 */
public class FreestandingDeclarationsTest extends TestCase {

  private static ModuleSummary parse(String source) {
    return SignatureParser.parse(source, "kernel");
  }

  public void testModifiersBeforeFnLeaveTheFunctionVisible() {
    ModuleSummary summary = parse("""
        export naked section(".text.boot") align(4) fn _start() {
            __builtin_asm("nop")
        }

        public export fn hook(kind: Ptr, line: Int) -> Int {
            return line
        }

        cold export fn halt() {
        }
        """);
    assertEquals(3, summary.functions().size());
    assertEquals("_start", summary.functions().get(0).name());
    assertEquals("hook", summary.functions().get(1).name());
    assertEquals("halt", summary.functions().get(2).name());
  }

  public void testReprAndPackedBeforeStructLeaveTheTypeVisible() {
    ModuleSummary summary = parse("""
        repr(C) struct Gate {
            flags: U8,
            offset: U32,
        }

        packed struct Wire {
            tag: U8,
        }

        repr(C) packed struct Both {
            tag: U8,
        }
        """);
    assertEquals(3, summary.types().size());
    assertEquals("Gate", summary.types().get(0).name());
    assertEquals("Wire", summary.types().get(1).name());
    assertEquals("Both", summary.types().get(2).name());
  }

  public void testAVariableNamedLikeAModifierIsStillAVariable() {
    ModuleSummary summary = parse("""
        let export = 5
        let packed = 6
        fn after() -> Int {
            return 1
        }
        """);
    assertEquals(1, summary.functions().size());
    assertEquals("after", summary.functions().get(0).name());
    assertEquals(2, summary.globals().size());
  }

  /**
   * The function markers of src/parse/modifiers.psm -- including the ones that take an argument
   * ({@code inline(always)}) -- and {@code const} declarations leave what they modify visible.
   */
  public void testFunctionMarkersAndConstDeclarations() {
    ModuleSummary summary = parse("""
        inline(always) fn a() {}
        hot noreturn fn b() {}
        public const fn c() -> Int { return 1 }
        noinline cold fn d() {}
        const CELLS = 64
        public const LIMIT: U8 = 200
        const hot = 3
        fn after() {}
        """);
    assertEquals(java.util.List.of("a", "b", "c", "d", "after"),
        summary.functions().stream().map(f -> f.name()).toList());
    assertEquals(java.util.List.of("CELLS", "LIMIT", "hot"),
        summary.globals().stream().map(g -> g.name()).toList());
  }
}
