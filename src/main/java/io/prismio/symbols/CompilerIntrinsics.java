package io.prismio.symbols;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import io.prismio.annotator.PrismioDiagnostic;
import io.prismio.annotator.PrismioDiagnosticParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The members the compiler lowers itself instead of declaring in the standard
 * library, so no source file lists them: a {@code Vec}'s {@code push} and
 * {@code length} become runtime calls in {@code src/sema/vec.psm}, an array's
 * {@code length} and searches are rewritten in {@code src/sema/array.psm}, a
 * {@code Channel}'s in {@code src/sema/channel.psm}.
 *
 * <p>These are the only members the plugin writes down, and it does not take
 * its own word for them. Once per compiler binary, a probe program uses each one
 * and {@code prismio check} reads it; a member that compiler rejects is not
 * offered. A toolchain that dropped or renamed one therefore stops showing it,
 * and one that never had {@code Channel} shows none of its members. Until the
 * probe answers — and when there is no compiler to ask — every entry is shown.
 */
public final class CompilerIntrinsics {

  private static final Logger LOG = Logger.getInstance(CompilerIntrinsics.class);

  /** What a probe needs to exercise one member: a receiver named {@code r}, and a use of it. */
  record Entry(@NotNull FnSig member, @NotNull String setup, @NotNull String use) {}

  private static final List<Entry> ENTRIES = entries();
  private static final Map<String, Set<String>> REJECTED = new ConcurrentHashMap<>();
  private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();

  private CompilerIntrinsics() {}

  private static List<Entry> entries() {
    List<Entry> out = new ArrayList<>();
    // A vector literal needs `import std.vec`; the probe hoists the line.
    String vecSetup = "import std.vec\nlet mut r: Vec<Int> = []";
    TypeRef vecT = TypeRef.parse("Vec<T>");
    // src/sema/vec.psm: semaVecEntryPoint, semaVecArity, semaVecIsProperty.
    out.add(method(vecT, "push", "value: T", null, vecSetup, "r.push(1)"));
    out.add(method(vecT, "set", "index: Int, value: T", null, vecSetup, "r.set(0, 1)"));
    // `replace` exists only for boxed struct elements ("flat Vec elements are inline").
    out.add(method(vecT, "replace", "index: Int, value: T", null,
        "import std.vec\nlet mut r: Vec<ProbeBoxed> = []", "r.replace(0, ProbeBoxed { name: \"a\" })"));
    out.add(method(vecT, "swap", "i: Int, j: Int", null, vecSetup, "r.swap(0, 1)"));
    out.add(method(vecT, "insert", "index: Int, value: T", null, vecSetup, "r.insert(0, 1)"));
    out.add(method(vecT, "reserve", "count: Int", null, vecSetup, "r.reserve(8)"));
    out.add(method(vecT, "truncate", "length: Int", null, vecSetup, "r.truncate(0)"));
    out.add(method(vecT, "clear", "", null, vecSetup, "r.clear()"));
    out.add(property(vecT, "length", "Int", vecSetup, "let x = r.length"));
    out.add(property(vecT, "capacity", "Int", vecSetup, "let x = r.capacity"));
    out.add(property(vecT, "isEmpty", "Bool", vecSetup, "let x = r.isEmpty"));
    out.add(property(vecT, "isNotEmpty", "Bool", vecSetup, "let x = r.isNotEmpty"));
    out.add(property(vecT, "first", "T", vecSetup, "let x = r.first"));
    out.add(property(vecT, "last", "T", vecSetup, "let x = r.last"));

    // src/sema/array.psm: an array's length is in its type, so `length` is the literal `N`
    // and the four searches pass it to the `[T]` overloads in std/vec.psm themselves.
    String arraySetup = "import std.vec\nlet r = [1, 2]";
    TypeRef arrayT = TypeRef.parse("[T]");
    out.add(property(arrayT, "length", "Int", arraySetup, "let x = r.length"));
    out.add(method(arrayT, "contains", "needle: T", "Bool", arraySetup, "let x = r.contains(1)"));
    out.add(method(arrayT, "indexOf", "needle: T", "Int", arraySetup, "let x = r.indexOf(1)"));
    out.add(method(arrayT, "lastIndexOf", "needle: T", "Int", arraySetup, "let x = r.lastIndexOf(1)"));
    out.add(method(arrayT, "countOf", "needle: T", "Int", arraySetup, "let x = r.countOf(1)"));

    // src/sema/channel.psm: semaChannelEntryPoint.
    // A channel carries references, so the probe's message is a one-field struct,
    // as tests/test_96_channels.psm sends one.
    String chanSetup = "let r = Channel<ProbeMessage>(4)";
    TypeRef chanT = TypeRef.parse("Channel<T>");
    out.add(method(chanT, "send", "value: T", "Bool", chanSetup, "let x = r.send(ProbeMessage { value: 1 })"));
    out.add(method(chanT, "receive", "", "T?", chanSetup, "let x = r.receive()"));
    out.add(method(chanT, "share", "", "Channel<T>", chanSetup, "let x = r.share()"));
    out.add(method(chanT, "close", "", null, chanSetup, "r.close()"));
    out.add(method(chanT, "free", "", null, chanSetup, "r.free()"));
    out.add(property(chanT, "length", "Int", chanSetup, "let x = r.length"));

    // src/sema/builtins.psm: a scalar's `T?` has `unwrapOr` and `expect`, lowered by the
    // compiler rather than declared in std, for each type whose `T?` is a value.
    String[][] scalars = {
        {"Int", "0"}, {"I8", "0 as I8"}, {"I16", "0 as I16"}, {"I64", "0 as I64"},
        {"Isize", "0 as Isize"}, {"U8", "0 as U8"}, {"U16", "0 as U16"}, {"U32", "0 as U32"},
        {"U64", "0 as U64"}, {"Usize", "0 as Usize"}, {"Float", "0.0"}, {"Bool", "false"},
        {"Char", "'a'"}};
    for (String[] scalar : scalars) {
      String name = scalar[0];
      TypeRef optT = TypeRef.parse(name + "?");
      String optSetup = "let r: " + name + "? = none";
      out.add(method(optT, "unwrapOr", "fallback: " + name, name, optSetup,
          "let x = r.unwrapOr(" + scalar[1] + ")"));
      out.add(method(optT, "expect", "", name, optSetup, "let x = r.expect()"));
    }
    return List.copyOf(out);
  }

