package io.prismio.symbols;

import com.intellij.psi.tree.IElementType;
import io.prismio.lang.PrismioWords;
import io.prismio.psi.PrismioTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The type of the expression in front of a {@code .}, worked out from the source
 * around it — what {@code let x = "Hello"} makes of {@code x}.
 *
 * <p>This is not the compiler's type checker and does not try to be: it follows
 * the handful of things that fix a local's type in practice (a literal, an
 * annotation, a constructor or struct literal, a call whose declaration states
 * its result, a parameter, a {@code for} binder, {@code self}) and answers null
 * rather than guess at anything else. A wrong member list is worse than none;
 * an empty one at least sends the reader to the declaration.
 *
 * <p>Every type it answers with comes from a declaration — the file's, the
 * project's, or the toolchain's standard library — so it knows nothing about any
 * particular type that the sources it reads do not say.
 */
public final class TypeInference {

  /** What stands before the dot: an instance, a type (for {@code Type.}), or an import alias. */
  public record Receiver(@Nullable TypeRef type, boolean isStatic, @Nullable String module) {
    static Receiver instance(@Nullable TypeRef type) {
      return type == null ? null : new Receiver(type, false, null);
    }

    static Receiver ofType(@NotNull TypeRef type) {
      return new Receiver(type, true, null);
    }

    static Receiver ofModule(@NotNull String module) {
      return new Receiver(null, false, module);
    }
  }

  private static final int MAX_DEPTH = 24;

  private final List<Tok> t;
  private final SymbolTable.Scope scope;
  private final List<ModuleSummary.Import> imports;
  private int depth;

  private TypeInference(@NotNull CharSequence text, @NotNull SymbolTable.Scope scope,
      @NotNull List<ModuleSummary.Import> imports) {
    this.t = Tok.code(Tok.lex(text));
    this.scope = scope;
    this.imports = imports;
  }

  /**
   * The receiver of a member access being typed at {@code offset}: the caret, or
   * the start of the member name typed so far. Null when the text before it does
   * not end in {@code expression.}, or when the expression's type cannot be
   * known from the source.
   */
  public static @Nullable Receiver receiverAt(@NotNull CharSequence text, int offset,
      @NotNull SymbolTable.Scope scope, @NotNull List<ModuleSummary.Import> imports) {
    TypeInference inference = new TypeInference(text, scope, imports);
    return inference.receiverBefore(offset);
  }

  /** The type of a whole expression, for tests and for callers that have one in hand. */
  public static @Nullable TypeRef typeOf(@NotNull CharSequence text, @NotNull SymbolTable.Scope scope) {
    TypeInference inference = new TypeInference(text, scope, List.of());
    return inference.t.isEmpty() ? null : inference.expr(0, inference.t.size() - 1);
  }

  /**
   * The type of every {@code let} that does not write one, keyed by the offset
   * where its name ends: where an inlay hint reads {@code : String}. A binding
   * whose type cannot be known from the source is left out, never guessed.
   */
  public static @NotNull Map<Integer, TypeRef> letTypes(@NotNull CharSequence text,
      @NotNull SymbolTable.Scope scope, @NotNull List<ModuleSummary.Import> imports) {
    TypeInference inference = new TypeInference(text, scope, imports);
    Map<Integer, TypeRef> out = new java.util.LinkedHashMap<>();
    List<Tok> t = inference.t;
    for (int k = 0; k < t.size(); k++) {
      if (!t.get(k).is("let") || t.get(k).type() != PrismioTypes.KEYWORD) {
        continue;
      }
      int n = k + 1;
      if (n < t.size() && t.get(n).is("mut")) {
        n++;
      }
      if (n + 1 >= t.size() || !t.get(n).isName() || t.get(n + 1).type() != PrismioTypes.ASSIGNMENT_OP) {
        continue;
      }
      // A literal says its own type; a hint would only repeat it.
      if (n + 2 < t.size() && SignatureParser.literalType(t.get(n + 2)) != null
          && (n + 3 >= t.size() || t.get(n + 3).newlineBefore())) {
        continue;
      }
      TypeRef type = inference.letType(n);
      if (type != null && !type.name().equals("Range") && !type.name().equals("fn")
          && type.args().stream().noneMatch(arg -> arg.name().length() == 1)) {
        out.put(t.get(n).end(), type);
      }
    }
    return out;
  }

