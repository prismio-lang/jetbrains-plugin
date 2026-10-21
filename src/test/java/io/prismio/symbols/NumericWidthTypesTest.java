package io.prismio.symbols;

import junit.framework.TestCase;

/**
 * {@code Int<N>} and {@code UInt<N>} (src/ast/int_width.psm) and the float family: the signature
 * parser reads the width as a number rather than a type, and a width with a name is that name,
 * as the compiler's parser folds it.
 */
public class NumericWidthTypesTest extends TestCase {

  public void testAWidthWithANameIsThatName() {
    assertEquals("I8", TypeRef.integer(false, "8").toString());
    assertEquals("I16", TypeRef.integer(false, "16").toString());
    assertEquals("Int", TypeRef.integer(false, "32").toString());
    assertEquals("I64", TypeRef.integer(false, "64").toString());
    assertEquals("U8", TypeRef.integer(true, "8").toString());
    assertEquals("U32", TypeRef.integer(true, "32").toString());
    assertEquals("U64", TypeRef.integer(true, "64").toString());
    assertEquals("Int<24>", TypeRef.integer(false, "24").toString());
    assertEquals("UInt<128>", TypeRef.integer(true, "128").toString());
  }

  public void testSignatureParserReadsWidthsAndKeepsTheRestOfTheDeclaration() {
    ModuleSummary summary = SignatureParser.parse("""
        struct Pixel { r: UInt<5>, g: UInt<6>, wide: Int<128>, v: Vec<Int<24>>, h: F16 }

        fn pack(p: UInt<5>, q: Int<8>, r: Int<24>?, s: F32) -> UInt<16> {
            return 0
        }

        fn after() -> Int {
            return 1
        }
        """, "app");
    SymbolTable table = new SymbolTable();
    table.add(summary, SymbolTable.ALL);
    TypeDecl pixel = new SymbolTable.Scope(java.util.List.of(table)).type("Pixel");
    assertEquals(5, pixel.fields().size());
    assertEquals("UInt<5>", pixel.fields().get(0).type().toString());
    assertEquals("UInt<6>", pixel.fields().get(1).type().toString());
    assertEquals("Int<128>", pixel.fields().get(2).type().toString());
    assertEquals("Vec<Int<24>>", pixel.fields().get(3).type().toString());
    assertEquals("F16", pixel.fields().get(4).type().toString());

    assertEquals(2, summary.functions().size());
    FnSig pack = summary.functions().get(0);
    assertEquals(4, pack.params().size());
    assertEquals("UInt<5>", pack.params().get(0).type().toString());
    assertEquals("I8", pack.params().get(1).type().toString());
    assertEquals("Int<24>?", pack.params().get(2).type().toString());
    assertEquals("F32", pack.params().get(3).type().toString());
    assertEquals("U16", pack.returnType().toString());
    assertEquals("after", summary.functions().get(1).name());
  }
}
