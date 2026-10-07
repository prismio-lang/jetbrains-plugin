package io.prismio.symbols;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A {@code struct}, {@code enum} or {@code trait}. Types carry no visibility
 * check in the compiler, so every one is visible to an importer.
 *
 * @param fields a struct's fields; empty for the other kinds
 * @param variants an enum's variant names
 * @param methods a trait's method signatures, which every implementing type answers to
 */
public record TypeDecl(
    @NotNull String name,
    @NotNull String module,
    @NotNull Kind kind,
    @NotNull List<String> typeParameters,
    @NotNull List<FnSig.Param> fields,
    @NotNull List<String> variants,
    @NotNull List<FnSig> methods,
    @NotNull String doc,
    int offset) {

  public enum Kind { STRUCT, ENUM, TRAIT }

  /** The type this declaration introduces, with its own parameters as arguments: {@code Box<T>}. */
  public @NotNull TypeRef selfType() {
    return new TypeRef(name, typeParameters.stream().map(TypeRef::simple).toList(), false);
  }
}