  /**
   * The call whose argument list holds {@code offset}: its overloads, and which
   * argument the caret is in. For parameter info.
   *
   * @param openParen offset of the call's {@code (}
   * @param asMethod whether the overloads are shown without their receiver parameter
   */
  public record Call(int openParen, @NotNull List<FnSig> overloads, boolean asMethod, int argument) {}

  public static @Nullable Call callAt(@NotNull CharSequence text, int offset,
      @NotNull SymbolTable.Scope scope, @NotNull List<ModuleSummary.Import> imports) {
    TypeInference inference = new TypeInference(text, scope, imports);
    return inference.callBefore(offset);
  }

  private @Nullable Call callBefore(int offset) {
    int last = -1;
    for (int k = 0; k < t.size() && t.get(k).start() < offset; k++) {
      last = k;
    }
    int level = 0;
    int commas = 0;
    for (int k = last; k >= 0; k--) {
      Tok tok = t.get(k);
      IElementType type = tok.type();
      if (type == PrismioTypes.RPAREN || type == PrismioTypes.RBRACKET || type == PrismioTypes.RBRACE) {
        level++;
      } else if (type == PrismioTypes.LBRACKET || type == PrismioTypes.LBRACE) {
        if (level-- == 0) {
          return null;
        }
      } else if (type == PrismioTypes.COMMA && level == 0) {
        commas++;
      } else if (type == PrismioTypes.LPAREN) {
        if (level-- > 0) {
          continue;
        }
        if (k == 0 || !t.get(k - 1).isName()) {
          return null;
        }
        Tok callee = t.get(k - 1);
        List<FnSig> overloads = new ArrayList<>();
        boolean asMethod = false;
        if (k >= 2 && t.get(k - 2).type() == PrismioTypes.DOT) {
          Receiver receiver = receiverBefore(callee.start());
          if (receiver == null) {
            return null;
          }
          if (receiver.module() != null) {
            for (FnSig fn : scope.functions(callee.text())) {
              if (fn.module().equals(receiver.module())) {
                overloads.add(fn);
              }
            }
          } else {
            List<Member> members = receiver.isStatic()
                ? scope.staticMembers(receiver.type()) : scope.instanceMembers(receiver.type());
            for (Member member : members) {
              if (member.name().equals(callee.text()) && member.isCall() && member.function() != null) {
                overloads.add(member.function());
                asMethod |= member.origin() == Member.Origin.EXTENSION;
              }
            }
          }
        } else {
          java.util.Set<String> seen = new java.util.HashSet<>();
          for (FnSig fn : scope.functions(callee.text())) {
            if (seen.add(fn.module() + fn.parameterText(false))) {
              overloads.add(fn);
            }
          }
        }
        return overloads.isEmpty() ? null : new Call(tok.start(), overloads, asMethod, commas);
      }
    }
    return null;
  }

  private @Nullable Receiver receiverBefore(int offset) {
    int d = -1;
    for (int k = 0; k < t.size() && t.get(k).end() <= offset; k++) {
      d = k;
    }
    if (d >= 0 && t.get(d).end() == offset && t.get(d).isName()) {
      d--;
    }
    if (d < 1 || t.get(d).type() != PrismioTypes.DOT) {
      return null;
    }
    int end = d - 1;
    int start = chainStart(end);
    if (start < 0) {
      return null;
    }
    Value value = chain(start, end);
    if (value == null) {
      return null;
    }
    if (value.module != null) {
      return Receiver.ofModule(value.module);
    }
    return value.isStatic ? Receiver.ofType(value.type) : Receiver.instance(value.type);
  }

  // ------------------------------------------------------------ chains

