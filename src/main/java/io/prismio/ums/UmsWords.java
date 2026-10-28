package io.prismio.ums;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the manifest's identifiers mean.
 *
 * <p>None of these are keywords — {@code ums/parser/} emits a plain identifier
 * for every one, and the meaning is added by {@code ums/model/lowering.psm}. The
 * tables here mirror that file so the editor recognises exactly what the
 * compiler recognises, and colours nothing it would reject.
 *
 * <p>Each entry carries its own description because completion, documentation
 * and the annotator all want the same sentence, and three copies of it drift.
 */
public final class UmsWords {

  private UmsWords() {}

  /** Blocks that may appear at the top level, in the order a manifest wants them. */
  public static final Map<String, String> TOP_LEVEL_BLOCKS = ordered(
      "toolchain", "Selects the project-local compiler. Must be the first block.",
      "project", "Project identity: name, version, and the Prismio version it needs.",
      "targets", "The artifacts this project builds.",
      "commands", "Commands the project owns, invoked as `prismio <name>`.",
      "profiles", "Per-profile settings: `debug { }` and `release { }`.",
      "dependencies", "Packages this project needs.");

  /**
   * Declarations at the top level that are not blocks: names a manifest defines once and uses anywhere a
   * value is written. They are gone before lowering ({@code ums/model/constants.psm}).
   */
  public static final Map<String, String> TOP_LEVEL_DECLARATIONS = ordered(
      "const", "`const NAME = value`: names a string, integer, boolean or list. `${NAME}` inside a string "
          + "interpolates it; `$$` is a literal `$`.",
      "enum", "`enum Name { A, B = \"b\" }`: a closed set of values. `Name.A` is a variant's value, or its name.");

  /** Keys inside `project { }`. */
  public static final Map<String, String> PROJECT_KEYS = ordered(
      "name", "Package name. Letters, digits, `-`, `_` and `.`, starting with a letter, digit or `_`.",
      "version", "This project's version, a SemVer such as `1.2.0` or `1.0.0-beta.1`.",
      "prismio", "The Prismio version this project requires.",
      "description", "One line about the package. Must not be empty.",
      "license", "An SPDX expression, such as `Apache-2.0`.",
      "licenseFile", "A project-relative licence file. Mutually exclusive with `license`.",
      "authors", "An array of author strings.");

  /** Keys inside `toolchain { }`. */
  public static final Map<String, String> TOOLCHAIN_KEYS = ordered(
      "host", "The project's own compiler, a path under `.prismio/`. Run only once this machine has built it.");

  /** Declarations inside `targets { }`. */
  public static final Map<String, String> TARGET_KINDS = ordered(
      "executable", "A program. Links the Prismio runtime, plus any native code it declares.",
      "library", "Modelled and validated; artifact emission is not implemented yet.",
      "test", "A program that exits 0 when it passes. `prismio test` builds and runs it.");

  /** Keys and blocks inside a target body. */
  public static final Map<String, String> TARGET_BODY = ordered(
      "entry", "The source file this target is built from.",
      "runtime", "`\"installed\"` (the default) or `\"none\"`, when the native sources provide the runtime.",
      "exportDynamic", "`true` makes the executable's symbols visible to code it loads while running.",
      "freestanding", "`true` builds a program with no operating system under it: no runtime, no C library, no startup files.",
      "triple", "The machine to build for, an LLVM target triple such as `aarch64-unknown-none-elf`.",
      "features", "An LLVM feature list for code generation, such as `-neon,-fp-armv8`.",
      "codeModel", "`\"small\"`, `\"kernel\"`, `\"medium\"` or `\"large\"`.",
      "noRedZone", "`true` marks every function `noredzone`: an interrupt handler uses the stack it interrupts.",
      "native", "C sources this target compiles and links, and the flags they use.",
      "link", "Ordered native linker inputs for this target.");

