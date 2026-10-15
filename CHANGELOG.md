# Changelog

What changes in the Prismio IntelliJ plugin. The plugin's lexers and keyword tables are
hand-written and follow the compiler (`src/lexer`, `src/parse`), so an entry here usually
names the compiler change it follows. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

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