  /** An expression's value: an instance of {@code type}, the type itself, or a module. */
  private record Value(@Nullable TypeRef type, boolean isStatic, @Nullable String module) {
    static @Nullable Value of(@Nullable TypeRef type) {
      return type == null ? null : new Value(type, false, null);
    }
  }

  /**
   * Where the postfix chain ending at {@code j} starts: back over {@code .name},
   * calls, indexing and type arguments to its first primary.
   */
  private int chainStart(int j) {
    while (j >= 0) {
      Tok tok = t.get(j);
      if (tok.type() == PrismioTypes.RPAREN || tok.type() == PrismioTypes.RBRACKET) {
        int open = matchingBackward(j);
        if (open < 0) {
          return -1;
        }
        j = open;
        // A call or index continues the chain to what it applies to; a bracket
        // or parenthesis with nothing before it is the primary itself.
        if (j > 0 && continuesChain(t.get(j - 1)) && !t.get(j).newlineBefore()) {
          j--;
          if (t.get(j).is(">") || t.get(j).is(">>")) {
            int lt = matchingAngleBackward(j);
            if (lt > 0 && t.get(lt - 1).isName()) {
              j = lt - 1;
            }
          }
          continue;
        }
      } else if (tok.type() == PrismioTypes.RBRACE) {
        // `Point { x: 1 }.` — a struct literal.
        int open = matchingBackward(j);
        if (open > 0 && t.get(open - 1).isName()) {
          j = open - 1;
        } else {
          return -1;
        }
      } else if (tok.is(">") || tok.is(">>")) {
        // `Box<Int>.` — a type with arguments, as a static receiver.
        int lt = matchingAngleBackward(j);
        if (lt > 0 && t.get(lt - 1).isName()) {
          j = lt - 1;
        } else {
          return -1;
        }
      } else if (!(tok.isName() || isLiteral(tok) || tok.is("self"))) {
        return -1;
      }
      if (j > 1 && t.get(j - 1).type() == PrismioTypes.DOT && !t.get(j).newlineBefore()) {
        j -= 2;
        continue;
      }
      if (j > 1 && t.get(j - 1).type() == PrismioTypes.DOT && t.get(j - 1).newlineBefore()) {
        // A chain continued on a new line, `.map(...)` style.
        j -= 2;
        continue;
      }
      return j;
    }
    return -1;
  }

  private static boolean continuesChain(Tok before) {
    return before.isName() || before.type() == PrismioTypes.RPAREN
        || before.type() == PrismioTypes.RBRACKET || before.is(">") || before.is(">>")
        || before.type() == PrismioTypes.STRING_LITERAL;
  }

  /** Evaluates the postfix chain {@code [a, b]}. */
  private @Nullable Value chain(int a, int b) {
    if (++depth > MAX_DEPTH) {
      depth--;
      return null;
    }
    try {
      int[] next = {a};
      Value value = primary(a, b, next);
      int k = next[0];
      while (value != null && k <= b) {
        Tok tok = t.get(k);
        if (tok.type() == PrismioTypes.DOT && k + 1 <= b) {
          Tok name = t.get(k + 1);
          k += 2;
          boolean call = k <= b && t.get(k).type() == PrismioTypes.LPAREN;
          int argCount = 0;
          int close = k;
          if (call) {
            close = matching(k);
            argCount = argumentRanges(k, close).size();
          }
          value = member(value, name.text(), call, argCount);
          k = call ? close + 1 : k;
        } else if (tok.type() == PrismioTypes.LBRACKET) {
          int close = matching(k);
          boolean slice = false;
          for (int m = k + 1; m < close; m++) {
            if (t.get(m).type() == PrismioTypes.RANGE) {
              slice = true;
            }
          }
          value = Value.of(slice ? value.type : elementOf(value.type));
          k = close + 1;
        } else if (tok.type() == PrismioTypes.OPTIONAL) {
          k++;
        } else {
          return null;
        }
      }
      return value;
    } finally {
      depth--;
    }
  }

