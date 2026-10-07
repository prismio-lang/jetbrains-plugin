package io.prismio.symbols;

import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A function, method or property as its declaration states it.
 *
 * @param module the module that declares it: {@code std.string}, or a project module's dotted name
 * @param owner the type an {@code impl} block attaches it to, or null for a free function
 * @param ownerParameters the {@code impl}'s type parameters: {@code T} in {@code impl<T> Result<T, E>}
 * @param trait the trait an {@code impl Trait for Type} block implements, or null
 * @param hasSelf whether it takes {@code self}: an instance member rather than {@code Type.name()}
 * @param property declared with {@code prop}, read as {@code x.name} with no parentheses
 * @param visibility {@link #PUBLIC}, {@link #PRIVATE} or {@link #INTERNAL}, as {@code src/parse/decl.psm} numbers them
 * @param offset where the name starts in the declaring text
 */
public record FnSig(
    @NotNull String name,
    @NotNull String module,
    @Nullable TypeRef owner,
    @NotNull List<String> ownerParameters,
    @Nullable String trait,
    @NotNull List<String> typeParameters,
    @NotNull List<Param> params,
    @Nullable TypeRef returnType,
    boolean hasSelf,
    boolean property,
    boolean external,
    int visibility,
    @NotNull String doc,
    int offset) {

  public static final int PUBLIC = 0;
  public static final int PRIVATE = 1;
  public static final int INTERNAL = 2;

  /**
   * Whether code in another module may call it. Visibility is enforced on
   * functions only, and a trait's methods are reachable wherever the trait is:
   * {@code impl Display for Int { fn show(self) }} carries no marker and is still
   * {@code x.show()} everywhere.
   */
  public boolean visibleOutside() {
    return visibility == PUBLIC || trait != null;
  }

  /**
   * Whether the compiler's uniform call syntax lets it be written as a method on
   * its first parameter's type: {@code sort(xs)} as {@code xs.sort()}.
   */
  public boolean callableAsMethod() {
    return owner == null && !params.isEmpty();
  }

  /** The parameters a caller writes: all of them, less the receiver when called as a method. */
  public @NotNull List<Param> callParams(boolean asMethod) {
    return asMethod && owner == null && !params.isEmpty() ? params.subList(1, params.size()) : params;
  }

  public @NotNull String parameterText(boolean asMethod) {
    return callParams(asMethod).stream().map(Param::toString)
        .collect(Collectors.joining(", ", "(", ")"));
  }

  /** Every type parameter in scope for this signature: the impl's and its own. */
  public @NotNull List<String> allTypeParameters() {
    if (ownerParameters.isEmpty()) {
      return typeParameters;
    }
    if (typeParameters.isEmpty()) {
      return ownerParameters;
    }
    return java.util.stream.Stream.concat(ownerParameters.stream(), typeParameters.stream()).toList();
  }

  public record Param(@NotNull String name, @Nullable TypeRef type, @NotNull String mode) {
    @Override
    public @NotNull String toString() {
      String prefix = mode.isEmpty() ? "" : mode + " ";
      return type == null ? prefix + name : prefix + name + ": " + type;
    }
  }
}
