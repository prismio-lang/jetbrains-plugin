package io.prismio.symbols;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Declarations from one or more modules, indexed for the three questions
 * completion and inference ask: what is this name, what does this type answer
 * to, and what does this call return.
 *
 * <p>A table is filled once per source it covers and never changed after; a
 * query over several (the standard library, the project, the file being edited)
 * goes through {@link Scope}, which asks each in turn.
 */
public final class SymbolTable {

  private final Map<String, List<FnSig>> functions = new HashMap<>();
  private final Map<String, List<FnSig>> ownMembers = new HashMap<>();
  private final Map<String, List<FnSig>> extensions = new HashMap<>();
  /**
   * Functions whose first parameter is {@code T?} for a type parameter {@code T} --
   * std.option's {@code unwrapOr}, {@code expect}, {@code map}: methods of every
   * optional, whatever it holds, so no module is their home.
   */
  private final List<FnSig> optionalExtensions = new ArrayList<>();
  private final Map<String, List<ModuleSummary.Global>> constants = new HashMap<>();
  private final Map<String, Set<String>> traitsOf = new HashMap<>();
  private final Map<String, TypeDecl> types = new HashMap<>();
  private final Map<String, ModuleSummary.Global> globals = new HashMap<>();
  /** The modules that declare a type or an {@code impl} for it: where its methods live. */
  private final Map<String, Set<String>> homes = new HashMap<>();

  /** Only what another package may call: the standard library, seen from a program. */
  public static final int EXPORTED = 0;
  /** Adds {@code internal}: another file of the same project. */
  public static final int PACKAGE = 1;
  /** Everything: the file being edited, which may call all it declares. */
  public static final int ALL = 2;

  /**
   * Adds a module's declarations, keeping the functions that are reachable from
   * where the completion happens ({@link #EXPORTED}, {@link #PACKAGE} or {@link #ALL}).
   */
  public void add(@NotNull ModuleSummary summary, int reach) {
    for (FnSig fn : summary.functions()) {
      boolean reachable = fn.visibleOutside() || reach == ALL
          || reach == PACKAGE && fn.visibility() == FnSig.INTERNAL;
      if (!reachable) {
        continue;
      }
      if (fn.owner() != null) {
        homes.computeIfAbsent(fn.owner().memberKey(), k -> new java.util.HashSet<>()).add(fn.module());
        ownMembers.computeIfAbsent(fn.owner().memberKey(), k -> new ArrayList<>()).add(fn);
        if (fn.trait() != null) {
          traitsOf.computeIfAbsent(fn.owner().memberKey(), k -> new LinkedHashSet<>()).add(fn.trait());
        }
        continue;
      }
      functions.computeIfAbsent(fn.name(), k -> new ArrayList<>()).add(fn);
      FnSig.Param first = fn.params().isEmpty() ? null : fn.params().get(0);
      if (first != null && first.type() != null) {
        extensions.computeIfAbsent(first.type().memberKey(), k -> new ArrayList<>()).add(fn);
        if (first.type().optional() && first.type().args().isEmpty()
            && fn.allTypeParameters().contains(first.type().name())) {
          optionalExtensions.add(fn);
        }
      }
    }
    for (TypeDecl type : summary.types()) {
      types.putIfAbsent(type.name(), type);
      homes.computeIfAbsent(type.name(), k -> new java.util.HashSet<>()).add(type.module());
    }
    for (ModuleSummary.Global global : summary.globals()) {
      if (global.owner() == null) {
        globals.putIfAbsent(global.name(), global);
      } else {
        constants.computeIfAbsent(global.owner().memberKey(), k -> new ArrayList<>()).add(global);
      }
    }
  }

  void addBuiltIn(@NotNull FnSig member) {
    if (member.owner() != null) {
      ownMembers.computeIfAbsent(member.owner().memberKey(), k -> new ArrayList<>()).add(member);
    }
  }

  /** Several tables answering as one, the first to declare a name winning. */
  public static final class Scope {
    private final List<SymbolTable> tables;