  private @Nullable Value primary(int a, int b, int[] next) {
    Tok x = t.get(a);
    next[0] = a + 1;
    TypeRef literal = SignatureParser.literalType(x);
    if (literal != null) {
      return Value.of(literal);
    }
    if (x.type() == PrismioTypes.LPAREN) {
      int close = matching(a);
      next[0] = close + 1;
      return Value.of(expr(a + 1, close - 1));
    }
    if (x.type() == PrismioTypes.LBRACKET) {
      int close = matching(a);
      next[0] = close + 1;
      List<int[]> elements = argumentRanges(a, close);
      TypeRef element = elements.isEmpty() ? null : expr(elements.get(0)[0], elements.get(0)[1]);
      // `[]` is a Vec (sema gives a `Vec<T>` with no initializer `[]`); a literal with
      // elements is an `Array<T, N>` until something says it is a Vec.
      if (elements.isEmpty()) {
        return Value.of(TypeRef.simple("Vec"));
      }
      return Value.of(element == null ? TypeRef.simple("Array") : TypeRef.array(element, elements.size()));
    }
    if (!x.isName()) {
      return null;
    }
    String name = x.text();
    if (name.equals("self")) {
      return Value.of(enclosingImplOwner(a));
    }
    if (name.equals("Self")) {
      TypeRef owner = enclosingImplOwner(a);
      return owner == null ? null : new Value(owner, true, null);
    }

    int k = a + 1;
    List<TypeRef> typeArgs = List.of();
    if (k <= b && t.get(k).is("<") && looksLikeType(name, a)) {
      int gt = matchingAngle(k);
      if (gt > 0) {
        typeArgs = typeArguments(k, gt);
        k = gt + 1;
      }
    }
    TypeRef asType = new TypeRef(name, typeArgs, false);

    if (k <= b && t.get(k).type() == PrismioTypes.LPAREN && !t.get(k).newlineBefore()) {
      int close = matching(k);
      next[0] = close + 1;
      if (looksLikeType(name, a)) {
        // `Type(...)` constructs one (`Map<K, V>()` calls `new`, src/sema/checker.psm).
        return Value.of(asType);
      }
      return Value.of(callResult(name, k, close));
    }
    if (k <= b && t.get(k).type() == PrismioTypes.LBRACE && looksLikeType(name, a)
        && scope.type(name) != null) {
      next[0] = matching(k) + 1;
      return Value.of(asType);
    }
    next[0] = k;
    if (typeArgs.isEmpty()) {
      TypeRef local = variable(name, a);
      if (local != null) {
        return Value.of(local);
      }
      ModuleSummary.Global global = scope.global(name);
      if (global != null && global.type() != null) {
        return Value.of(global.type());
      }
      for (ModuleSummary.Import anImport : imports) {
        if (name.equals(anImport.alias())) {
          return new Value(null, false, anImport.path());
        }
      }
    }
    if (looksLikeType(name, a)) {
      return new Value(asType, true, null);
    }
    return null;
  }

  /** A member of {@code value}'s type, read or called with {@code argCount} arguments. */
  private @Nullable Value member(@NotNull Value value, @NotNull String name, boolean call, int argCount) {
    if (value.module != null) {
      for (FnSig fn : scope.functions(name)) {
        if (fn.module().equals(value.module) && fn.returnType() != null) {
          return Value.of(fn.returnType());
        }
      }
      return null;
    }
    if (value.type == null) {
      return null;
    }
    List<Member> members = value.isStatic ? scope.staticMembers(value.type) : scope.instanceMembers(value.type);
    Member best = null;
    for (Member m : members) {
      if (!m.name().equals(name) || m.isCall() != call && m.kind() != Member.Kind.VARIANT) {
        continue;
      }
      if (best == null) {
        best = m;
      }
      if (call && m.function() != null
          && m.function().callParams(m.origin() == Member.Origin.EXTENSION).size() == argCount) {
        best = m;
        break;
      }
    }
    if (best == null) {
      return null;
    }
    if (best.kind() == Member.Kind.VARIANT) {
      return Value.of(value.type);
    }
    return Value.of(best.type());
  }

