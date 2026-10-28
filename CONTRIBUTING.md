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
| `lexer`, `lang` | The Prismio lexer and its word tables |
| `psi`, `parser` | A flat token tree; identifiers get their own element so they can carry references |
| `symbols` | Reads declarations from source, indexes the toolchain's standard library, infers types |
| `completion` | Completion, keywords and parameter info |
| `imports` | Finding and adding imports, and the "Import std.x" quick fix |
| `hints` | Inline type hints for `let` |
| `annotator` | Highlighting of names, and compiler errors as you type |
| `navigation` | Go to declaration, find usages, rename, structure view |
| `execution` | Run configurations, including the ones made from `build.ums` |
| `formatter`, `folding`, `editor`, `handler` | Formatting and typing behaviour |
| `toolchain`, `settings` | Finding the compiler, and the settings pages |
| `ums` | The `build.ums` language, from lexer to run targets |
| `highlighter`, `documentation`, `spellcheck`, `template`, `debugger` | Colours, Ctrl+Q docs, spellchecking, New File actions, line breakpoints |

`src/main/resources/META-INF/plugin.xml` registers all of it.

## Rules a change has to follow

**Follow the compiler.** The plugin should accept what the compiler accepts and
nothing else. The files that copy something from the compiler say which
compiler file they follow:

| Plugin file | Follows, in the compiler |
|---|---|
| `lexer/PrismioLexer.java` | `src/lexer/scanner.psm` |
| `lang/PrismioWords.java` | `src/lexer/token.psm`, `src/ast/types.psm`, `src/parse/decl.psm` |
| `ums/UmsLexer.java` | `ums/parser/lexer.psm` |
| `ums/UmsWords.java` | `ums/model/lowering.psm` |
| `symbols/CompilerIntrinsics.java` | `src/sema/vec.psm`, `src/sema/channel.psm` |

When the language changes, the compiler changes first and these files follow.

**Don't write the standard library into the plugin.** Functions, types, methods
and modules come from the toolchain the user has configured, read by
`symbols/StdlibIndex`. That way a different compiler version brings its own
library. The only members listed by hand are the few the compiler builds in
(`CompilerIntrinsics`), and those are checked against the configured compiler
before they are shown.

**The lexers are written by hand, on purpose.** Words like `private`, `dyn` and
`pin` are keywords in one position and ordinary names everywhere else, which a
generated lexer cannot express. There is no JFlex or Grammar-Kit step. Before
changing a lexer, add a case to `PrismioLexerTest` or `UmsLexerTest` that shows
the new behaviour.

**Formatting must not change code.** A formatter rule is easy to get subtly
wrong: one rule once turned `a and b` into `aand b`. After changing the
formatter, run the checkout tests below, which reformat every file in the
compiler repository.

## Testing

```bash
./gradlew test
```

Some tests need real Prismio code and skip without it. Point them at a compiler
checkout and a compiler binary to run them too:

```bash
./gradlew test -Dprismio.checkout=/path/to/prismio -Dprismio.compiler=/path/to/prismio/binary
```

Those tests lex, parse and reformat every file in the checkout, check every
file through the compiler, and index both a checkout's `std/` and an installed
toolchain's `stdlib/`.

Completion tests use a small standard library in `src/test/testData/stdlib`,
so they don't depend on whichever compiler is installed. When the standard
library's syntax changes, update those files to match.

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