  private static Entry method(TypeRef owner, String name, String params, @Nullable String result,
      String setup, String use) {
    return new Entry(sig(owner, name, params, result, false), setup, use);
  }

  private static Entry property(TypeRef owner, String name, String result, String setup, String use) {
    return new Entry(sig(owner, name, "", result, true), setup, use);
  }

  private static FnSig sig(TypeRef owner, String name, String params, @Nullable String result,
      boolean property) {
    List<FnSig.Param> parsed = new ArrayList<>();
    if (!params.isEmpty()) {
      for (String param : params.split(",")) {
        String[] parts = param.split(":");
        parsed.add(new FnSig.Param(parts[0].strip(), TypeRef.parse(parts[1].strip()), ""));
      }
    }
    return new FnSig(name, "", owner, List.of("T"), null, List.of(), List.copyOf(parsed),
        TypeRef.parse(result), true, property, false, false, FnSig.PUBLIC, "", -1);
  }

  /**
   * The members this compiler accepts, as a table. Starts the probe the first time
   * a compiler is seen and answers with every entry until it finishes.
   */
  public static @NotNull SymbolTable table(@Nullable Path compiler) {
    Set<String> rejected = compiler == null ? Set.of() : rejectedBy(compiler);
    SymbolTable table = new SymbolTable();
    for (Entry entry : ENTRIES) {
      if (!rejected.contains(key(entry))) {
        table.addBuiltIn(entry.member());
      }
    }
    return table;
  }

  /**
   * The types whose members this compiler lowers itself: {@code Vec}, {@code Array}, {@code Channel}. These are
   * offered as type names, so an optional owner -- a member of {@code Int?} -- is not one.
   */
  public static @NotNull Set<String> owners(@Nullable Path compiler) {
    Set<String> rejected = compiler == null ? Set.of() : rejectedBy(compiler);
    Set<String> owners = new java.util.LinkedHashSet<>();
    for (Entry entry : ENTRIES) {
      if (!rejected.contains(key(entry)) && !entry.member().owner().optional()) {
        owners.add(entry.member().owner().base());
      }
    }
    return owners;
  }