  /**
   * What calling the free function {@code name} returns. The overload is chosen by
   * argument count, and the function's type parameters are bound from the
   * arguments' types where those are known: {@code vecOf(1, 2)} is a {@code Vec<Int>}.
   */
  private @Nullable TypeRef callResult(@NotNull String name, int open, int close) {
    List<int[]> args = argumentRanges(open, close);
    List<FnSig> overloads = scope.functions(name);
    FnSig chosen = null;
    for (FnSig fn : overloads) {
      if (fn.params().size() == args.size()) {
        chosen = fn;
        break;
      }
    }
    if (chosen == null && !overloads.isEmpty()) {
      chosen = overloads.get(0);
    }
    if (chosen == null || chosen.returnType() == null) {
      return null;
    }
    if (chosen.typeParameters().isEmpty()) {
      return chosen.returnType();
    }
    Map<String, TypeRef> bindings = new HashMap<>();
    for (int k = 0; k < Math.min(args.size(), chosen.params().size()); k++) {
      TypeRef pattern = chosen.params().get(k).type();
      if (pattern == null) {
        continue;
      }
      TypeRef actual = expr(args.get(k)[0], args.get(k)[1]);
      if (actual != null) {
        TypeRef.unify(pattern, actual, chosen.typeParameters(), bindings);
      }
    }
    return chosen.returnType().substitute(bindings);
  }

  // ------------------------------------------------------------ expressions

  /** The type of the expression {@code [a, b]}, or null. */
  private @Nullable TypeRef expr(int a, int b) {
    if (a > b || ++depth > MAX_DEPTH) {
      if (a <= b) {
        depth--;
      }
      return null;
    }
    try {
      Tok first = t.get(a);
      if (first.is("if") || first.is("match") || first.is("spawn") || first.is("region")) {
        return null;
      }
      if (first.is("-") || first.is("+")) {
        return expr(a + 1, b);
      }
      if (first.is("not") || first.is("!")) {
        return TypeRef.BOOL;
      }
      int lastAs = -1;
      int operator = -1;
      int level = 0;
      for (int k = a; k <= b; k++) {
        Tok tok = t.get(k);
        IElementType type = tok.type();
        if (type == PrismioTypes.LPAREN || type == PrismioTypes.LBRACKET || type == PrismioTypes.LBRACE) {
          level++;
          continue;
        }
        if (type == PrismioTypes.RPAREN || type == PrismioTypes.RBRACKET || type == PrismioTypes.RBRACE) {
          level--;
          continue;
        }
        if (level != 0) {
          continue;
        }
        if (tok.is("<") && k > a && t.get(k - 1).isName() && looksLikeType(t.get(k - 1).text(), k - 1)) {
          int gt = matchingAngle(k);
          if (gt > 0 && gt <= b) {
            k = gt;
            continue;
          }
        }
        if (tok.is("as")) {
          lastAs = k;
        } else if (type == PrismioTypes.RELATIONAL_OP || type == PrismioTypes.LOGICAL_OP
            || tok.is("and") || tok.is("or")) {
          return TypeRef.BOOL;
        } else if (type == PrismioTypes.RANGE) {
          return TypeRef.simple("Range");
        } else if (operator < 0 && k > a && (type == PrismioTypes.ARITHMETIC_OP
            || type == PrismioTypes.BITWISE_OP || type == PrismioTypes.SHIFT_OP)) {
          operator = k;
        }
      }
      if (lastAs > 0) {
        return typeAt(lastAs + 1, b);
      }
      if (operator > 0) {
        TypeRef left = expr(a, operator - 1);
        if (t.get(operator).is("+")) {
          TypeRef right = expr(operator + 1, b);
          if (TypeRef.STRING.equals(left) || TypeRef.STRING.equals(right)) {
            return TypeRef.STRING;
          }
          return left != null ? left : right;
        }
        return left;
      }
      Value value = chain(a, b);
      return value == null || value.isStatic || value.module != null ? null : value.type;
    } finally {
      depth--;
    }
  }

