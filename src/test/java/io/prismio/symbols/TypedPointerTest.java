package io.prismio.symbols;

import java.util.List;
import junit.framework.TestCase;

/**
 * {@code Ptr<T>} with its capabilities before the pointee ({@code Ptr<mut T>},
 * {@code Ptr<volatile T>}, {@code Ptr<mut volatile T>}, {@code Ptr<T>?}), docs/PTR_PLAN.md:
 * the signature parser reads them, a field read through one is typed as the field, and
 * completion offers the pointee's fields, or {@code load} for a scalar.
 */
public class TypedPointerTest extends TestCase {

  private static final String DECLARATIONS = """
      repr(C) struct Header {
          magic: U32,
          length: U32,
          flags: U16,
      }

      repr(C) struct Packet {
          header: Header,
          tag: U8,
      }

      struct Node {
          value: Int,
          next: Ptr<Node>?,
      }

      enum Mode { Idle, Busy }

      """;

  private static SymbolTable.Scope scopeOf(String source) {
    SymbolTable table = new SymbolTable();
    table.add(SignatureParser.parse(DECLARATIONS + source, "app"), SymbolTable.ALL);
    return new SymbolTable.Scope(List.of(table, CompilerIntrinsics.table(null)));
  }

  /** What {@code expression.} is the receiver of, inside the parameters {@code params}. */
  private static TypeRef receiver(String params, String expression) {
    String source = DECLARATIONS + "fn f(" + params + ") {\n    " + expression + ".\n}\n";
    SymbolTable.Scope scope = scopeOf("fn f(" + params + ") {\n    " + expression + ".\n}\n");
    TypeInference.Receiver receiver = TypeInference.receiverAt(source,
        source.indexOf(expression + ".") + expression.length() + 1, scope, List.of());
    return receiver == null ? null : receiver.type();
  }

  private static List<String> names(SymbolTable.Scope scope, TypeRef type) {
    return scope.instanceMembers(type).stream().map(Member::name).toList();
  }

  // ------------------------------------------------------------------ TypeRef

  public void testCapabilitiesAreReadAndPrintedInOrder() {
    assertEquals("Ptr<Header>", TypeRef.parse("Ptr<Header>").toString());
    assertEquals("Ptr<mut Header>", TypeRef.parse("Ptr<mut Header>").toString());
    assertEquals("Ptr<volatile Header>", TypeRef.parse("Ptr<volatile Header>").toString());
    assertEquals("Ptr<mut volatile Header>", TypeRef.parse("Ptr<mut volatile Header>").toString());
    // Either order is written; the compiler prints `mut` first.
    assertEquals("Ptr<mut volatile Header>", TypeRef.parse("Ptr<volatile mut Header>").toString());
    assertEquals("Ptr<mut Header>?", TypeRef.parse("Ptr<mut Header>?").toString());

    TypeRef pointer = TypeRef.parse("Ptr<mut volatile U32>");
    assertTrue(pointer.isTypedPointer());
    assertTrue(pointer.isMutablePointer());
    assertTrue(pointer.isVolatilePointer());
    assertEquals("U32", pointer.pointee().toString());
    assertFalse(TypeRef.parse("Ptr<Header>").isMutablePointer());
    assertFalse(TypeRef.simple("Ptr").isTypedPointer());
    assertEquals("Ptr", TypeRef.parse("Ptr<mut Header>").memberKey());
    assertEquals("Ptr?", TypeRef.parse("Ptr<Header>?").memberKey());
  }

  public void testPointerToPointerAndPointerInsideOtherTypes() {
    TypeRef nested = TypeRef.parse("Ptr<Ptr<mut Int>>");
    assertEquals("Ptr<Ptr<mut Int>>", nested.toString());
    assertTrue(nested.pointee().isMutablePointer());
    assertEquals("Vec<Ptr<volatile Node>>", TypeRef.parse("Vec<Ptr<volatile Node>>").toString());
  }

  // ---------------------------------------------------------------- signatures

