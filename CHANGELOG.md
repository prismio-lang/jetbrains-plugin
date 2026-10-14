# Changelog

What changes in the Prismio IntelliJ plugin. The plugin's lexers and keyword tables are
hand-written and follow the compiler (`src/lexer`, `src/parse`), so an entry here usually
names the compiler change it follows. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

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