    public Scope(@NotNull List<SymbolTable> tables) {
      this.tables = tables;
    }

    public @Nullable TypeDecl type(@NotNull String name) {
      for (SymbolTable table : tables) {
        TypeDecl type = table.types.get(name);
        if (type != null) {
          return type;
        }
      }
      return null;
    }

    public @NotNull List<FnSig> functions(@NotNull String name) {
      List<FnSig> out = new ArrayList<>();
      for (SymbolTable table : tables) {
        out.addAll(table.functions.getOrDefault(name, List.of()));
      }
      return out;
    }

    public @Nullable ModuleSummary.Global global(@NotNull String name) {
      for (SymbolTable table : tables) {
        ModuleSummary.Global global = table.globals.get(name);
        if (global != null) {
          return global;
        }
      }
      return null;
    }

    /**
     * Whether a member declared on a typed pointer applies to {@code receiver}: {@code load} is
     * declared once per scalar, on {@code Ptr<Int>}, {@code Ptr<U8>}, and so on, so it must match
     * the pointee; {@code store} is declared on {@code Ptr<mut Int>}, so it needs a {@code mut} receiver. Other owners are not narrowed by their arguments here.
     */
    private static boolean pointeeMatches(@NotNull FnSig fn, @NotNull TypeRef receiver) {
      TypeRef owner = fn.owner();
      if (owner == null || !owner.isTypedPointer()) {
        return true;
      }
      return receiver.isTypedPointer()
          && (!owner.isMutablePointer() || receiver.isMutablePointer())
          && TypeRef.unify(owner, receiver, fn.ownerParameters(), new HashMap<>());
    }

    /** A constructor of a typed pointer belongs to a written {@code Ptr<T>}, not to the opaque {@code Ptr}. */
    private static boolean staticPointerMatches(@NotNull FnSig fn, @NotNull TypeRef type) {
      return fn.owner() == null || !fn.owner().isTypedPointer() || type.isTypedPointer();
    }