  public void testSignatureParserKeepsTheRestOfADeclarationAfterACapability() {
    ModuleSummary summary = SignatureParser.parse("""
        fn verify(p: Ptr<mut Header>, q: Ptr<volatile mut U32>, r: Ptr<Node>?, n: Int) -> Ptr<volatile Header>? {
            return none
        }

        fn after() -> Int {
            return 1
        }
        """, "app");
    assertEquals(2, summary.functions().size());
    FnSig verify = summary.functions().get(0);
    assertEquals(4, verify.params().size());
    assertEquals("Ptr<mut Header>", verify.params().get(0).type().toString());
    assertEquals("Ptr<mut volatile U32>", verify.params().get(1).type().toString());
    assertEquals("Ptr<Node>?", verify.params().get(2).type().toString());
    assertEquals("Int", verify.params().get(3).type().toString());
    assertEquals("Ptr<volatile Header>?", verify.returnType().toString());
    assertEquals("after", summary.functions().get(1).name());
  }

  public void testAStructMayHoldAPointerToItself() {
    TypeDecl node = scopeOf("").type("Node");
    assertEquals(2, node.fields().size());
    assertEquals("Ptr<Node>?", node.fields().get(1).type().toString());
  }

  public void testVolatileAsANameIsStillAType() {
    // Outside a `Ptr<...>` list `volatile` is a name, as the compiler reads it.
    ModuleSummary summary = SignatureParser.parse("fn f(volatile: Int, x: Vec<volatile>) {}", "app");
    assertEquals("volatile", summary.functions().get(0).params().get(0).name());
    assertEquals("Vec<volatile>", summary.functions().get(0).params().get(1).type().toString());
  }

  // ---------------------------------------------------------------- members

  public void testAStructPointeeOffersItsFieldsWithTheirOwnTypes() {
    SymbolTable.Scope scope = scopeOf("");
    for (String written : List.of("Ptr<Header>", "Ptr<mut Header>", "Ptr<volatile Header>",
        "Ptr<mut volatile Header>")) {
      assertTrue(written, names(scope, TypeRef.parse(written)).containsAll(List.of("magic", "length", "flags")));
    }
    TypeRef magic = scope.instanceMembers(TypeRef.parse("Ptr<mut Header>")).stream()
        .filter(m -> m.name().equals("magic")).findFirst().orElseThrow().type();
    assertEquals("U32", magic.toString());
  }

  public void testAScalarPointeeOffersLoadAndNoFields() {
    SymbolTable.Scope scope = scopeOf("");
    Member load = scope.instanceMembers(TypeRef.parse("Ptr<mut Int>")).stream()
        .filter(m -> m.name().equals("load")).findFirst().orElseThrow();
    assertEquals("Int", load.type().toString());
    assertEquals("U8", scope.instanceMembers(TypeRef.parse("Ptr<U8>")).stream()
        .filter(m -> m.name().equals("load")).findFirst().orElseThrow().type().toString());
    // A struct is read field by field; `p.load()` on one is the compiler's refusal.
    assertFalse(names(scope, TypeRef.parse("Ptr<Header>")).contains("load"));
    // And a scalar has no struct's fields.
    assertFalse(names(scope, TypeRef.parse("Ptr<Int>")).contains("magic"));
  }

  /** `store` writes a scalar through a `mut` pointer; a read-only pointer is refused by the compiler. */
  public void testStoreNeedsAMutPointerToAScalar() {
    SymbolTable.Scope scope = scopeOf("");
    assertTrue(names(scope, TypeRef.parse("Ptr<mut Int>")).contains("store"));
    assertTrue(names(scope, TypeRef.parse("Ptr<mut volatile U8>")).contains("store"));
    assertFalse(names(scope, TypeRef.parse("Ptr<Int>")).contains("store"));
    assertFalse(names(scope, TypeRef.parse("Ptr<mut Header>")).contains("store"));
    assertFalse(names(scope, TypeRef.simple("Ptr")).contains("store"));
    Member store = scope.instanceMembers(TypeRef.parse("Ptr<mut U16>")).stream()
        .filter(m -> m.name().equals("store")).findFirst().orElseThrow();
    assertEquals("value: U16", store.function().params().get(0).toString());
    assertNull(store.type());
  }

