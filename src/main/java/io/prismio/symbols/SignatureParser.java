package io.prismio.symbols;

import io.prismio.psi.PrismioTypes;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads a module's declarations — signatures only, never bodies.
 *
 * <p>This is how the plugin learns what the standard library offers without
 * keeping its own copy of it. The source it reads is the toolchain's: a
 * {@code std/*.psm} in a checkout, or the interface each installed
 * {@code stdlib/*.plib} embeds. A compiler that adds, renames or removes a
 * method therefore changes completion the moment the IDE points at it.
 *
 * <p>The grammar followed is {@code src/parse/decl.psm} and
 * {@code src/parse/parser.psm}. Anything it does not recognise is skipped a
 * token at a time, and a body is skipped by brace matching, so a construct
 * added to the language later costs at most the declaration it appears in.
 */
public final class SignatureParser {

  private final CharSequence text;
  private final List<Tok> all;
  private final List<Tok> toks;
  private final String module;
  private int i;
  /** A {@code >>} closes two type-argument lists; the second close is owed here. */
  private boolean pendingClose;

  private final List<FnSig> functions = new ArrayList<>();
  private final List<TypeDecl> types = new ArrayList<>();
  private final List<ModuleSummary.Global> globals = new ArrayList<>();
  private final List<ModuleSummary.Import> imports = new ArrayList<>();

  private SignatureParser(@NotNull CharSequence text, @NotNull String module) {
    this.text = text;
    this.all = Tok.lex(text);
    this.toks = Tok.code(all);
    this.module = module;
  }

  public static @NotNull ModuleSummary parse(@NotNull CharSequence text, @NotNull String module) {
    SignatureParser parser = new SignatureParser(text, module);
    parser.parseTopLevel();
    return new ModuleSummary(module, parser.moduleDoc(), List.copyOf(parser.functions),
        List.copyOf(parser.types), List.copyOf(parser.globals), List.copyOf(parser.imports));
  }

  // ---------------------------------------------------------------- top level

  /** {@code async} was just skipped before the function about to be parsed. */
  private boolean sawAsync;

  private void parseTopLevel() {
    while (i < toks.size()) {
      int declStart = i;
      int visibility = visibilityMarker();
      skipDeclarationModifiers();
      Tok t = peek(0);
      if (t == null) {
        return;
      }
      switch (t.text()) {
        case "import" -> parseImport();
        case "from" -> parseFromImport();
        case "fn" -> parseFunction(declStart, visibility, null, List.of(), null, false);
        case "extern" -> parseExtern(declStart, visibility);
        case "struct" -> parseStruct(declStart);
        case "enum" -> parseEnum(declStart);
        case "trait" -> parseTrait(declStart);
        case "impl" -> parseImpl();
        case "let", "const" -> parseGlobal(declStart, null);
        default -> {
          if (t.type() == PrismioTypes.LBRACE) {
            skipBalanced();
          } else {
            i++;
          }
        }
      }
    }
  }

  /**
   * The words between a visibility marker and {@code fn} or {@code struct} that say
   * something about the symbol or the layout (src/parse/modifiers.psm): {@code cold}, {@code hot},
   * {@code inline}, {@code inline(always)}, {@code inline(never)}, {@code noinline}, {@code noreturn},
   * {@code const}, {@code export}, {@code naked}, {@code section("...")} and {@code align(n)} before
   * {@code fn}; {@code repr(C)} and {@code packed} before {@code struct}. Each counts
   * only when a declaration word or another modifier follows it, so a variable named
   * {@code export} is still a variable.
   */
  private void skipDeclarationModifiers() {
    while (isModifierAt(i)) {
      if (at("async")) {
        sawAsync = true;
      }
      Tok next = peek(1);
      if (next != null && next.type() == PrismioTypes.LPAREN) {
        i = matching(i + 1) + 1;
      } else {
        i++;
      }
    }
  }

  private boolean isModifierAt(int at) {
    if (at >= toks.size()) {
      return false;
    }
    Tok t = toks.get(at);
    int after = at + 1;
    switch (t.text()) {
      case "cold", "hot", "noinline", "noreturn", "const", "export", "naked", "packed", "async" -> {
      }
      case "inline" -> {
        // `inline(always)` and `inline(never)` carry an argument; a plain `inline` does not.
        if (after < toks.size() && toks.get(after).type() == PrismioTypes.LPAREN) {
          after = matching(after) + 1;
        }
      }
      case "section", "align", "repr" -> {
        if (after >= toks.size() || toks.get(after).type() != PrismioTypes.LPAREN) {
          return false;
        }
        after = matching(after) + 1;
      }
      default -> {
        return false;
      }
    }
    if (after >= toks.size()) {
      return false;
    }
    Tok follower = toks.get(after);
    return follower.text().equals("fn") || follower.text().equals("struct")
        || follower.text().equals("prop") || isModifierAt(after);
  }

  /**
   * {@code public}, {@code private} or {@code internal} before a declaration word, or -1. Enums,
   * structs and fields take one; {@code trait} and {@code impl} do not (P3001: "traits and impls
   * take no marker"), so a marker before either is not skipped as one.
   */
  private int visibilityMarker() {
    Tok t = peek(0);
    Tok next = peek(1);
    if (t == null || next == null) {
      return -1;
    }
    int level = switch (t.text()) {
      case "public" -> FnSig.PUBLIC;
      case "private" -> FnSig.PRIVATE;
      case "internal" -> FnSig.INTERNAL;
      default -> -1;
    };
    if (level < 0) {
      return -1;
    }
    switch (next.text()) {
      case "fn", "extern", "struct", "enum", "let", "const", "prop", "cold", "hot", "inline", "noinline",
          "noreturn", "async", "export", "naked", "section", "align", "repr", "packed" -> {
        i++;
        return level;
      }
      default -> {
        return -1;
      }
    }
  }

  /**
   * {@code from std import {io, string as s}} and {@code from std import *}: the
   * grouped and wildcard imports with the directory first. The same imports the
   * other order makes, so everything downstream reads one list.
   */
  private void parseFromImport() {
    Tok first = toks.get(i);
    int start = first.start();
    Tok next = peek(1);
    // A `from` that is not followed by a directory is an identifier of some
    // other statement, which `parseTopLevel` skips a token at a time.
    if (next == null || !(next.isName() || next.type() == PrismioTypes.KEYWORD)) {
      i++;
      return;
    }
    i++;
    String dir = parseDirectory();
    if (dir == null || !at("import")) {
      return;
    }
    i++;
    if (atType(PrismioTypes.LBRACE)) {
      List<String[]> entries = parseGroupEntries();
      int end = i > 0 ? toks.get(i - 1).end() : text.length();
      for (String[] entry : entries) {
        imports.add(new ModuleSummary.Import(dir + "." + entry[0], false, entry[1], start, end, true));
      }
      return;
    }
    if (atType(PrismioTypes.ARITHMETIC_OP) && at("*")) {
      imports.add(new ModuleSummary.Import(dir, true, null, start, toks.get(i).end(), false));
      i++;
    }
  }

  /** The braces of an import group and the names in them, with each one's alias. */
  private @NotNull List<String[]> parseGroupEntries() {
    i++;
    List<String[]> entries = new ArrayList<>();
    while (i < toks.size() && !atType(PrismioTypes.RBRACE)) {
      Tok entry = toks.get(i);
      if (entry.isName() || entry.type() == PrismioTypes.KEYWORD) {
        String alias = null;
        i++;
        if (at("as") && peek(1) != null) {
          alias = peek(1).text();
          i += 2;
        }
        entries.add(new String[] {entry.text(), alias});
      } else {
        i++;
      }
    }
    i++;
    return entries;
  }

  private void parseImport() {
    Tok first = toks.get(i);
    int start = first.start();
    i++;
    if (atType(PrismioTypes.LBRACE)) {
      // import {io, string as s} from std
      List<String[]> entries = parseGroupEntries();
      int end = toks.get(i - 1).end();
      String dir = parseFrom();
      if (dir != null) {
        end = toks.get(i - 1).end();
        for (String[] entry : entries) {
          imports.add(new ModuleSummary.Import(dir + "." + entry[0], false, entry[1], start, end, true));
        }
      }
      return;
    }
    if (atType(PrismioTypes.ARITHMETIC_OP) && at("*")) {
      i++;
      String dir = parseFrom();
      if (dir != null) {
        imports.add(new ModuleSummary.Import(dir, true, null, start, toks.get(i - 1).end(), false));
      }
      return;
    }
    StringBuilder path = new StringBuilder();
    boolean wildcard = false;
    int end = start;
    while (i < toks.size()) {
      Tok segment = toks.get(i);
      if (segment.newlineBefore() && path.length() > 0) {
        break;
      }
      if (segment.isName() || segment.type() == PrismioTypes.KEYWORD && !segment.is("as")) {
        path.append(segment.text());
        end = segment.end();
        i++;
        if (atType(PrismioTypes.DOT) && !peek(0).newlineBefore()) {
          path.append('.');
          i++;
          if (at("*")) {
            wildcard = true;
            path.setLength(path.length() - 1);
            end = toks.get(i).end();
            i++;
            break;
          }
          continue;
        }
      }
      break;
    }
    String alias = null;
    if (at("as") && peek(1) != null && !peek(0).newlineBefore()) {
      alias = peek(1).text();
      end = peek(1).end();
      i += 2;
    }
    if (path.length() > 0) {
      imports.add(new ModuleSummary.Import(path.toString(), wildcard, alias, start, end, false));
    }
  }

  private @Nullable String parseFrom() {
    if (!at("from")) {
      return null;
    }
    i++;
    return parseDirectory();
  }

  /** The dotted directory after {@code from}: {@code std}, or {@code a.b}. */
  private @Nullable String parseDirectory() {
    StringBuilder dir = new StringBuilder();
    while (i < toks.size()) {
      Tok segment = toks.get(i);
      if (!segment.isName() && segment.type() != PrismioTypes.KEYWORD) {
        break;
      }
      dir.append(segment.text());
      i++;
      if (atType(PrismioTypes.DOT)) {
        dir.append('.');
        i++;
        continue;
      }
      break;
    }
    return dir.length() == 0 ? null : dir.toString();
  }

  private void parseExtern(int declStart, int visibility) {
    i++;
    if (at("fn")) {
      FnSig sig = parseFunction(declStart, visibility, null, List.of(), null, true);
      if (sig == null) {
        return;
      }
    } else if (at("let")) {
      parseGlobal(declStart, null);
    }
  }

  // ------------------------------------------------------------- functions

  /**
   * {@code fn name<T>(params) -> Ret [contract] [where ...] [{ body }]}, with the
   * caret on {@code fn} or {@code prop}. Records the result and returns it.
   */
  private @Nullable FnSig parseFunction(int declStart, int visibility, @Nullable TypeRef owner,
      @NotNull List<String> ownerParameters, @Nullable String trait, boolean external) {
    boolean property = at("prop");
    boolean isAsync = sawAsync;
    sawAsync = false;
    i++;
    Tok nameTok = peek(0);
    if (nameTok == null || !(nameTok.isName() || nameTok.type() == PrismioTypes.KEYWORD)) {
      return null;
    }
    i++;
    List<String> typeParameters = atText("<") ? parseTypeParameters() : List.of();
    List<FnSig.Param> params = new ArrayList<>();
    boolean hasSelf = false;
    if (atType(PrismioTypes.LPAREN)) {
      i++;
      while (i < toks.size() && !atType(PrismioTypes.RPAREN)) {
        String mode = "";
        while (at("inout") || at("sink") || at("mut")) {
          if (!at("mut")) {
            mode = toks.get(i).text();
          }
          i++;
        }
        Tok paramName = peek(0);
        if (paramName == null) {
          break;
        }
        if (paramName.is("self")) {
          hasSelf = true;
          i++;
          if (atType(PrismioTypes.COLON)) {
            i++;
            readType();
          }
        } else if (paramName.isName() || paramName.type() == PrismioTypes.KEYWORD) {
          i++;
          TypeRef type = null;
          if (atType(PrismioTypes.COLON)) {
            i++;
            type = readType();
          }
          params.add(new FnSig.Param(paramName.text(), type, mode));
        }
        // Past anything this did not understand, to the next parameter.
        while (i < toks.size() && !atType(PrismioTypes.COMMA) && !atType(PrismioTypes.RPAREN)) {
          if (atType(PrismioTypes.LPAREN) || atType(PrismioTypes.LBRACKET) || atType(PrismioTypes.LBRACE)) {
            skipBalanced();
          } else {
            i++;
          }
        }
        if (atType(PrismioTypes.COMMA)) {
          i++;
        }
      }
      i++;
    }
    TypeRef returnType = null;
    if (atType(PrismioTypes.ARROW)) {
      i++;
      returnType = readType();
    }
    // The FFI contract, a `where` clause: whatever sits between the signature
    // and the body. A body always opens on the signature's line or the next.
    while (i < toks.size() && !atType(PrismioTypes.LBRACE) && !atType(PrismioTypes.RBRACE)
        && !startsDeclaration(toks.get(i))) {
      if (atType(PrismioTypes.LPAREN)) {
        skipBalanced();
      } else {
        i++;
      }
    }
    if (atType(PrismioTypes.LBRACE)) {
      skipBalanced();
    }
    int level = visibility >= 0 ? visibility : FnSig.PRIVATE;
    FnSig sig = new FnSig(nameTok.text(), module, owner, ownerParameters, trait, typeParameters,
        List.copyOf(params), returnType, hasSelf, property, external, isAsync, level, docBefore(declStart),
        nameTok.start());
    functions.add(sig);
    return sig;
  }

  /**
   * A declaration word at the start of a line ends whatever came before it. That
   * is what stops a bodiless {@code extern fn} from swallowing the next declaration.
   */
  private static boolean startsDeclaration(Tok t) {
    if (!t.newlineBefore()) {
      return false;
    }
    return switch (t.text()) {
      case "fn", "extern", "struct", "enum", "trait", "impl", "let", "const", "import", "public", "private",
          "internal", "prop", "cold", "hot", "inline", "noinline", "noreturn", "async", "export", "naked",
          "section", "align", "repr", "packed" -> true;
      default -> false;
    };
  }

  /** {@code <T: Copy, U>} — the names only; bounds do not change what a member returns. */
  private @NotNull List<String> parseTypeParameters() {
    List<String> names = new ArrayList<>();
    i++;
    int depth = 1;
    boolean expectName = true;
    while (i < toks.size() && depth > 0) {
      Tok t = toks.get(i);
      if (t.is("<")) {
        depth++;
      } else if (t.is(">")) {
        depth--;
      } else if (t.is(">>")) {
        depth -= 2;
      } else if (t.type() == PrismioTypes.COMMA && depth == 1) {
        expectName = true;
        i++;
        continue;
      } else if (t.type() == PrismioTypes.LBRACE || startsDeclaration(t)) {
        break;
      } else if (t.type() == PrismioTypes.LPAREN || t.type() == PrismioTypes.RPAREN) {
        // A closure bound, `F: Fn(T) -> U`: its parentheses are inside the list, and
        // the `->` is an arrow, not a `>` that closes it.
        i++;
        continue;
      } else if (expectName && depth == 1 && t.isName()) {
        names.add(t.text());
      }
      expectName = false;
      i++;
    }
    return names;
  }

  // ------------------------------------------------------------------ types

  /**
   * One type, from the caret: a name with arguments, {@code [T]}, {@code T?},
   * {@code dyn Trait} or {@code fn(A) -> B}. Answers null, having consumed
   * nothing it could not read, when there is no type here.
   */
  private @Nullable TypeRef readType() {
    Tok t = peek(0);
    if (t == null) {
      return null;
    }
    if (t.is("dyn") || t.is("inout") || t.is("sink") || t.is("unique")) {
      i++;
      return readType();
    }
    if (t.type() == PrismioTypes.LBRACKET) {
      i++;
      TypeRef element = readType();
      while (i < toks.size() && !atType(PrismioTypes.RBRACKET)) {
        i++;
      }
      i++;
      return element == null ? null : optionalSuffix(TypeRef.of("Array", element));
    }
    if (t.is("fn")) {
      // A function type has no members to complete; read past it.
      i++;
      if (atType(PrismioTypes.LPAREN)) {
        skipBalanced();
      }
      if (atType(PrismioTypes.ARROW)) {
        i++;
        readType();
      }
      return TypeRef.simple("fn");
    }
    if (!t.isName()) {
      return null;
    }
    StringBuilder name = new StringBuilder(t.text());
    i++;
    while (atType(PrismioTypes.DOT) && peek(1) != null && peek(1).isName()) {
      name.append('.').append(peek(1).text());
      i += 2;
    }
    List<TypeRef> args = new ArrayList<>();
    boolean mutable = false;
    boolean isVolatile = false;
    // `Int<24>`, `UInt<5>`: the argument is a width in bits, not a type.
    if ((name.toString().equals("Int") || name.toString().equals("UInt")) && atText("<")
        && peek(1) != null && peek(1).type() == PrismioTypes.INTEGER) {
      String width = peek(1).text();
      i += 2;
      if (atText(">")) {
        i++;
      } else if (atText(">>")) {
        i++;
        pendingClose = true;
      }
      return optionalSuffix(TypeRef.integer(name.toString().equals("UInt"), width));
    }
    if (atText("<")) {
      i++;
      if (name.toString().equals(TypeRef.POINTER)) {
        // `Ptr<mut volatile T>`: capabilities before the pointee, in either order (parsePtrQualifiers).
        while (true) {
          Tok word = peek(0);
          Tok after = peek(1);
          if (word != null && word.is(TypeRef.MUT) && word.type() == PrismioTypes.KEYWORD) {
            mutable = true;
          } else if (word != null && word.is(TypeRef.VOLATILE) && after != null
              && (after.isName() || after.type() == PrismioTypes.KEYWORD
                  || after.type() == PrismioTypes.LBRACKET)) {
            isVolatile = true;
          } else {
            break;
          }
          i++;
        }
      }
      while (i < toks.size()) {
        TypeRef arg = readType();
        if (arg != null) {
          args.add(arg);
        }
        if (pendingClose) {
          pendingClose = false;
          break;
        }
        if (atType(PrismioTypes.COMMA)) {
          i++;
          continue;
        }
        if (atText(">")) {
          i++;
          break;
        }
        if (atText(">>")) {
          i++;
          pendingClose = true;
          break;
        }
        // Not a type argument list after all.
        break;
      }
    }
    return optionalSuffix(new TypeRef(name.toString(), List.copyOf(args), false,
        TypeRef.qualifiers(mutable, isVolatile)));
  }

  private TypeRef optionalSuffix(TypeRef type) {
    if (atType(PrismioTypes.OPTIONAL)) {
      i++;
      return type.withOptional(true);
    }
    return type;
  }

  private void parseStruct(int declStart) {
    i++;
    Tok name = peek(0);
    if (name == null || !name.isName()) {
      return;
    }
    i++;
    List<String> typeParameters = atText("<") ? parseTypeParameters() : List.of();
    List<FnSig.Param> fields = new ArrayList<>();
    if (atType(PrismioTypes.LBRACE)) {
      int close = matching(i);
      i++;
      while (i < close) {
        Tok field = toks.get(i);
        if ((field.isName() || field.type() == PrismioTypes.KEYWORD) && peek(1) != null
            && peek(1).type() == PrismioTypes.COLON) {
          i += 2;
          fields.add(new FnSig.Param(field.text(), readType(), ""));
        } else if (field.type() == PrismioTypes.LBRACE || field.type() == PrismioTypes.LPAREN) {
          skipBalanced();
        } else {
          i++;
        }
      }
      i = close + 1;
    }
    types.add(new TypeDecl(name.text(), module, TypeDecl.Kind.STRUCT, typeParameters,
        List.copyOf(fields), List.of(), List.of(), docBefore(declStart), name.start()));
  }

  private void parseEnum(int declStart) {
    i++;
    Tok name = peek(0);
    if (name == null || !name.isName()) {
      return;
    }
    i++;
    List<String> typeParameters = atText("<") ? parseTypeParameters() : List.of();
    List<String> variants = new ArrayList<>();
    if (atType(PrismioTypes.LBRACE)) {
      int close = matching(i);
      i++;
      boolean expect = true;
      while (i < close) {
        Tok t = toks.get(i);
        if (expect && t.isName()) {
          variants.add(t.text());
          expect = false;
          i++;
        } else if (t.type() == PrismioTypes.COMMA) {
          expect = true;
          i++;
        } else if (t.type() == PrismioTypes.LPAREN || t.type() == PrismioTypes.LBRACE) {
          skipBalanced();
        } else {
          if (t.newlineBefore() && t.isName()) {
            variants.add(t.text());
          }
          i++;
        }
      }
      i = close + 1;
    }
    types.add(new TypeDecl(name.text(), module, TypeDecl.Kind.ENUM, typeParameters, List.of(),
        List.copyOf(variants), List.of(), docBefore(declStart), name.start()));
  }

  private void parseTrait(int declStart) {
    i++;
    Tok name = peek(0);
    if (name == null || !name.isName()) {
      return;
    }
    i++;
    while (i < toks.size() && !atType(PrismioTypes.LBRACE) && !startsDeclaration(toks.get(i))) {
      i++;
    }
    if (!atType(PrismioTypes.LBRACE)) {
      return;
    }
    int close = matching(i);
    i++;
    int before = functions.size();
    TypeRef self = TypeRef.simple("Self");
    parseMembers(close, self, List.of(), name.text());
    List<FnSig> methods = List.copyOf(functions.subList(before, functions.size()));
    // A trait's own declarations are its contract, not members of a type called
    // `Self`; they reach a type through its `impl`.
    functions.subList(before, functions.size()).clear();
    i = close + 1;
    types.add(new TypeDecl(name.text(), module, TypeDecl.Kind.TRAIT, List.of(), List.of(),
        List.of(), methods, docBefore(declStart), name.start()));
  }

  /** {@code impl<T> Type<T> { ... }} or {@code impl Trait for Type { ... }}. */
  private void parseImpl() {
    i++;
    List<String> parameters = atText("<") ? parseTypeParameters() : List.of();
    TypeRef first = readType();
    String trait = null;
    TypeRef owner = first;
    if (at("for")) {
      i++;
      trait = first == null ? null : first.name();
      owner = readType();
    }
    while (i < toks.size() && !atType(PrismioTypes.LBRACE) && !startsDeclaration(toks.get(i))) {
      i++;
    }
    if (!atType(PrismioTypes.LBRACE) || owner == null) {
      return;
    }
    int close = matching(i);
    i++;
    parseMembers(close, owner, parameters, trait);
    i = close + 1;
  }

  private void parseMembers(int close, @NotNull TypeRef owner, @NotNull List<String> parameters,
      @Nullable String trait) {
    while (i < close) {
      int declStart = i;
      int visibility = visibilityMarker();
      skipDeclarationModifiers();
      if (at("fn") || at("prop")) {
        parseFunction(declStart, visibility, owner, parameters, trait, false);
      } else if (at("let") || at("const")) {
        parseGlobal(declStart, owner);
      } else if (atType(PrismioTypes.LBRACE)) {
        skipBalanced();
      } else {
        i++;
      }
    }
  }

  /** {@code let [mut] name[: Type] [= value]}, at module level or as an impl's constant. */
  private void parseGlobal(int declStart, @Nullable TypeRef owner) {
    i++;
    if (at("mut")) {
      i++;
    }
    Tok name = peek(0);
    if (name == null || !name.isName()) {
      return;
    }
    i++;
    TypeRef type = null;
    if (atType(PrismioTypes.COLON)) {
      i++;
      type = readType();
    }
    if (atType(PrismioTypes.ASSIGNMENT_OP)) {
      i++;
      Tok value = peek(0);
      if (type == null && value != null) {
        type = literalType(value);
      }
      while (i < toks.size() && !toks.get(i).newlineBefore()) {
        if (atType(PrismioTypes.LPAREN) || atType(PrismioTypes.LBRACKET) || atType(PrismioTypes.LBRACE)) {
          skipBalanced();
        } else {
          i++;
        }
      }
    }
    globals.add(new ModuleSummary.Global(name.text(), module, owner, type, docBefore(declStart),
        name.start()));
  }

  static @Nullable TypeRef literalType(@NotNull Tok t) {
    if (t.type() == PrismioTypes.STRING_LITERAL) {
      return TypeRef.STRING;
    }
    if (t.type() == PrismioTypes.INTEGER) {
      return TypeRef.INT;
    }
    if (t.type() == PrismioTypes.FLOAT) {
      return TypeRef.FLOAT;
    }
    if (t.type() == PrismioTypes.CHARACTER_LITERAL) {
      return TypeRef.CHAR;
    }
    if (t.type() == PrismioTypes.BOOLEAN || t.is("true") || t.is("false")) {
      return TypeRef.BOOL;
    }
    return null;
  }

  // ------------------------------------------------------------ utilities

  private @Nullable Tok peek(int ahead) {
    int at = i + ahead;
    return at < toks.size() ? toks.get(at) : null;
  }

  private boolean at(@NotNull String value) {
    return i < toks.size() && toks.get(i).is(value);
  }

  private boolean atText(@NotNull String value) {
    return at(value);
  }

  private boolean atType(@NotNull com.intellij.psi.tree.IElementType type) {
    return i < toks.size() && toks.get(i).type() == type;
  }

  /** Index of the bracket closing the one at {@code open}, or the last token when it is never closed. */
  private int matching(int open) {
    int depth = 0;
    for (int k = open; k < toks.size(); k++) {
      com.intellij.psi.tree.IElementType type = toks.get(k).type();
      if (type == PrismioTypes.LBRACE || type == PrismioTypes.LPAREN || type == PrismioTypes.LBRACKET) {
        depth++;
      } else if (type == PrismioTypes.RBRACE || type == PrismioTypes.RPAREN
          || type == PrismioTypes.RBRACKET) {
        depth--;
        if (depth == 0) {
          return k;
        }
      }
    }
    return toks.size() - 1;
  }

  private void skipBalanced() {
    i = matching(i) + 1;
  }

  /**
   * The comment block directly above a declaration: its documentation. A blank
   * line between the two means the comment is about something else.
   */
  private @NotNull String docBefore(int codeIndex) {
    if (codeIndex >= toks.size()) {
      return "";
    }
    int offset = toks.get(codeIndex).start();
    int k = indexInAll(offset) - 1;
    List<String> lines = new ArrayList<>();
    int boundary = offset;
    while (k >= 0 && all.get(k).isComment()) {
      Tok comment = all.get(k);
      if (newlines(comment.end(), boundary) > 1) {
        break;
      }
      lines.add(0, commentText(comment));
      boundary = comment.start();
      k--;
    }
    return String.join("\n", lines).strip();
  }

  /** The comment that opens the file, up to its first blank line. */
  private @NotNull String moduleDoc() {
    List<String> lines = new ArrayList<>();
    int boundary = 0;
    for (Tok t : all) {
      if (!t.isComment() || newlines(boundary, t.start()) > 1) {
        break;
      }
      lines.add(commentText(t));
      boundary = t.end();
    }
    return String.join("\n", lines).strip();
  }

  private int indexInAll(int offset) {
    int lo = 0;
    int hi = all.size() - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int start = all.get(mid).start();
      if (start < offset) {
        lo = mid + 1;
      } else if (start > offset) {
        hi = mid - 1;
      } else {
        return mid;
      }
    }
    return lo;
  }

  private int newlines(int from, int to) {
    int count = 0;
    for (int k = Math.max(0, from); k < Math.min(to, text.length()); k++) {
      if (text.charAt(k) == '\n') {
        count++;
      }
    }
    return count;
  }

  private static @NotNull String commentText(@NotNull Tok comment) {
    String raw = comment.text();
    if (raw.startsWith("/*")) {
      raw = raw.substring(raw.startsWith("/**") ? 3 : 2);
      if (raw.endsWith("*/")) {
        raw = raw.substring(0, raw.length() - 2);
      }
      StringBuilder out = new StringBuilder();
      for (String line : raw.split("\n")) {
        String stripped = line.strip();
        if (stripped.startsWith("*")) {
          stripped = stripped.substring(1).strip();
        }
        if (out.length() > 0) {
          out.append('\n');
        }
        out.append(stripped);
      }
      return out.toString();
    }
    int skip = 0;
    while (skip < raw.length() && raw.charAt(skip) == '/') {
      skip++;
    }
    String body = raw.substring(skip);
    return body.startsWith(" ") ? body.substring(1) : body;
  }
}
