package io.prismio.lang;

import java.util.Set;

/**
 * The word tables the lexer, completion and colour settings all read.
 *
 * <p>One copy, because three copies drift. Each list mirrors a specific place in
 * the compiler and names it, so a reader checking whether this is current knows
 * exactly which file to open.
 */
public final class PrismioWords {

  private PrismioWords() {}

  /**
   * Reserved words — {@code isKeyword} in {@code src/lexer/token.psm}.
   *
   * <p>Note {@code and} and {@code or}: Prismio spells the logical operators as
   * words. {@code &&} and {@code ||} also lex, but no source in the compiler,
   * the standard library or the test suite uses them. So are {@code is}, {@code is not}
   * and {@code not}, the words for {@code ==}, {@code !=} and {@code !}.
   */
  public static final Set<String> KEYWORDS = Set.of(
      "import",
      "match", "if", "else", "and", "or", "is", "not",
      "true", "false",
      "break", "continue", "return", "throw",
      "while", "loop", "for", "in",
      "let", "struct", "impl", "enum", "trait", "where", "fn", "extern",
      "mut", "as",
      "inout", "sink",
      "region",
      "none", "default");

  /**
   * Words the parser gives meaning to in one position only.
   *
   * <p>Not in {@code isKeyword}, so each is a legal identifier elsewhere: a
   * variable may be called {@code pin}. They are highlighted anyway because a
   * reader wants to see them, and they are a separate token from a real keyword
   * so that nothing here can make valid code look wrong.
   *
   * <p>The three visibility levels are public (0), private (1) and internal (2);
   * a declaration with no modifier takes the default. {@code priv} was the
   * pre-0.1 spelling of {@code private} and is gone, not aliased.
   *
   * <p>Sources: {@code src/parse/decl.psm} for the visibility levels, {@code dyn},
   * {@code Self}, {@code type} and the FFI contracts; {@code src/parse/expr.psm}
   * for {@code spawn}; {@code src/parse/stmt.psm} for {@code pin}, {@code repeat} and
   * {@code step}, which follows a range's end in a {@code for} header ({@code 10..0 step 2});
   * {@code src/parse/parser.psm} for {@code from}, which follows an import group or
   * {@code *}: {@code import {io, string} from std}; {@code src/parse/decl.psm} for
   * {@code prop}, which declares a property where {@code fn} declares a method, and
   * {@code cold}, which marks a function that rarely runs ({@code cold fn fail()}), and
   * the other function markers of {@code src/parse/modifiers.psm}: {@code hot},
   * {@code inline} (a hint), {@code inline(always)}, {@code inline(never)}, {@code noinline}
   * and {@code noreturn}, and {@code const} before {@code fn} ({@code const fn area()}) or a
   * name ({@code const CELLS = area(8)}, a declaration whose value is worked out at compile
   * time).
   * {@code async} before {@code fn}, {@code await} and {@code join} before a name, and
   * {@code scope} before {@code {}, in {@code src/parse/decl.psm}, {@code expr.psm} and
   * {@code stmt.psm} (docs/ASYNC_PLAN.md).
   * Also in {@code src/parse/decl.psm}, for programs with no operating system under them
   * (docs/FREESTANDING_PLAN.md): {@code export}, {@code naked}, {@code section("...")}
   * and {@code align(n)} before {@code fn}, and {@code repr(C)} and {@code packed}
   * before {@code struct}.
   *
   * <p>{@code volatile} is a capability of a typed pointer, written before the pointee with
   * {@code mut}: {@code Ptr<volatile T>}, {@code Ptr<mut volatile T>} ({@code parsePtrQualifiers}
   * in {@code src/parse/decl.psm}; docs/PTR_PLAN.md). It is a qualifier only inside a
   * {@code Ptr<...>} argument list and only when a type follows, so the lexer decides it by its
   * neighbours rather than by this table alone.
   */
  public static final Set<String> CONTEXTUAL_KEYWORDS = Set.of(
      "public", "private", "internal",
      "dyn", "Self", "type", "spawn",
      "pin", "unique",
      "produce", "borrow", "alias", "free",
      "repeat", "from", "step",
      "prop", "cold", "hot", "inline", "noinline", "noreturn", "const",
      "async", "await", "scope", "join",
      "export", "naked", "section", "align", "repr", "packed",
      "volatile",
      "codeset", "typelist", "workload", "setup", "measure");

  /**
   * Built-in types — {@code typeFromSemKey} and {@code typeFromAnnotation} in
   * {@code src/ast/types.psm}.
   *
   * <p>{@code I32} is another spelling of {@code Int} (the parser renames it), so it
   * is listed. {@code UInt} is the constructor of {@code UInt<N>}, and {@code Int}
   * of {@code Int<N>}; {@code F16}, {@code F32}, {@code F64}, {@code F128} and
   * {@code BF16} are the float family (src/ast/float_width.psm). {@code F} and
   * {@code Float} followed by a width are compile errors, so they are not listed
   * as taking one.
   */
  public static final Set<String> BUILTIN_TYPES = Set.of(
      "Int", "UInt", "Float", "Bool", "Char", "String", "Ptr", "Void",
      "I8", "I16", "I32", "I64", "Isize",
      "U8", "U16", "U32", "U64", "Usize",
      "F16", "F32", "F64", "F128", "BF16");

  /** Generic types the standard library ships; not known to the compiler itself. */
  public static final Set<String> STDLIB_TYPES = Set.of(
      "List", "Map", "Result", "Box", "Task", "Slice", "DataView", "Chan");

  /** Modules resolvable as {@code import std.<leaf>}; the files in {@code std/}. */
  public static final Set<String> STD_MODULES = Set.of(
      "io", "string", "list", "map", "option", "fs", "process",
      "display", "eq", "ord", "key", "copy", "iter");
}
