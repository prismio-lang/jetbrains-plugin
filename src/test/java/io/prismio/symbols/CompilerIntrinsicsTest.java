package io.prismio.symbols;

import java.nio.file.Path;
import java.util.Set;
import junit.framework.TestCase;

/** The lowered-member probe against a real compiler ({@code -Dprismio.compiler}); skips without one. */
public class CompilerIntrinsicsTest extends TestCase {

  public void testProbeKeepsWhatTheCompilerAccepts() {
    String compiler = System.getProperty("prismio.compiler");
    if (compiler == null) {
      return;
    }
    Set<String> rejected = CompilerIntrinsics.probe(Path.of(compiler));
    System.out.println(compiler + " rejects " + rejected);
    // Every compiler since the Vec methods landed lowers these.
    assertFalse(rejected.contains("Vec.push"));
    assertFalse(rejected.contains("Vec.length"));
    // A scalar `T?` lowers `unwrapOr` and `expect`; a compiler from before scalar optionals
    // refuses the probe's `let r: Int? = none` and so both.
    String expectOptionals = System.getProperty("prismio.expectOptionals");
    if (expectOptionals != null) {
      boolean current = Boolean.parseBoolean(expectOptionals);
      assertEquals(current, !rejected.contains("Int?.unwrapOr"));
      assertEquals(current, !rejected.contains("Float?.expect"));
    }
    // Not vacuous: a compiler that predates typed channels must be seen to refuse them.
    String expectChannel = System.getProperty("prismio.expectChannel");
    if (expectChannel != null) {
      boolean current = Boolean.parseBoolean(expectChannel);
      assertEquals(current, !rejected.contains("Channel.send"));
      // `replace` landed with the same generation; before it the name fell through to String's.
      assertEquals(current, !rejected.contains("Vec.replace"));
    }
  }

  public void testWithoutACompilerEverythingIsOffered() {
    assertEquals(Set.of("Vec", "Array", "Channel", "Ptr"), CompilerIntrinsics.owners(null));
  }

  /** An {@code Int?} is offered its own members, and an {@code Int} none of them. */
  public void testAnOptionalHasItsOwnMembers() {
    SymbolTable.Scope scope = new SymbolTable.Scope(java.util.List.of(CompilerIntrinsics.table(null)));
    java.util.List<String> optional = scope.instanceMembers(TypeRef.parse("Int?")).stream()
        .map(Member::name).toList();
    assertTrue(optional.contains("unwrapOr"));
    assertTrue(optional.contains("expect"));
    java.util.List<String> plain = scope.instanceMembers(TypeRef.parse("Int")).stream()
        .map(Member::name).toList();
    assertFalse(plain.contains("unwrapOr"));
    assertEquals("Int?", TypeRef.parse("Int?").memberKey());
    assertEquals("Vec", TypeRef.parse("List<Int>").memberKey());
    // `[T]` is an array, not a Vec: they have different members.
    assertEquals("Array", TypeRef.parse("[Int]").memberKey());
    assertEquals("Array", TypeRef.parse("Array<Int, 4>").memberKey());
  }

  /** An array has its length and four searches, and none of a Vec's growing members. */
  public void testAnArrayIsNotOfferedAVecsMembers() {
    SymbolTable.Scope scope = new SymbolTable.Scope(java.util.List.of(CompilerIntrinsics.table(null)));
    java.util.List<String> array = scope.instanceMembers(TypeRef.parse("Array<Int, 4>")).stream()
        .map(Member::name).toList();
    assertTrue(array.containsAll(java.util.List.of("length", "contains", "indexOf", "lastIndexOf", "countOf")));
    assertFalse(array.contains("push"));
    assertFalse(array.contains("isEmpty"));
    java.util.List<String> vec = scope.instanceMembers(TypeRef.parse("Vec<Int>")).stream()
        .map(Member::name).toList();
    assertTrue(vec.contains("push"));
    assertFalse(vec.contains("lastIndexOf"));
  }
}
