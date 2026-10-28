# Changelog

What changes in the Prismio IntelliJ plugin. The plugin's lexers and keyword tables are
hand-written and follow the compiler (`src/lexer`, `src/parse`), so an entry here usually
names the compiler change it follows. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- **`codeset` was not a keyword.** The plugin's word tables did not contain it, nor `typelist`, `workload`, `setup` or
  `measure`, which the compiler lists as contextual words (the compiler's `token_tables` test had them on a "plugin does not
  know yet" allowance). Each is highlighted as a keyword where the parser reads one and never elsewhere: `codeset`, `typelist`
  and `workload` first on a line or after `public`/`private`/`internal` with a name following (`for typelist in lists` and
  `let codeset = 1` stay names), `setup` and `measure` before a block. A `codeset` is a declaration kind of its own in the
  structure view and Go to, its entries are coloured like an enum's variants, and there is a `codeset` live template. The
  allowance in the compiler's test is empty now.

- **Enter after `{` with code still on the line put that code twice as far in as it belongs.** Pressing Enter at
  `if (a) { <caret>b() }` left IntelliJ's own indent on the new line and the handler inserted a second one, so `b()`
  landed in column 8 with the caret left behind in column 4 (16 and 8 inside a function). `PrismioEnterHandler` sets the
  line's indent now and does not add to it, and the line above loses the space it ended with.
- **Reformatting turned `Ptr<mut T>` into `Ptr < mut T >`.** `mut` is a keyword, and the scan that tells a type-argument
  list from a comparison only accepted names, so it read the `<` as an operator.

### Added

- **Reformat Code and range formatting come from the server.** `formatting` is in `PrismioLspFeatures.SERVED`, so the IDE
  formats with `prismio fmt` (about 9 ms for a 6,000-line file), the same rules as the command line and every other
  editor. `PrismioBlock` stays: it gives the indent of the line being typed, which a server cannot, and formats when the
  server is off or no compiler is found.

- **An `if` that has an `else` is formatted over several lines.** Reformat Code puts each branch of
  `if (a) { b() } else { c() }`, and of an `else if` chain, on lines of their own (`} else {` stays together); an empty
  branch stays `{ }`, and an `if` with no `else` is left as written, on one line or several. `PrismioBlock`.

- **The compiler's language server.** The plugin starts `prismio lsp` through the platform's LSP client
  (`io.prismio.lsp`; loaded only where the platform has one) and takes diagnostics and the import quick fix from it,
  for `.psm` sources and `build.ums` manifests. Errors now come from the program a file belongs to (manifest targets
  tried in turn, a program that imports the module, the file alone) with every unsaved buffer, and a manifest gets the
  compiler's own `UMS####` diagnostics. Setting: **Use the Prismio language server** (on by default). Which other
  server features the editor uses is `PrismioLspFeatures.SERVED`; the platform client has a customizer for every one
  the server advertises, so each moves over in the commit that deletes the Java that answered it.

- **Hover, inlay hints, parameter info and completion come from the server** (`PrismioLspFeatures.SERVED`). Hover and
  Quick Documentation show the compiler's signature and comment; hints add the parameter names at a call beside the
  type of a `let`; parameter info lists every overload; completion offers every library name with the `import` it needs,
  `import` and `from` lines, types, `Type.` statics, a typed pointer's fields, an optional's functions and the freestanding
  builtins. The inline rendering of `/** */` comments stays the plugin's. **Setting the language server off, or changing the
  compiler path, now stops and restarts it at once** (a settings topic; before, nothing changed until the IDE restarted),
  and the server is started with `PRISMIO_INTERNAL_HOSTED` as VS Code does.
- `PrismioServerEditorFeaturesTest`, `PrismioServerHoverTest`, `PrismioServerCompletionTest` (the plugin's 35 completion
  tests, answered by the server) and `PrismioServerNavigationTest` (4 of 7 pass; see docs/LSP_PLAN.md in the compiler
  repository). They need a toolchain with the compiler repository's 01ed914 or later.

### Removed

- **`PrismioDocumentationProvider`, `PrismioTypeHintsProvider`, `PrismioParameterInfoHandler`, `completion/` and
  `UmsCompletionContributor`**, each after a differential or test held the server to what it answered: 1,219 hover names
  (the server agreed on every one it answers; ten names the compiler's index had no record of are now indexed), 320 hints
  (the server corrected ten the plugin got wrong), 1,910 call positions, and 35 completion tests (24 failed at the start).
  The differentials are in Git history (`ab81b99`).
- `UmsAnnotator`'s "not a name UMS recognises" warning: it repeated UMS2004 and was wrong whenever the toolchain was
  newer than the plugin. The colours stay.
- **The external annotator and `PrismioCheckTarget`**, held to the server by a differential test on 344 files before
  they went (same codes, lines and messages; the server says more only in type errors, because it checks with
  `--partial`). **`UmsValueChecks`**: the compiler judges a manifest now; it had drifted (it rejected `.cpp` native
  sources the build accepts). `UmsAnnotator` keeps the colours and the weak warning for an unknown name.

- **Function markers and `const`** (compiler 0.2.0, `src/parse/modifiers.psm`). `hot`, `noinline`, `noreturn`,
  `const`, `inline(always)` and `inline(never)` highlight as contextual keywords and are offered by completion.
  `hot`, `const`, `noinline` and `noreturn` are keywords only before another word, so a variable called `hot` or
  `const` stays a name. The signature parser reads the argument of `inline(...)`, so a declaration written with
  any marker keeps its symbol, in an `impl` block too (it used to read only `cold`, `inline` and `async` there).
  `const NAME = value` is a constant: a declaration for navigation, a local for rename and find usages, a global
  for completion.

- **`Int<N>`, `UInt<N>` and the float family** (compiler 0.2.0, `src/ast/int_width.psm`,
  `src/ast/float_width.psm`).
  - `F16`, `F32`, `F64`, `F128`, `BF16` and `UInt` highlight as built-in types, and are offered by
    completion with the rest. `I32` does too: it is another spelling of `Int`, which the plugin's table
    had left out on the ground that the compiler had no such type.
  - The signature parser reads the width of `Int<24>` / `UInt<5>` as a number, not a type, and gives a
    width that has a name that name (`Int<8>` is `I8`, `Int<32>` is `Int`, `UInt<64>` is `U64`) as the
    compiler's parser does, so a parameter, field or `Vec<Int<24>>` no longer swallows the declaration
    after it.
  - The formatter keeps `Int<24>` together instead of spacing it as a comparison; `a < 24` is still one.
  - `unwrapOr` and `expect` are offered on `F32?`, `F16?`, `BF16?` and `F128?`.

- **`inline`** is a contextual keyword and a declaration modifier, beside `cold`: `inline fn probe()`,
  at top level or in an `impl` block, in any order with `export`, `naked`, `section` and `align`. The
  signature parser reads past it and completion offers it before `fn`. Follows the compiler's `inline fn`.
- **`is`, `is not` and `not`** are keywords. The compiler reads them as `==`, `!=` and `!`
  (`x is 3`, `x is not none and not done`), and the plugin's keyword set stopped at `and` and
  `or`, so they highlighted as plain names.
- **Typed pointers, `Ptr<T>`** (compiler 0.2.0, `docs/PTR_PLAN.md` stages 1 and 2).
  - `volatile` is highlighted as a contextual keyword only inside a `Ptr<...>` argument list
    with a type after it: `Ptr<volatile T>`, `Ptr<mut volatile T>`, `Ptr<volatile mut T>`. A
    variable named `volatile`, and `Vec<volatile T>`, stay identifiers.
  - The signature parser and type inference read `Ptr<mut T>`, `Ptr<volatile T>`,
    `Ptr<mut volatile T>` and `Ptr<T>?` (`TypeRef` keeps the capabilities and prints them as the
    compiler does), so a parameter, field or `let` of one no longer swallows the rest of the
    declaration.
  - `p.field` is typed as the field when `p` is a `Ptr<Struct>`, and completion offers the fields
    after `p.`. A struct stored inline is a pointer with the parent's capabilities, so
    `packet.header.magic` types through two. `Ptr<T>?` is not read through: the plugin does not
    follow `!= none` narrowing.
  - Stage 4: every typed pointer, whatever it points at, has the property `addr` (`Usize`) and the
    methods `offset`, `byteOffset`, `wrappingOffset` (each answers the receiver's own `Ptr<..>`,
    capabilities included), `offsetFrom`, `isAligned`, `sameAs` and `advance` (`Ptr<..>?`). The
    constructors `at`, `castAligned` (`Ptr<T>?`), `atUnchecked` and `cast` are offered after a
    written `Ptr<mut T>.`, and complete with the `import std.raw` they need.
  - Stage 5: reads and writes through a `Ptr<volatile T>` are ordinary (`p.field`, `load`, `store`),
    and `readVolatile()` / `writeVolatile(v)` are offered for a scalar. The atomics `atomicLoad`,
    `atomicStore`, `atomicSwap`, `atomicAdd`, `atomicSub`, `atomicAnd`, `atomicOr`, `atomicXor` and
    `atomicCompareExchange` are offered for an integer pointee only (not Float, Bool or Char), the
    writing ones on a `Ptr<mut ...>` only, each with the `import std.raw` its `AtomicOrder` needs.
  - `load` is offered after `p.` for a pointer to a scalar and not for a struct, and `store(value)`
    for a scalar behind a `Ptr<mut ...>` only (stage 3). `p.field = v` is an ordinary assignment;
    `p.field += v` is refused by the compiler (P3302), so nothing offers it. Both are declared
    once per scalar in `CompilerIntrinsics`, the one table of lowered members, and checked against
    the compiler by the same probe as `Vec`'s; stages 3-5 add their methods there.
- **async/await and structured concurrency** (compiler 0.2.0, `docs/ASYNC_PLAN.md`).
  - `async` before `fn`, `await` and `join` before a name, and `scope` before `{` are
    highlighted as keywords only where the parser reads them as one. A `scope` variable or the
    std `join(...)` function stays an identifier, so Find Usages and Go to Declaration still see it.
  - `async fn` is visible to navigation and completion at top level, after `public`/`private`/
    `internal`, and as a method in an `impl` block (`FnSig.async()`).
  - Completion offers `async` at top level, `scope` as a statement, and `await` and `join` in an
    expression.
  - Type inference: `await f(x)` is what `f` returns, and a call to an `async fn` without `await`
    has no inferred type, because it is a `Future` of that type, which the plugin does not model.
    `join t` is not typed.
- **Freestanding programs**: the compiler's `__builtin_*` family, the `export`, `naked`,
  `section(...)` and `align(n)` function modifiers and `repr(C)` and `packed` before `struct`,
  and completion and value checks for the freestanding `build.ums` target properties.

### Changed

- A visibility marker is read before `struct`, `enum`, `fn`, `let`, `prop` and the like but no
  longer before `trait` or `impl`, which the compiler refuses (P3001, "traits and impls take no
  marker").
- Type inference reads explicit type arguments on a call of a generic function, so
  `allocOf<Header>(4)` is a `Ptr<mut Header>` (std.mem's `allocOf`, `allocZeroedOf`; `reallocOf`
  and `free(Ptr<mut T>)` come from the toolchain's std like any other function, as does
  `Buffer.pointer`, a `Ptr<mut U8>?`).

- Follows `T?` replacing `Option<T>`, the `??` operator and from-imports.

### Known issues

- `UmsValueChecksTest.testNoErrorsOnAValidManifestOfARealCheckout` fails against the compiler's
  own `build.ums`: the UMS value check reports `UMS2324 native source 'runtime/llvm_bridge.cpp' is
  not a C file`, because the check still takes `native` sources to be `.c` only.

## [0.1.0]

First release: highlighting, formatting and folding for `.psm` and `build.ums`; type-aware
completion with automatic imports; compiler errors as you type with import quick fixes; go to
declaration, find usages and rename; parameter and inline type hints; run configurations from
`build.ums`; the New Project wizard and New | UMS Manifest.