    /**
     * What {@code receiver.} can be followed by, for an instance of {@code receiver}:
     * its fields, the members its {@code impl} blocks declare, those of the traits it
     * implements, and every free function whose first parameter it can be — the
     * compiler's uniform call syntax makes {@code xs.sort()} of {@code sort(xs)}.
     */
    public @NotNull List<Member> instanceMembers(@NotNull TypeRef receiver) {
      List<Member> out = new ArrayList<>();
      Set<String> seen = new java.util.HashSet<>();
      String base = receiver.memberKey();

      TypeDecl decl = type(base);
      if (decl != null) {
        Map<String, TypeRef> bindings = bind(decl.typeParameters(), decl.selfType(), receiver);
        for (FnSig.Param field : decl.fields()) {
          if (seen.add("f:" + field.name())) {
            out.add(new Member(field.name(), Member.Kind.FIELD, Member.Origin.OWN,
                field.type() == null ? null : field.type().substitute(bindings), null, "", ""));
          }
        }
      }
      // `p.field` through a `Ptr<Struct>`: the struct's fields, read where they lie. Not through
      // an optional: `Ptr<T>?` is narrowed by `!= none` first, which the plugin does not follow.
      TypeRef pointee = receiver.optional() ? null : receiver.pointee();
      TypeDecl pointed = pointee == null || pointee.optional() ? null : type(pointee.base());
      if (pointed != null && pointed.kind() == TypeDecl.Kind.STRUCT) {
        Map<String, TypeRef> bindings = bind(pointed.typeParameters(), pointed.selfType(), pointee);
        for (FnSig.Param field : pointed.fields()) {
          if (seen.add("f:" + field.name())) {
            out.add(new Member(field.name(), Member.Kind.FIELD, Member.Origin.OWN,
                field.type() == null ? null : throughPointer(field.type().substitute(bindings), receiver),
                null, "", ""));
          }
        }
      }

      Set<String> traits = new LinkedHashSet<>();
      // What the compiler lowers itself answers before a library function of the same name:
      // an array's `contains` is `contains(a, N, x)`, so std's `count` parameter is not one
      // the caller writes.
      Set<String> lowered = new java.util.HashSet<>();
      for (SymbolTable table : tables) {
        for (FnSig fn : table.ownMembers.getOrDefault(base, List.of())) {
          if (!fn.hasSelf() || !pointeeMatches(fn, receiver)) {
            continue;
          }
          Member member = asMember(fn, receiver, fn.trait() == null
              ? (fn.module().isEmpty() ? Member.Origin.BUILT_IN : Member.Origin.OWN)
              : Member.Origin.TRAIT);
          if (member != null && seen.add(signatureKey(fn))) {
            out.add(member);
            if (member.origin() == Member.Origin.BUILT_IN) {
              lowered.add(member.name());
            }
          }
        }
        traits.addAll(table.traitsOf.getOrDefault(base, Set.of()));
      }

      // A trait's methods the impl did not write: its defaults.
      for (String traitName : traits) {
        TypeDecl trait = type(traitName);
        if (trait == null) {
          continue;
        }
        for (FnSig fn : trait.methods()) {
          if (fn.hasSelf() && seen.add(signatureKey(fn))) {
            Map<String, TypeRef> bindings = Map.of("Self", receiver);
            out.add(new Member(fn.name(), fn.property() ? Member.Kind.PROPERTY : Member.Kind.METHOD,
                Member.Origin.TRAIT, fn.returnType() == null ? null : fn.returnType().substitute(bindings),
                fn, trait.module(), fn.doc()));
          }
        }
      }

      Set<String> home = new java.util.HashSet<>();
      for (SymbolTable table : tables) {
        home.addAll(table.homes.getOrDefault(base, Set.of()));
      }
      for (SymbolTable table : tables) {
        for (FnSig fn : table.extensions.getOrDefault(base, List.of())) {
          if (lowered.contains(fn.name()) || !isHome(fn.module(), base, home)) {
            continue;
          }
          Map<String, TypeRef> bindings = new HashMap<>();
          TypeRef first = fn.params().get(0).type();
          if (first == null || !TypeRef.unify(first, receiver, fn.allTypeParameters(), bindings)) {
            continue;
          }
          if (seen.add(signatureKey(fn))) {
            out.add(new Member(fn.name(), Member.Kind.METHOD, Member.Origin.EXTENSION,
                fn.returnType() == null ? null : fn.returnType().substitute(bindings), fn, fn.module(),
                fn.doc()));
          }
        }
        // Any optional is the receiver of a function over `T?`: `home.unwrapOr("/")`.
        if (receiver.optional()) {
          for (FnSig fn : table.optionalExtensions) {
            if (lowered.contains(fn.name())) {
              continue;
            }
            Map<String, TypeRef> bindings = new HashMap<>();
            TypeRef first = fn.params().get(0).type();
            if (first == null || !TypeRef.unify(first, receiver, fn.allTypeParameters(), bindings)) {
              continue;
            }
            if (seen.add(signatureKey(fn))) {
              out.add(new Member(fn.name(), Member.Kind.METHOD, Member.Origin.EXTENSION,
                  fn.returnType() == null ? null : fn.returnType().substitute(bindings), fn, fn.module(),
                  fn.doc()));
            }
          }
        }
      }
      return out;
    }

    /**
     * Whether a free function in {@code module} reads as a method of {@code base}.
     * Uniform call syntax makes every function a method of its first parameter's
     * type, so {@code "x".print()} compiles; offering every one would bury a
     * String's own methods under {@code std.fs}'s path helpers. So only the
     * type's home counts, as Kotlin offers only what was written as an
     * extension: a module that declares the type or an {@code impl} for it, or
     * one named after it — {@code std.vec}, whose functions are {@code Vec}'s
     * methods ({@code src/sema/vec.psm}: "most of Vec's reading methods are
     * exactly that").
     */
    private static boolean isHome(String module, String base, Set<String> home) {
      // A function written over `[T]` is written for arrays: it is not the String-and-path
      // noise the rule exists to keep out, and no module is named after `Array`.
      if (base.equals("Array") || home.contains(module)) {
        return true;
      }
      String leaf = module.substring(module.lastIndexOf('.') + 1);
      return leaf.equalsIgnoreCase(base);
    }