  /** Declarations inside a `native { }` block. */
  public static final Map<String, String> NATIVE_KINDS = ordered(
      "source", "C files (`.c`) to compile and link, in order. C++ is not supported.",
      "include", "A header directory (`-I`).",
      "define", "A preprocessor definition (`-D`), such as `NAME=1`.",
      "flag", "Any other compiler flag.",
      "responseFile", "Compiler flags from a file, one per line.");

  /** Profiles a `profiles { }` block may configure. */
  public static final Map<String, String> PROFILE_NAMES = ordered(
      "debug", "The default profile: `-g` and overflow checks unless changed here.",
      "release", "`--release`: optimised, neither setting unless changed here.");

  /** Keys inside `profiles { debug { } }`. */
  public static final Map<String, String> PROFILE_KEYS = ordered(
      "debugInfo", "`true` or `false`: build with `-g` (the program at `-O0`).",
      "overflowChecks", "`true` or `false`: trap on integer overflow in this project's code.");

  /** Declarations inside a `link { }` block. */
  public static final Map<String, String> LINK_KINDS = ordered(
      "library", "Passes `-l<name>`.",
      "search", "A project-root-relative native search path.",
      "file", "An exact project-root-relative object or library.",
      "framework", "A Mach-O framework. Ignored on other targets.",
      "responseFile", "Linker arguments from a file, such as another tool's output.",
      "script", "A linker script, passed as `-T <path>`. The path is project-root-relative.",
      "arg", "One linker argument, as written.");

  /** Declarations inside `dependencies { }`. */
  public static final Map<String, String> DEPENDENCY_SCOPES = ordered(
      "implementation", "A dependency this project uses internally.",
      "api", "A dependency this project exposes in its own API.",
      "testImplementation", "A dependency only the test targets need.");

  /** Declarations inside `commands { }`. */
  public static final Map<String, String> COMMAND_KINDS = ordered(
      "command", "One command, invoked as `prismio <name>`. Its steps run in order from the project root, "
          + "and a failing step's exit status becomes the command's.");

  /** Keys and steps inside a `command(...)` body. */
  public static final Map<String, String> COMMAND_BODY = ordered(
      "description", "One line shown when the command is listed.",
      "build", "Builds a declared target. Takes exactly one target name.",
      "run", "Runs a declared target, a `.py` script, or a `.psm` tool (built into `.prismio/build/<profile>/tools/`). "
          + "Runs from the project root.",
      "shell", "Runs any program. No shell is involved: the name and each argument are passed as an argument list, "
          + "so quoting, `$(...)` and `%VAR%` mean nothing. Runs from the project root; portability is yours.");

  /**
   * The one identifier that is a value rather than a name: inside a `run` or
   * `shell` step it splices in whatever the user typed after the command name,
   * keeping its position among the fixed arguments.
   */
  public static final String ARGS_MARKER = "args";

  /** Every identifier the manifest gives meaning to, for a quick "is this known" test. */
  public static final Set<String> ALL_KNOWN = collect();

  private static Map<String, String> ordered(String... pairs) {
    Map<String, String> out = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      out.put(pairs[i], pairs[i + 1]);
    }
    return Map.copyOf(out);
  }

  private static Set<String> collect() {
    var names = new java.util.HashSet<String>();
    for (Map<String, String> table : java.util.List.of(TOP_LEVEL_BLOCKS, PROJECT_KEYS,
        TOOLCHAIN_KEYS, TARGET_KINDS, TARGET_BODY, NATIVE_KINDS, LINK_KINDS,
        PROFILE_NAMES, PROFILE_KEYS, DEPENDENCY_SCOPES, COMMAND_KINDS, COMMAND_BODY)) {
      names.addAll(table.keySet());
    }
    names.addAll(TOP_LEVEL_DECLARATIONS.keySet());
    names.add(ARGS_MARKER);
    return Set.copyOf(names);
  }
}