  private static String key(Entry entry) {
    return entry.member().owner().memberKey() + "." + entry.member().name();
  }

  private static Set<String> rejectedBy(@NotNull Path compiler) {
    String identity;
    try {
      identity = compiler + ":" + Files.getLastModifiedTime(compiler).toMillis() + ":" + Files.size(compiler);
    } catch (IOException e) {
      return Set.of();
    }
    Set<String> known = REJECTED.get(identity);
    if (known != null) {
      return known;
    }
    if (RUNNING.add(identity) && ApplicationManager.getApplication() != null) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          REJECTED.put(identity, probe(compiler));
        } finally {
          RUNNING.remove(identity);
        }
      });
    }
    return Set.of();
  }

  /**
   * Runs {@code prismio check} over one function per member. A member whose
   * function draws an error is rejected; an error anywhere else means the probe
   * itself did not work on this compiler, and nothing is rejected on its word.
   */
  static @NotNull Set<String> probe(@NotNull Path compiler) {
    StringBuilder source = new StringBuilder();
    Set<String> imports = new java.util.LinkedHashSet<>();
    List<int[]> ranges = new ArrayList<>();
    List<String> bodies = new ArrayList<>();
    for (Entry entry : ENTRIES) {
      String setup = entry.setup();
      StringBuilder body = new StringBuilder();
      for (String line : setup.split("\n")) {
        if (line.startsWith("import ")) {
          imports.add(line);
        } else {
          body.append("    ").append(line).append('\n');
        }
      }
      body.append("    ").append(entry.use()).append('\n');
      bodies.add(body.toString());
    }
    for (String line : imports) {
      source.append(line).append('\n');
    }
    source.append("\nstruct ProbeMessage {\n    value: Int\n}\n\nstruct ProbeBoxed {\n    name: String\n}\n\n");
    for (int k = 0; k < ENTRIES.size(); k++) {
      int first = lineCount(source) + 1;
      source.append("fn probe").append(k).append("() {\n").append(bodies.get(k)).append("}\n\n");
      ranges.add(new int[] {first, lineCount(source)});
    }
    source.append("fn main() -> Int {\n    return 0\n}\n");

    Path dir = null;
    try {
      dir = Files.createTempDirectory("prismio-intrinsics");
      Path file = dir.resolve("probe.psm");
      Files.writeString(file, source, StandardCharsets.UTF_8);
      // The source path comes first: `check --diagnostic-format=json <file>` is refused (P1026).
      GeneralCommandLine command = new GeneralCommandLine(compiler.toString(), "check",
          file.toString(), "--diagnostic-format=json")
          .withWorkDirectory(dir.toFile())
          .withCharset(StandardCharsets.UTF_8);
      ProcessOutput output = new CapturingProcessHandler(command).runProcess(20_000);
      if (output.isTimeout()) {
        return Set.of();
      }
      String reported = output.getStderr() + "\n" + output.getStdout();
      // No summary means the compiler did not run the check at all -- a refused
      // argument, a crash -- and its silence says nothing about any member.
      if (!reported.contains("\"kind\":\"summary\"")) {
        return Set.of();
      }
      List<PrismioDiagnostic> diagnostics = PrismioDiagnosticParser.parseOutput(reported);
      Set<String> rejected = new HashSet<>();
      for (PrismioDiagnostic diagnostic : diagnostics) {
        if (!"error".equalsIgnoreCase(diagnostic.severity())) {
          continue;
        }
        int hit = -1;
        for (int k = 0; k < ranges.size(); k++) {
          if (diagnostic.line() >= ranges.get(k)[0] && diagnostic.line() <= ranges.get(k)[1]) {
            hit = k;
            break;
          }
        }
        if (hit < 0) {
          return Set.of();
        }
        rejected.add(key(ENTRIES.get(hit)));
      }
      return Set.copyOf(rejected);
    } catch (Exception e) {
      LOG.debug("intrinsic probe failed", e);
      return Set.of();
    } finally {
      if (dir != null) {
        try (var files = Files.walk(dir)) {
          files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
          // A temp directory left behind is the OS's to clean.
        }
      }
    }
  }

  private static int lineCount(CharSequence text) {
    int count = 0;
    for (int k = 0; k < text.length(); k++) {
      if (text.charAt(k) == '\n') {
        count++;
      }
    }
    return count;
  }
}