    /** What {@code Type.} can be followed by: constructors and other functions without {@code self}, constants, variants. */
    public @NotNull List<Member> staticMembers(@NotNull TypeRef type) {
      List<Member> out = new ArrayList<>();
      Set<String> seen = new java.util.HashSet<>();
      String base = type.memberKey();
      TypeDecl decl = type(base);
      if (decl != null) {
        for (String variant : decl.variants()) {
          out.add(new Member(variant, Member.Kind.VARIANT, Member.Origin.OWN, type, null, decl.module(),
              ""));
        }
      }
      for (SymbolTable table : tables) {
        for (FnSig fn : table.ownMembers.getOrDefault(base, List.of())) {
          if (fn.hasSelf() || !staticPointerMatches(fn, type) || !seen.add(signatureKey(fn))) {
            continue;
          }
          Member member = asMember(fn, type, fn.trait() == null ? Member.Origin.OWN : Member.Origin.TRAIT);
          if (member != null) {
            out.add(new Member(member.name(), Member.Kind.STATIC_METHOD, member.origin(), member.type(),
                fn, member.module(), member.doc()));
          }
        }
        for (ModuleSummary.Global constant : table.constants.getOrDefault(base, List.of())) {
          if (seen.add("c:" + constant.name())) {
            out.add(new Member(constant.name(), Member.Kind.CONSTANT, Member.Origin.OWN, constant.type(),
                null, constant.module(), constant.doc()));
          }
        }
      }
      return out;
    }

    private static @Nullable Member asMember(@NotNull FnSig fn, @NotNull TypeRef receiver,
        @NotNull Member.Origin origin) {
      Map<String, TypeRef> bindings = new HashMap<>();
      if (fn.owner() != null) {
        TypeRef.unify(fn.owner(), receiver, fn.ownerParameters(), bindings);
      }
      bindings.putIfAbsent("Self", receiver);
      TypeRef type = fn.returnType() == null ? null : fn.returnType().substitute(bindings);
      Member.Kind kind = fn.property() ? Member.Kind.PROPERTY : Member.Kind.METHOD;
      return new Member(fn.name(), kind, origin, type, fn, fn.module(), fn.doc());
    }

    /**
     * What reading a field of type {@code field} through {@code pointer} gives: the field's own
     * type, except a struct stored inline, which is a pointer to that storage with the parent's
     * capabilities, so {@code p.header.magic} reads through two (docs/PTR_PLAN.md).
     */
    private @NotNull TypeRef throughPointer(@NotNull TypeRef field, @NotNull TypeRef pointer) {
      TypeDecl inline = field.optional() ? null : type(field.base());
      if (inline == null || inline.kind() != TypeDecl.Kind.STRUCT) {
        return field;
      }
      return new TypeRef(TypeRef.POINTER, List.of(field), false, pointer.qualifiers());
    }

    private static Map<String, TypeRef> bind(List<String> parameters, TypeRef pattern, TypeRef actual) {
      Map<String, TypeRef> bindings = new HashMap<>();
      TypeRef.unify(pattern, actual, parameters, bindings);
      return bindings;
    }

    /** Overloads with the same parameter list are one entry: {@code print} is declared once per type it prints. */
    private static String signatureKey(FnSig fn) {
      StringBuilder key = new StringBuilder(fn.property() ? "p:" : "m:").append(fn.name()).append('(');
      for (FnSig.Param param : fn.callParams(fn.owner() == null)) {
        key.append(param.type()).append(',');
      }
      return key.append(')').toString();
    }
  }
}
