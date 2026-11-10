# Changelog

What changes in the Prismio plugin for JetBrains IDEs. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.2.0] - 2026-11-10

The plugin is a client of the compiler's language server, `prismio lsp`, now. Everything that has to understand the
language (errors, completion, hover, hints, navigation, rename, the outline, name colours, Reformat Code) is answered by
the compiler you point the plugin at, so a newer or older toolchain shows its own functions, methods and errors. What
stays in the plugin is what has to answer before a server can (the lexer's colours, brackets, typing, the indent of the
line you are on) and what belongs to the IDE (run configurations, the New Project wizard, the manifest's colours).

It needs a JetBrains IDE from 2026.2 on and a Prismio toolchain that ships `prismio-lsp` (0.2.0).

### Added

- **The compiler's language server.** The plugin starts `prismio lsp` through the IDE's LSP client for `.psm` sources
  and `build.ums` manifests. From it come:
  - errors as you type, checked as part of the program a file belongs to (manifest targets tried in turn, then a
    program that imports the module, then the file alone) with every unsaved buffer, and the compiler's own `UMS####`
    diagnostics for a manifest;
  - the import quick fix (Alt+Enter on a missing name);
  - hover and Quick Documentation (the compiler's signature and comment), parameter info with every overload, and
    inlay hints for parameter names and the type of a `let`;
  - completion: every library name with the `import` it needs, `import` and `from` lines, types, `Type.` statics,
    members after a dot, and `build.ums` blocks and keys;
  - Go to Declaration, also into the standard library (`xs.contains(` opens `vec.psm`; the server writes out the source
    a `.plib` carries), Find Usages, Rename, Highlight Usages, the Structure view and Go to Symbol;
  - **name colours** from the server's semantic tokens, mapped onto the plugin's colour keys so the colour scheme page
    and your own scheme apply: function declarations and calls, methods, parameters, locals, constants, fields, enum
    variants, structs, enums, traits, type parameters and import paths;
  - **Reformat Code** and range formatting with `prismio fmt`, the same rules as the command line and VS Code. The
    plugin's own indenter still gives the indent of the line you are typing, and formats when the server is off.
- **Settings | Languages & Frameworks | Prismio | Use the Prismio language server** (on by default). Turning it off,
  or changing the compiler, stops and restarts the server at once.
- **The 0.2.0 language** is highlighted:
  - function markers `hot`, `noinline`, `noreturn`, `const`, `inline`, `inline(always)` and `inline(never)`, each a
    keyword only where the parser reads one, so a variable called `hot` or `const` stays a name;
  - `Int<N>`, `UInt<N>`, `F16`, `F32`, `F64`, `F128`, `BF16`, `UInt` and `I32` as built-in types, with `Int<24>` kept
    together by the formatter;
  - typed pointers, with `volatile` a keyword only inside `Ptr<...>`;
  - `async`, `await`, `join` and `scope` where the parser reads them as keywords;
  - `is`, `is not` and `not`; `codeset`, `typelist`, `workload`, `setup` and `measure`; `asm`; `...` as one token;
  - freestanding programs: the `export`, `naked`, `section(...)` and `align(n)` modifiers, and `repr(C)` and `packed`
    before `struct`.
- **`build.ums` with `const`, `enum` and `${}`.** `const` and `enum` declarations, the names they declare and each use
  of them (`Channel.BETA` included) are coloured, and so are `${NAME}` and `$$` inside a string. The manifest colour page
  has a demo and settings for each.
- A `codeset` live template.

### Changed

- **The lexer's word tables are held to the compiler.** `PrismioTokenTablesTest` compares the keywords, contextual
  words and built-in types with `prismio dump-tokens`; it found `...` lexing as a range and a dot, and five contextual
  keywords the plugin did not know.
- **An `if` with an `else` is formatted over several lines**, one branch to a block with `} else {` kept together; an
  empty branch stays `{ }`, and an `if` with no `else` is left as written.
- **New | UMS Manifest writes the configured compiler's version** in `prismio = "..."`, as New Project and
  `prismio init` do, instead of `0.1.0`.
- The plugin uses the IDE's LSP client API introduced in 2026.2 and no deprecated platform API.

### Fixed

- **Live templates wrote code the compiler rejects.** `let`, `let mut` and `println` ended with `;`, which is not in
  the language, and `match` had no parentheses around its value and no block around each arm. A test expands every
  template and runs `prismio check` on it.
- **The colour scheme page's demo was not valid Prismio.** It is now, and a test compiles it.
- **Rename, Highlight Usages and Reformat Code did nothing in a `.psm` file**: the IDE's LSP client offers them only
  for plain text unless a plugin says otherwise.
- **Enter after `{` with code still on the line indented that code twice.**
- **Reformat Code turned `Ptr<mut T>` into `Ptr < mut T >`.**
- The language-server setting said to restart the IDE; applying it restarts the server.

### Removed

The plugin's own copies of the language, each removed after a differential test held the server to its answers:

- the name resolver, Find Usages, rename, structure view and Go to Symbol (`navigation/`), the declaration reader,
  standard-library index and type inference (`symbols/`), completion (`completion/`, `UmsCompletionContributor`) and
  auto-import (`imports/`);
- the documentation, inlay-hint and parameter-info providers;
- the external annotator that ran `prismio check`, and `UmsValueChecks`, which had drifted from what the build accepts
  (it rejected `.cpp` native sources);
- the heuristic name colourer (`PrismioAnnotator`), now that the server colours names, and with it the **Mutable
  Variable** colour, which the server's legend has no modifier for;
- the manifest's "not a name UMS recognises" warning, which repeated `UMS2004` and was wrong whenever the toolchain
  was newer than the plugin;
- the `stdlibPath` setting, which nothing read. A project that had it set loses it silently.

### Known issues

- Only IntelliJ IDEA and CLion have been used by hand. PyCharm, RustRover, WebStorm and Rider 2026.2 pass JetBrains'
  plugin verifier.
- With the language server off there are no compiler errors, completion or navigation, and names are coloured by the
  lexer alone.
- Rendered documentation comments use the platform's `InlineDocumentation`, which JetBrains marks experimental.

## [0.1.0]

First release: highlighting, formatting and folding for `.psm` and `build.ums`; type-aware
completion with automatic imports; compiler errors as you type with import quick fixes; go to
declaration, find usages and rename; parameter and inline type hints; run configurations from
`build.ums`; the New Project wizard and New | UMS Manifest.