  /** Stage 4: the address members belong to every typed pointer, and answer the receiver's own type. */
  public void testAddressMembersOfAnyTypedPointer() {
    SymbolTable.Scope scope = scopeOf("");
    List<String> all = List.of("addr", "offset", "byteOffset", "wrappingOffset", "offsetFrom",
        "isAligned", "sameAs", "advance");
    for (String written : List.of("Ptr<Header>", "Ptr<mut Int>", "Ptr<volatile U8>")) {
      assertTrue(written, names(scope, TypeRef.parse(written)).containsAll(all));
    }
    // An opaque `Ptr` and an optional one have no pointee to step over.
    assertFalse(names(scope, TypeRef.simple("Ptr")).contains("offset"));
    assertFalse(names(scope, TypeRef.parse("Ptr<Int>?")).contains("offset"));
    // No constructor is an instance member.
    assertFalse(names(scope, TypeRef.parse("Ptr<Int>")).contains("atUnchecked"));
  }

  public void testAddressMembersAnswerTheTypesTheCompilerGives() {
    assertEquals("Usize", receiver("p: Ptr<mut Header>", "p.addr").toString());
    assertEquals("Ptr<mut Header>", receiver("p: Ptr<mut Header>", "p.offset(1)").toString());
    assertEquals("Ptr<mut volatile Int>",
        receiver("p: Ptr<volatile mut Int>", "p.byteOffset(4)").toString());
    assertEquals("Ptr<Header>", receiver("p: Ptr<Header>", "p.wrappingOffset(2)").toString());
    assertEquals("Int", receiver("p: Ptr<Header>", "p.offsetFrom(p)").toString());
    assertEquals("Bool", receiver("p: Ptr<Header>", "p.isAligned(4)").toString());
    assertEquals("Bool", receiver("p: Ptr<Header>", "p.sameAs(p)").toString());
    assertEquals("Ptr<mut Header>?", receiver("p: Ptr<mut Header>", "p.advance(1, p)").toString());
    // Stepping keeps the pointer readable: `p.offset(1).magic`.
    assertEquals("U32", receiver("p: Ptr<Header>", "p.offset(1).magic").toString());
  }

  public void testPointerConstructorsAreAssociatedFunctionsOfTheWrittenType() {
    SymbolTable.Scope scope = scopeOf("");
    List<Member> members = scope.staticMembers(TypeRef.parse("Ptr<mut Int>"));
    List<String> names = members.stream().map(Member::name).toList();
    assertTrue(names.containsAll(List.of("at", "atUnchecked", "cast", "castAligned")));
    assertFalse(names.contains("offset"));
    for (Member member : members) {
      assertEquals("std.raw", member.module());
    }
    assertEquals("Ptr<mut Int>?", members.stream().filter(m -> m.name().equals("at"))
        .findFirst().orElseThrow().type().toString());
    assertEquals("Ptr<mut Int>", members.stream().filter(m -> m.name().equals("cast"))
        .findFirst().orElseThrow().type().toString());
    // A bare `Ptr` has no pointee to construct.
    assertTrue(scope.staticMembers(TypeRef.simple("Ptr")).stream().noneMatch(m -> m.name().equals("at")));
  }

  public void testAConstructorCallIsTypedAsThePointerItNames() {
    assertEquals("Ptr<mut Int>?", receiver("", "Ptr<mut Int>.at(0)").toString());
    assertEquals("Ptr<mut Int>", receiver("", "Ptr<mut Int>.atUnchecked(0)").toString());
    assertEquals("Ptr<U8>?", receiver("p: Ptr<mut Int>", "Ptr<U8>.castAligned(p)").toString());
    assertEquals("Ptr<mut Int>", receiver("", "Ptr<mut Int>.atUnchecked(0).offset(1)").toString());
  }

  /** Stage 5: a volatile pointer is read like any other, and the one-off volatile access is offered. */
  public void testVolatilePointersAreReadThroughAndHaveTheOneOffs() {
    SymbolTable.Scope scope = scopeOf("");
    assertTrue(names(scope, TypeRef.parse("Ptr<mut volatile Header>")).contains("magic"));
    assertTrue(names(scope, TypeRef.parse("Ptr<mut volatile Int>")).containsAll(List.of("load", "store")));
    assertEquals("Ptr<mut volatile Header>",
        receiver("p: Ptr<mut volatile Packet>", "p.header").toString());
    assertTrue(names(scope, TypeRef.parse("Ptr<Int>")).contains("readVolatile"));
    assertFalse(names(scope, TypeRef.parse("Ptr<Int>")).contains("writeVolatile"));
    assertTrue(names(scope, TypeRef.parse("Ptr<mut Float>")).contains("writeVolatile"));
    assertFalse(names(scope, TypeRef.parse("Ptr<Header>")).contains("readVolatile"));
    assertEquals("U8", receiver("p: Ptr<U8>", "p.readVolatile()").toString());
  }

