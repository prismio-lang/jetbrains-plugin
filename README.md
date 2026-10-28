<div align="center">

<img src="https://www.prismio.org/icons/prismio-banner.png" alt="Prismio Logo" width="140" />

[//]: # (No Changes must be made above this line)

**JetBrains IDE support for the [Prismio](https://prismio.org) programming language.**

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-orange?logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/)
[![Version](https://img.shields.io/badge/version-0.2.0-blue)](https://github.com/prismio-lang/jetbrains-plugin/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)
[![IntelliJ](https://img.shields.io/badge/IntelliJ%20IDEA-2026.2%2B-blueviolet?logo=intellij-idea)](https://www.jetbrains.com/idea/)

</div>

---

Edit, check and run Prismio projects in IntelliJ IDEA, CLion, and the other
JetBrains IDEs. The plugin covers `.psm` source files and `build.ums` project
files.

Everything that needs to understand the language (errors, completion, hover, hints, navigation, rename, imports,
formatting) is answered by the compiler itself, through its language server, `prismio lsp`: the plugin carries no copy
of the language, so it follows the compiler you pick. It needs a toolchain that includes `prismio-lsp`.

## What you get

**Writing code**

- Syntax highlighting, with separate colours for types, functions, fields and variables.
- Completion that knows the type before the dot. After `let name = "Ada"`,
  typing `name.` lists the methods of `String`.
- Imports added for you. Choose `println` from the list and `import std.io`
  appears at the top of the file.
- Compiler errors as you type. Alt+Enter on a missing name adds the import it needs.
- Parameter hints inside a call, and the type of a `let` shown inline.
- Formatting, folding, comment toggling, brace matching and live templates.

**Finding your way**

- Go to declaration, including from an `import` to the file it names.
- Find usages and rename.
- Structure view and Go to Symbol.
- Documentation from `///` and `/** */` comments (Ctrl+Q), rendered in the editor. The
  plugin turns on **Render documentation comments** once (Settings | Editor |
  General | Appearance); switch it off there and it stays off.

**Starting a project**

- **New Project | Prismio** asks for the compiler (auto-detected, with its
  version shown), can create a Git repository, and writes a `build.ums` and a
  main program.
- **New | UMS Manifest** adds a `build.ums` to a folder.

**Running**

- Your `build.ums` fills the run menu: one entry for each executable, plus
  Build, Test, and every command the manifest declares.
- Run buttons in the gutter next to `fn main` and next to each target and
  command in `build.ums`.
- Compiler errors in the run output link back to the line, and program output keeps its colours.

**build.ums**

- Highlighting, completion and formatting.
- A warning for a name the build system does not know, and an error for a
  value it will reject.
- Structure view of blocks, targets and commands.

## Installing

1. Open **Settings | Plugins | Marketplace**.
2. Search for **Prismio** and click **Install**.
3. Install the Prismio toolchain if you have not already.

The plugin uses the `prismio` compiler on your PATH. To use a different one,
set it in **Settings | Languages & Frameworks | Prismio**.

Completion, error checking, hover, navigation and formatting come from the
compiler you pick, through its language server, so a newer or older compiler
shows its own functions and methods and its own errors. **Settings | Languages &
Frameworks | Prismio | Use the Prismio language server** turns the server off;
the plugin then keeps highlighting, folding, typing and running.

## Compatibility

Any JetBrains IDE from 2026.2 on. The plugin depends only on the shared
platform, plus the spellchecker and debugger modules every IDE ships.

## Building from source

You need JDK 26. Gradle comes with the repository.

```bash
git clone https://github.com/prismio-lang/jetbrains-plugin.git
cd jetbrains-plugin
./gradlew buildPlugin   # the zip lands in build/distributions/
./gradlew runIde        # opens a test IDE with the plugin installed
```

## Contributing

Bug reports and pull requests are welcome. [CONTRIBUTING.md](CONTRIBUTING.md)
explains how the code is laid out and how to test a change.

To report a security problem, follow [SECURITY.md](SECURITY.md) rather than
opening a public issue.

## Related

[Prismio](https://github.com/prismio-lang/prismio) - the compiler and standard library.

## License

Apache 2.0. See [LICENSE](LICENSE).
