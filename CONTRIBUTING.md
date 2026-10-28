# Contributing to the Prismio plugin

Thanks for helping. This guide covers how to set up, where things live, and
what a change needs before it is merged.

## Setting up

You need JDK 26 and Git. Gradle comes with the repository, and the build
downloads the IDE it compiles against.

```bash
git clone https://github.com/prismio-lang/jetbrains-plugin.git
cd jetbrains-plugin
./gradlew buildPlugin   # compile and package
./gradlew runIde        # open a test IDE with the plugin installed
```

To try the plugin on real code, open a checkout of the
[compiler](https://github.com/prismio-lang/prismio) in the test IDE, or any
project with a `build.ums`.

## Reporting a bug

Check the [open issues](https://github.com/prismio-lang/jetbrains-plugin/issues)
first. A new report should include:

- the plugin version and the IDE with its version
- the Prismio compiler version (`prismio --version`)
- the smallest `.psm` or `build.ums` that shows the problem
- what you expected, and what happened instead

If the IDE reported an exception, attach its log (**Help | Show Log in Finder**,
or **Explorer** on Windows).

For a feature idea, open an issue describing the problem it solves. For
anything large, agree on the approach in the issue before writing code.

## How the code is laid out

Everything is under `src/main/java/io/prismio/`.

| Package | What it does |
|---|---|
| `lsp` | The platform's client of `prismio lsp`, and `PrismioLspFeatures`, which says which server features the editor uses |
| `lexer`, `lang` | The Prismio lexer and its word tables, held to `prismio dump-tokens` by a test |
| `psi`, `parser` | A flat token tree |
| `annotator` | Highlighting of names (compiler errors come from the server) |
| `execution` | Run configurations, including the ones made from `build.ums` |
| `formatter`, `folding`, `editor`, `handler` | Indentation and typing behaviour (spacing is `prismio fmt`'s, through the server) |
| `toolchain`, `settings` | Finding the compiler, and the settings pages |
| `ums` | The `build.ums` language: lexer, colours, run targets (its diagnostics and completion come from the server) |
| `highlighter`, `documentation`, `spellcheck`, `template`, `debugger` | Colours, the inline rendering of `/** */`, spellchecking, New File actions, line breakpoints |

Hover, completion, parameter info, inlay hints, definitions, usages, rename, the outline, Go to Symbol, the import quick fix
and compiler errors are **not** in this plugin: they are the compiler's, served by `prismio lsp` (see
`docs/LSP_PLAN.md` in the compiler repository). To change what one of them answers, change the compiler.

`src/main/resources/META-INF/plugin.xml` registers all of it.

## Rules a change has to follow

**Follow the compiler.** The plugin should accept what the compiler accepts and
nothing else. The files that copy something from the compiler say which
compiler file they follow:

| Plugin file | Follows, in the compiler |
|---|---|
| `lexer/PrismioLexer.java` | `src/lexer/scanner.psm` |
| `lang/PrismioWords.java` | `prismio dump-tokens` (`PrismioTokenTablesTest` fails when they differ) |
| `ums/UmsLexer.java` | `ums/parser/lexer.psm` |
| `ums/UmsWords.java` | `ums/model/lowering.psm` |

When the language changes, the compiler changes first and these files follow.

**Don't write the language into the plugin.** Functions, types, methods, modules, signatures and names in scope come
from the compiler through the language server. The plugin once had its own reader of the standard library and its own
type inference; they disagreed with the compiler on one name in two hundred and were deleted. What the lexer needs
before a server answers (the word tables) is checked against `prismio dump-tokens`.

**The lexers are written by hand, on purpose.** Words like `private`, `dyn` and
`pin` are keywords in one position and ordinary names everywhere else, which a
generated lexer cannot express. There is no JFlex or Grammar-Kit step. Before
changing a lexer, add a case to `PrismioLexerTest` or `UmsLexerTest` that shows
the new behaviour.

**Spacing is the compiler's.** Reformat Code runs `prismio fmt` through the server. `PrismioBlock` only indents, and
`PrismioFormatterDifferentialTest` holds it to leaving formatted text alone.

## Testing

```bash
./gradlew test
```

Some tests need real Prismio code and skip without it. Point them at a compiler
checkout and a compiler binary to run them too:

```bash
./gradlew test -Dprismio.checkout=/path/to/prismio -Dprismio.compiler=/path/to/prismio/binary
```

Those tests lex every file in the checkout, compare the plugin's word tables and formatter with the compiler's, and ask
the server (the compiler repository's `prismio lsp`) for hover, hints, parameter info, completion and definitions over
the checkout's tests. The compiler must not be named `prismio` (a launcher forwards by that name); give the path of a
packaged toolchain's `bin/<other name>`, or a debug build copied to one, and set `PRISMIO_INTERNAL_HOSTED=1`.

## Pull requests

1. Branch from `main` and keep the change to one concern.
2. Add or update tests in `src/test/`.
3. Run `./gradlew test`, and the checkout tests if you touched the lexer,
   formatter, completion or error checking.
4. Open the pull request against `main` and link the issue it fixes.

Write commit messages the way the history reads: a first line that says what
changed, then a body that says why and how you checked it.

Java code targets Java 25, has no preview features, and follows the
[IntelliJ Platform guidelines](https://plugins.jetbrains.com/docs/intellij/intellij-coding-guidelines.html).
Comments explain why the code is the way it is, not what it does.

## Releasing

For maintainers:

1. Set `version` in `build.gradle.kts`.
2. Write the release notes in `<change-notes>` in `plugin.xml`.
3. Run `./gradlew test buildPlugin`.
4. Tag it: `git tag v<version> && git push origin v<version>`.
5. Upload `build/distributions/PrismioPlugin-<version>.zip` to the GitHub
   release and to JetBrains Marketplace.

## Security

Please don't open a public issue for a security problem. See
[SECURITY.md](SECURITY.md).