  public void testAtomicsAreForIntegerPointeesAndWritesNeedMut() {
    SymbolTable.Scope scope = scopeOf("");
    List<String> all = List.of("atomicLoad", "atomicStore", "atomicSwap", "atomicAdd", "atomicSub",
        "atomicAnd", "atomicOr", "atomicXor", "atomicCompareExchange");
    assertTrue(names(scope, TypeRef.parse("Ptr<mut Int>")).containsAll(all));
    assertTrue(names(scope, TypeRef.parse("Ptr<mut U8>")).containsAll(all));
    // Read-only: the load, and nothing that writes.
    List<String> readOnly = names(scope, TypeRef.parse("Ptr<U32>"));
    assertTrue(readOnly.contains("atomicLoad"));
    assertFalse(readOnly.contains("atomicAdd"));
    assertFalse(readOnly.contains("atomicStore"));
    for (String refused : List.of("Ptr<mut Float>", "Ptr<mut Bool>", "Ptr<mut Char>", "Ptr<mut Header>")) {
      assertFalse(refused, names(scope, TypeRef.parse(refused)).contains("atomicLoad"));
    }
    Member add = scope.instanceMembers(TypeRef.parse("Ptr<mut U16>")).stream()
        .filter(m -> m.name().equals("atomicAdd")).findFirst().orElseThrow();
    assertEquals("U16", add.type().toString());
    assertEquals("std.raw", add.module());
    assertEquals("(value: U16, order: AtomicOrder)", add.function().parameterText(true));
    assertNull(scope.instanceMembers(TypeRef.parse("Ptr<mut U16>")).stream()
        .filter(m -> m.name().equals("atomicStore")).findFirst().orElseThrow().type());
    assertEquals("U16", receiver("p: Ptr<mut U16>", "p.atomicCompareExchange(0, 1, AtomicOrder.AcqRel)").toString());
  }

  public void testAnOptionalPointerIsNotReadThrough() {
    SymbolTable.Scope scope = scopeOf("");
    assertFalse(names(scope, TypeRef.parse("Ptr<Header>?")).contains("magic"));
    assertFalse(names(scope, TypeRef.parse("Ptr<Int>?")).contains("load"));
  }

  public void testAnOpaquePtrHasNeitherFieldsNorLoad() {
    SymbolTable.Scope scope = scopeOf("");
    List<String> names = names(scope, TypeRef.simple("Ptr"));
    assertFalse(names.contains("load"));
    assertFalse(names.contains("magic"));
  }

  // ---------------------------------------------------------------- inference

  public void testAFieldReadThroughAPointerIsTypedAsTheField() {
    assertEquals("U32", receiver("p: Ptr<mut Header>", "p.magic").toString());
    assertEquals("U16", receiver("p: Ptr<Header>", "p.flags").toString());
    assertEquals("Int", receiver("p: Ptr<volatile Node>", "p.value").toString());
  }

  public void testANestedStructIsAPointerWithTheParentsCapabilities() {
    assertEquals("Ptr<mut Header>", receiver("p: Ptr<mut Packet>", "p.header").toString());
    assertEquals("Ptr<volatile Header>", receiver("p: Ptr<volatile Packet>", "p.header").toString());
    assertEquals("U32", receiver("p: Ptr<mut Packet>", "p.header.magic").toString());
    assertEquals("U8", receiver("p: Ptr<Packet>", "p.tag").toString());
  }

  public void testALinkedFieldIsAnOptionalPointer() {
    assertEquals("Ptr<Node>?", receiver("p: Ptr<Node>", "p.next").toString());
  }

  public void testALetOfAPointerKeepsItsCapabilities() {
    String source = DECLARATIONS + "fn f(p: Ptr<mut Packet>) {\n    let q: Ptr<volatile mut Header> = p.header\n    q.\n}\n";
    TypeInference.Receiver receiver = TypeInference.receiverAt(source, source.indexOf("    q.") + 6,
        scopeOf("fn f(p: Ptr<mut Packet>) {\n    let q: Ptr<volatile mut Header> = p.header\n    q.\n}\n"),
        List.of());
    assertNotNull(receiver);
    assertEquals("Ptr<mut volatile Header>", receiver.type().toString());
  }