  /** A type written in the source at {@code [a, b]}: after {@code :}, {@code as} or {@code ->}. */
  private @Nullable TypeRef typeAt(int a, int b) {
    StringBuilder text = new StringBuilder();
    for (int k = a; k <= b; k++) {
      text.append(t.get(k).text());
    }
    return TypeRef.parse(text.toString());
  }

  private @NotNull List<TypeRef> typeArguments(int lt, int gt) {
    TypeRef whole = typeAt(lt - 1, gt);
    return whole == null ? List.of() : whole.args();
  }

  private static @Nullable TypeRef elementOf(@Nullable TypeRef type) {
    if (type == null) {
      return null;
    }
    String base = type.base();
    if (base.equals("String")) {
      return TypeRef.CHAR;
    }
    if (base.equals("Map") && type.args().size() == 2) {
      return type.args().get(1);
    }
    return type.args().isEmpty() ? null : type.args().get(0);
  }

  // ------------------------------------------------------------ names

  /**
   * The type of the local {@code name} as seen from token {@code from}: its
   * nearest preceding {@code let} in an enclosing block, a parameter of the
   * enclosing function, or the binder of an enclosing {@code for}.
   */
  private @Nullable TypeRef variable(@NotNull String name, int from) {
    int level = 0;
    for (int k = from - 1; k >= 0; k--) {
      Tok tok = t.get(k);
      if (tok.type() == PrismioTypes.RBRACE) {
        level++;
        continue;
      }
      if (tok.type() == PrismioTypes.LBRACE) {
        if (level > 0) {
          level--;
          continue;
        }
        TypeRef header = bindingInHeader(name, k);
        if (header != null) {
          return header;
        }
        continue;
      }
      if (level == 0 && tok.is("let") && tok.type() == PrismioTypes.KEYWORD) {
        int n = k + 1;
        if (n < t.size() && t.get(n).is("mut")) {
          n++;
        }
        if (n < t.size() && t.get(n).is(name)) {
          return letType(n);
        }
      }
    }
    return null;
  }

  /** The type the {@code let} whose name is at {@code n} gives it. */
  private @Nullable TypeRef letType(int n) {
    int k = n + 1;
    if (k < t.size() && t.get(k).type() == PrismioTypes.COLON) {
      int end = k + 1;
      while (end < t.size() && t.get(end).type() != PrismioTypes.ASSIGNMENT_OP
          && !(end > k + 1 && t.get(end).newlineBefore())) {
        end++;
      }
      return typeAt(k + 1, end - 1);
    }
    if (k < t.size() && t.get(k).type() == PrismioTypes.ASSIGNMENT_OP) {
      return expr(k + 1, statementEnd(k + 1));
    }
    return null;
  }

  /**
   * A binding the header of the block opened at {@code brace} introduces: a
   * parameter of {@code fn name(params) {}} or the binder of {@code for x in xs {}}.
   */
  private @Nullable TypeRef bindingInHeader(@NotNull String name, int brace) {
    int headerStart = brace - 1;
    int limit = Math.max(0, brace - 400);
    while (headerStart > limit) {
      Tok tok = t.get(headerStart);
      if (tok.type() == PrismioTypes.LBRACE || tok.type() == PrismioTypes.RBRACE) {
        break;
      }
      if (tok.is("fn") || tok.is("prop") || tok.is("for")) {
        break;
      }
      headerStart--;
    }
    if (headerStart < 0 || headerStart >= brace) {
      return null;
    }
    Tok head = t.get(headerStart);
    if (head.is("fn") || head.is("prop")) {
      int open = headerStart + 1;
      while (open < brace && t.get(open).type() != PrismioTypes.LPAREN) {
        open++;
      }
      if (open >= brace) {
        return null;
      }
      int close = matching(open);
      for (int[] param : argumentRanges(open, close)) {
        int p = param[0];
        while (p <= param[1] && (t.get(p).is("inout") || t.get(p).is("sink") || t.get(p).is("mut"))) {
          p++;
        }
        if (p <= param[1] && t.get(p).is(name)) {
          if (name.equals("self")) {
            return enclosingImplOwner(headerStart);
          }
          if (p + 1 <= param[1] && t.get(p + 1).type() == PrismioTypes.COLON) {
            return typeAt(p + 2, param[1]);
          }
        }
      }
      return null;
    }
    if (head.is("for")) {
      int in = headerStart + 1;
      while (in < brace && !t.get(in).is("in")) {
        in++;
      }
      if (in == headerStart + 2 && t.get(headerStart + 1).is(name)) {
        TypeRef iterable = expr(in + 1, brace - 1);
        if (iterable == null) {
          return null;
        }
        if (iterable.name().equals("Range")) {
          return TypeRef.INT;
        }
        if (iterable.base().equals("Channel")) {
          return iterable.args().isEmpty() ? null : iterable.args().get(0);
        }
        return elementOf(iterable);
      }
    }
    return null;
  }

