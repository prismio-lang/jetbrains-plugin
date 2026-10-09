package io.prismio.lang;

import java.util.ArrayList;
import java.util.List;

/**
 * The compiler builtins a program with no operating system under it uses to touch
 * a device register, take a lock and run an instruction the language has no syntax
 * for.
 *
 * <p>Source: {@code src/common/mem_builtins.psm}, which is the compiler's one table
 * for them. They are not declared in any module, so nothing in the standard library
 * index can offer them; this is that table written out for completion. A name added
 * there and not here is merely not suggested, never wrong.
 *
 * <p>An address is a {@code Usize}, and the width is in the name:
 * {@code __builtin_mem_vstore_u32(0x09000000, 65)} writes four bytes.
 */
public final class FreestandingBuiltins {

  /** One builtin: its name, its parameter list and result as the compiler checks them, and a line of help. */
  public record Entry(String name, String parameters, String result, String help) {}

  private static final List<Entry> ALL = build();

  private FreestandingBuiltins() {}

  public static List<Entry> all() {
    return ALL;
  }

  private static List<Entry> build() {
    List<Entry> out = new ArrayList<>();
    for (String width : new String[] {"u8", "u16", "u32", "u64"}) {
      String t = width.toUpperCase();
      out.add(new Entry("__builtin_mem_load_" + width, "address: Usize", t, "plain load"));
      out.add(new Entry("__builtin_mem_store_" + width, "address: Usize, value: " + t, "", "plain store"));
      out.add(new Entry("__builtin_mem_uload_" + width, "address: Usize", t, "load at any alignment, of byte storage"));
      out.add(new Entry("__builtin_mem_ustore_" + width, "address: Usize, value: " + t, "", "store at any alignment, of byte storage"));
      out.add(new Entry("__builtin_mem_vload_" + width, "address: Usize", t, "volatile load: never dropped or reordered"));
      out.add(new Entry("__builtin_mem_vstore_" + width, "address: Usize, value: " + t, "", "volatile store: a device register"));
      out.add(new Entry("__builtin_mem_aload_" + width, "address: Usize", t, "atomic load, sequentially consistent"));
      out.add(new Entry("__builtin_mem_astore_" + width, "address: Usize, value: " + t, "", "atomic store"));
      out.add(new Entry("__builtin_mem_aswap_" + width, "address: Usize, value: " + t, t, "atomic exchange; answers the old value"));
      out.add(new Entry("__builtin_mem_aadd_" + width, "address: Usize, value: " + t, t, "atomic add; answers the old value"));
      out.add(new Entry("__builtin_mem_asub_" + width, "address: Usize, value: " + t, t, "atomic subtract; answers the old value"));
      out.add(new Entry("__builtin_mem_aand_" + width, "address: Usize, value: " + t, t, "atomic and; answers the old value"));
      out.add(new Entry("__builtin_mem_aor_" + width, "address: Usize, value: " + t, t, "atomic or; answers the old value"));
      out.add(new Entry("__builtin_mem_axor_" + width, "address: Usize, value: " + t, t, "atomic xor; answers the old value"));
      out.add(new Entry("__builtin_mem_acas_" + width, "address: Usize, expected: " + t + ", desired: " + t, t,
          "compare-and-swap; answers the value that was there"));
    }
    out.add(new Entry("__builtin_mem_fence", "", "", "a full memory barrier"));
    out.add(new Entry("__builtin_swap_bytes", "value", "the value's type", "byte order reversed; one rev or bswap"));
    out.add(new Entry("__builtin_float_bits", "value: Float", "U64", "a Float's 64 bits"));
    out.add(new Entry("__builtin_float_from_bits", "bits: U64", "Float", "the Float with these bits"));
    out.add(new Entry("__builtin_asm", "text: String", "", "run assembly; the text must be a string literal"));
    out.add(new Entry("__builtin_asm_read", "text: String", "U64", "run assembly; $0 is the result"));
    out.add(new Entry("__builtin_asm_write", "text: String, value: U64", "", "run assembly; $0 is the operand"));
    out.add(new Entry("__builtin_ptr_addr", "pointer: Ptr", "Usize", "a Ptr's address"));
    out.add(new Entry("__builtin_addr_ptr", "address: Usize", "Ptr", "a Ptr at an address"));
    for (String width : new String[] {"u8", "u16", "u32"}) {
      String t = width.toUpperCase();
      out.add(new Entry("__builtin_port_in_" + width, "port: U16", t, "x86 port input"));
      out.add(new Entry("__builtin_port_out_" + width, "port: U16, value: " + t, "", "x86 port output"));
    }
    return List.copyOf(out);
  }
}