  // ---------------------------------------------------------------- std.mem

  /** std/mem.psm's typed-pointer surface, as it declares it. */
  private static final String MEM = """
      public fn alloc(size: Usize) -> Usize { return 0 }
      public fn free(address: Usize) { }
      public fn allocOf<T>(count: Int) -> Ptr<mut T> { return alloc(count) }
      public fn allocZeroedOf<T>(count: Int) -> Ptr<mut T> { return alloc(count) }
      public fn reallocOf<T>(block: Ptr<mut T>, count: Int) -> Ptr<mut T> { return block }
      public fn free<T>(block: Ptr<mut T>) { }

      public struct Buffer {
          bytes: Int,
      }

      impl Buffer {
          public prop pointer(self) -> Ptr<mut U8>? {
              return none
          }
      }
      """;

  private static TypeRef afterMem(String params, String expression) {
    String source = DECLARATIONS + MEM + "fn f(" + params + ") {\n    " + expression + ".\n}\n";
    SymbolTable.Scope scope = scopeOf(MEM + "fn f(" + params + ") {\n    " + expression + ".\n}\n");
    TypeInference.Receiver receiver = TypeInference.receiverAt(source,
        source.indexOf(expression + ".\n") + expression.length() + 1, scope, List.of());
    return receiver == null ? null : receiver.type();
  }

  public void testAllocOfWithExplicitTypeArgumentsIsAPointerToThatType() {
    assertEquals("Ptr<mut Header>", afterMem("", "allocOf<Header>(4)").toString());
    assertEquals("Ptr<mut Header>", afterMem("", "allocZeroedOf<Header>(4)").toString());
    assertEquals("U32", afterMem("", "allocOf<Header>(4).magic").toString());
    assertEquals("Ptr<mut Int>", afterMem("", "allocOf<Int>(1).offset(1)").toString());
    // The block's own type binds a call with no written arguments.
    assertEquals("Ptr<mut Header>", afterMem("p: Ptr<mut Header>", "reallocOf(p, 8)").toString());
  }

  public void testALetOfAnAllocationIsTyped() {
    String body = "fn f() {\n    let h = allocOf<Header>(4)\n    h.\n}\n";
    String source = DECLARATIONS + MEM + body;
    TypeInference.Receiver receiver = TypeInference.receiverAt(source, source.indexOf("    h.") + 6,
        scopeOf(MEM + body), List.of());
    assertNotNull(receiver);
    assertEquals("Ptr<mut Header>", receiver.type().toString());
  }

  public void testComparisonsAreNotTakenForTypeArguments() {
    // `allocOf < Header > (4)` is read as a call only where `allocOf` is a generic function.
    assertEquals("Bool", afterMem("allocOf: Int, Header: Int", "(allocOf < Header)").toString());
  }

  public void testMemFunctionsAndBufferPointerAreMembers() {
    SymbolTable.Scope scope = scopeOf(MEM);
    assertEquals("Ptr<mut U8>?", scope.instanceMembers(TypeRef.parse("Buffer")).stream()
        .filter(m -> m.name().equals("pointer")).findFirst().orElseThrow().type().toString());
    assertEquals(1, scope.functions("allocOf").size());
    // `free` has the address form and the typed one.
    assertEquals(2, scope.functions("free").size());
  }

  // ------------------------------------------------------------ visibility

  public void testNoMarkerIsTakenBeforeATraitOrAnImpl() {
    ModuleSummary summary = SignatureParser.parse("""
        public trait Shape {
            fn area(self) -> Int
        }

        public struct Square { side: Int }
        public enum Mode { Idle, Busy }
        internal fn helper() -> Int { return 1 }
        """, "app");
    assertEquals(List.of("Shape", "Square", "Mode"),
        summary.types().stream().map(TypeDecl::name).toList());
    assertEquals(TypeDecl.Kind.TRAIT, summary.types().get(0).kind());
    assertEquals(1, summary.functions().stream().filter(f -> f.name().equals("helper")).count());
    assertEquals(FnSig.INTERNAL, summary.functions().stream().filter(f -> f.name().equals("helper"))
        .findFirst().orElseThrow().visibility());
  }
}