  /** The type an enclosing {@code impl} block attaches its members to. */
  private @Nullable TypeRef enclosingImplOwner(int from) {
    int level = 0;
    for (int k = from - 1; k >= 0; k--) {
      Tok tok = t.get(k);
      if (tok.type() == PrismioTypes.RBRACE) {
        level++;
      } else if (tok.type() == PrismioTypes.LBRACE) {
        if (level > 0) {
          level--;
          continue;
        }
        int head = k - 1;
        while (head >= 0 && !t.get(head).is("impl") && t.get(head).type() != PrismioTypes.RBRACE
            && t.get(head).type() != PrismioTypes.LBRACE) {
          head--;
        }
        if (head >= 0 && t.get(head).is("impl")) {
          int start = head + 1;
          if (start < k && t.get(start).is("<")) {
            start = matchingAngle(start) + 1;
          }
          int forAt = -1;
          for (int m = start; m < k; m++) {
            if (t.get(m).is("for")) {
              forAt = m;
            }
          }
          return typeAt(forAt >= 0 ? forAt + 1 : start, k - 1);
        }
      }
    }
    return null;
  }

  /**
   * Whether {@code name} at {@code at} names a type rather than a value: a declared
   * type, a built-in one, or a capitalised name that is not a local. Prismio's
   * types are capitalised and its values are not, which is the convention
   * {@code CODE_STYLE.md} and the whole standard library keep.
   */
  private boolean looksLikeType(@NotNull String name, int at) {
    if (scope.type(name) != null || PrismioWords.BUILTIN_TYPES.contains(name)) {
      return true;
    }
    return !name.isEmpty() && Character.isUpperCase(name.charAt(0)) && variable(name, at) == null
        && scope.global(name) == null;
  }

  // ------------------------------------------------------------ tokens

  private static boolean isLiteral(Tok tok) {
    return SignatureParser.literalType(tok) != null;
  }

  /** The last token of the statement starting at {@code from}: where a line ends outside any bracket. */
  private int statementEnd(int from) {
    int level = 0;
    for (int k = from; k < t.size(); k++) {
      Tok tok = t.get(k);
      if (k > from && level == 0 && tok.newlineBefore() && !continuesLine(t.get(k - 1), tok)) {
        return k - 1;
      }
      IElementType type = tok.type();
      if (type == PrismioTypes.LPAREN || type == PrismioTypes.LBRACKET || type == PrismioTypes.LBRACE) {
        level++;
      } else if (type == PrismioTypes.RPAREN || type == PrismioTypes.RBRACKET || type == PrismioTypes.RBRACE) {
        if (level == 0) {
          return k - 1;
        }
        level--;
      }
    }
    return t.size() - 1;
  }

  /** A line break inside an expression: after an operator or comma, or before a {@code .}. */
  private static boolean continuesLine(Tok before, Tok after) {
    IElementType b = before.type();
    return after.type() == PrismioTypes.DOT || b == PrismioTypes.DOT || b == PrismioTypes.COMMA
        || b == PrismioTypes.ARITHMETIC_OP || b == PrismioTypes.ASSIGNMENT_OP
        || b == PrismioTypes.RELATIONAL_OP || b == PrismioTypes.LOGICAL_OP || before.is("and")
        || before.is("or");
  }

  /** The {@code [first, last]} token ranges of the comma-separated items between {@code open} and {@code close}. */
  private @NotNull List<int[]> argumentRanges(int open, int close) {
    List<int[]> ranges = new ArrayList<>();
    int level = 0;
    int start = open + 1;
    for (int k = open + 1; k < close; k++) {
      IElementType type = t.get(k).type();
      if (type == PrismioTypes.LPAREN || type == PrismioTypes.LBRACKET || type == PrismioTypes.LBRACE) {
        level++;
      } else if (type == PrismioTypes.RPAREN || type == PrismioTypes.RBRACKET || type == PrismioTypes.RBRACE) {
        level--;
      } else if (type == PrismioTypes.COMMA && level == 0) {
        if (k > start) {
          ranges.add(new int[] {start, k - 1});
        }
        start = k + 1;
      }
    }
    if (close > start) {
      ranges.add(new int[] {start, close - 1});
    }
    return ranges;
  }

  private int matching(int open) {
    int level = 0;
    for (int k = open; k < t.size(); k++) {
      IElementType type = t.get(k).type();
      if (type == PrismioTypes.LPAREN || type == PrismioTypes.LBRACKET || type == PrismioTypes.LBRACE) {
        level++;
      } else if (type == PrismioTypes.RPAREN || type == PrismioTypes.RBRACKET || type == PrismioTypes.RBRACE) {
        level--;
        if (level == 0) {
          return k;
        }
      }
    }
    return t.size() - 1;
  }

  private int matchingBackward(int close) {
    int level = 0;
    for (int k = close; k >= 0; k--) {
      IElementType type = t.get(k).type();
      if (type == PrismioTypes.RPAREN || type == PrismioTypes.RBRACKET || type == PrismioTypes.RBRACE) {
        level++;
      } else if (type == PrismioTypes.LPAREN || type == PrismioTypes.LBRACKET || type == PrismioTypes.LBRACE) {
        level--;
        if (level == 0) {
          return k;
        }
      }
    }
    return -1;
  }

  /** The {@code >} closing the {@code <} at {@code lt}, counting {@code >>} as two; -1 when there is none. */
  private int matchingAngle(int lt) {
    int level = 0;
    for (int k = lt; k < t.size() && k < lt + 64; k++) {
      Tok tok = t.get(k);
      if (tok.is("<")) {
        level++;
      } else if (tok.is(">")) {
        level--;
      } else if (tok.is(">>")) {
        level -= 2;
      } else if (!(tok.isName() || tok.type() == PrismioTypes.COMMA || tok.type() == PrismioTypes.OPTIONAL
          || tok.type() == PrismioTypes.LBRACKET || tok.type() == PrismioTypes.RBRACKET
          || tok.type() == PrismioTypes.COLON || tok.is("+") || tok.type() == PrismioTypes.INTEGER)) {
        return -1;
      }
      if (level <= 0) {
        return k;
      }
    }
    return -1;
  }

  private int matchingAngleBackward(int gt) {
    int level = 0;
    for (int k = gt; k >= 0 && k > gt - 64; k--) {
      Tok tok = t.get(k);
      if (tok.is(">")) {
        level++;
      } else if (tok.is(">>")) {
        level += 2;
      } else if (tok.is("<")) {
        level--;
        if (level == 0) {
          return k;
        }
      } else if (!(tok.isName() || tok.type() == PrismioTypes.COMMA || tok.type() == PrismioTypes.OPTIONAL
          || tok.type() == PrismioTypes.LBRACKET || tok.type() == PrismioTypes.RBRACKET)) {
        return -1;
      }
    }
    return -1;
  }
}
