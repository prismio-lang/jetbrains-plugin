package io.prismio.annotator;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.prismio.imports.AddImportFix;
import io.prismio.psi.PrismioFile;
import io.prismio.symbols.StdlibIndex;
import io.prismio.toolchain.PrismioProjectSettings;
import io.prismio.toolchain.PrismioToolchainService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs {@code prismio check --diagnostic-format=json} in the background and shows the compiler's
 * own diagnostics, with their P#### codes, on the file being edited.
 *
 * <p>The file is checked as part of its program ({@link PrismioCheckTarget}), with the editor's
 * text overlaid on it ({@code --overlay}), because a module checked as a program of its own
 * resolves its imports against the wrong directory and cannot see the names its program shares.
 * The program a file was checked through is remembered, so an edit costs one compiler run; a
 * program that stops reading the file (P1075) is dropped and the next one tried.
 */
public final class PrismioExternalAnnotator
    extends ExternalAnnotator<PrismioExternalAnnotator.InitialInfo, PrismioExternalAnnotator.AnnotationResult> {

  /** Long enough for a whole program; a check still running then is abandoned, not reported. */
  private static final long TIMEOUT_SECONDS = 15;

  /** The compiler's answer for a program that never reads the overlaid file. */
  private static final String NOT_IN_PROGRAM = "P1075";

  /**
   * Diagnostics about which compiler serves the project, not about the file: a project host this
   * machine did not build is left alone (P1077, which the launcher omits in JSON mode), will not
   * start (P1052), or is an older generation being rebuilt (P1064) or has no target to rebuild it
   * (P1065). The global compiler checks the file instead, correctly. `prismio build` is the fix,
   * and it is not a fault to draw on every file that is open.
   */
  private static final Set<String> LAUNCHER_STATUS = Set.of("P1077", "P1052", "P1064", "P1065");

  private static final Map<Path, PrismioCheckTarget> TARGETS = new ConcurrentHashMap<>();

  public record InitialInfo(
      @NotNull Project project,
      @NotNull Path filePath,
      @NotNull Document document,
      @NotNull String content) {}

  public record AnnotationResult(@NotNull List<PrismioDiagnostic> diagnostics, @NotNull Document document) {}

  /** One run of the compiler: what it said, and whether the program read the file at all. */
  record Checked(@NotNull PrismioCheckTarget target, @NotNull List<PrismioDiagnostic> diagnostics) {
    boolean readFile() {
      return diagnostics.stream().noneMatch(d -> NOT_IN_PROGRAM.equals(d.code()));
    }
  }

  @Override
  public @Nullable InitialInfo collectInformation(
      @NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    if (!(file instanceof PrismioFile)) {
      return null;
    }
    Project project = file.getProject();
    // The language server publishes the same diagnostics, with every unsaved buffer; two sets
    // of them on one file would be drawn twice.
    if (PrismioProjectSettings.getInstance(project).getState().useLanguageServer) {
      return null;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
      return null;
    }

    Path path;
    try {
      path = virtualFile.toNioPath();
    } catch (UnsupportedOperationException e) {
      return null;
    }

    return new InitialInfo(project, path, editor.getDocument(), editor.getDocument().getText());
  }

  @Override
  public @Nullable AnnotationResult doAnnotate(@Nullable InitialInfo info) {
    if (info == null) {
      return null;
    }
    Path compiler = PrismioToolchainService.getInstance(info.project()).getCompilerExecutable();
    if (compiler == null) {
      return null;
    }
    // apply() runs on the UI thread and asks the library which module declares a
    // name the compiler could not find; read it here, off that thread, first.
    StdlibIndex.getInstance(info.project()).get();

    Path file = canonical(info.filePath());
    Path buffer = null;
    try {
      // Outside the source tree: a file beside the sources would be one more module to any
      // `import pkg.*` of that directory, in this check and in a build running meanwhile.
      buffer = Files.createTempFile("prismio-check-", ".psm");
      Files.writeString(buffer, info.content(), StandardCharsets.UTF_8);

      String basePath = info.project().getBasePath();
      Path project = basePath != null ? Path.of(basePath) : null;
      Checked checked = null;
      PrismioCheckTarget known = TARGETS.get(file);
      if (known != null) {
        checked = check(compiler, known, file, buffer, project);
        if (checked != null && !checked.readFile()) {
          TARGETS.remove(file);
          checked = null;
        }
      }
      if (checked == null) {
        checked = checkThroughProgram(compiler, file, info.content(), buffer, project);
        if (checked == null) {
          return null;
        }
        TARGETS.put(file, checked.target());
      }
      return new AnnotationResult(forFile(checked, file), info.document());
    } catch (IOException e) {
      return null;
    } finally {
      if (buffer != null) {
        try {
          Files.deleteIfExists(buffer);
        } catch (IOException ignored) {
        }
      }
    }
  }

  /**
   * {@code file}, whose editor text is in {@code buffer}, checked through the first program in
   * {@link PrismioCheckTarget#plan} that reads it -- preferring one with no errors of its own
   * elsewhere, which may stop before this file's are found. Null when none could be run.
   * {@code project} is the working directory and bounds the search for importers.
   */
  static @Nullable Checked checkThroughProgram(@NotNull Path compiler, @NotNull Path file, @NotNull String text,
                                               @NotNull Path buffer, @Nullable Path project) {
    Checked checked = null;
    for (PrismioCheckTarget target : PrismioCheckTarget.plan(file, text, project)) {
      Checked candidate = check(compiler, target, file, buffer, project);
      if (candidate == null || !candidate.readFile()) {
        continue;
      }
      if (checked == null) {
        checked = candidate;
      }
      if (errorsElsewhere(candidate.diagnostics(), file).isEmpty()) {
        return candidate;
      }
    }
    return checked;
  }

  /** The compiler's diagnostics for one program, or null when it could not be run to the end. */
  private static @Nullable Checked check(@NotNull Path compiler, @NotNull PrismioCheckTarget target,
                                         @NotNull Path file, @NotNull Path buffer, @Nullable Path project) {
    // The format first: a compiler that rejects a later argument has already switched to JSON,
    // so its refusal reaches the parser instead of being skipped as text.
    List<String> command = new ArrayList<>(List.of(
        compiler.toString(), "check", target.entry().toString(),
        "--diagnostic-format=json",
        "--overlay", file.toString(), buffer.toString()));
    if (target.module() != null) {
      command.add("--module");
      command.add(target.module());
    }
    Path output = null;
    try {
      // To a file, not a pipe: read only after the process ends, a pipe fills with a long
      // report and the compiler blocks writing it until the timeout discards everything.
      output = Files.createTempFile("prismio-check-", ".json");
      ProcessBuilder pb = new ProcessBuilder(command)
          .redirectErrorStream(true)
          .redirectOutput(output.toFile());
      Path workDir = project != null ? project : target.entry().getParent();
      if (workDir != null) {
        pb.directory(workDir.toFile());
      }
      Process process = pb.start();
      if (!finished(process)) {
        return null;
      }
      String text = Files.readString(output, StandardCharsets.UTF_8);
      List<PrismioDiagnostic> diagnostics = PrismioDiagnosticParser.parseOutput(text);
      if (diagnostics.stream().anyMatch(d -> "P1026".equals(d.code()) && d.message().contains("--overlay"))) {
        // A compiler from before `check --overlay` can only check a file as a program of its
        // own, which is wrong for most modules of a program; saying so beats showing its errors.
        return new Checked(target, List.of(new PrismioDiagnostic("diagnostic", 1, "warning", null, null, 0, 0, 0,
            "This Prismio compiler (" + compiler + ") cannot check a file as part of its program: it predates "
                + "`prismio check --overlay`. Update the toolchain, or point Settings | Prismio at a newer compiler.")));
      }
      return new Checked(target, diagnostics);
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } finally {
      if (output != null) {
        try {
          Files.deleteIfExists(output);
        } catch (IOException ignored) {
        }
      }
    }
  }

  /**
   * Waits for {@code process} to end, up to the timeout. An annotation the IDE cancels -- the
   * file was edited again -- stops the compiler at once rather than leaving it to run to the
   * end beside the next check. False when it was stopped, here or by the timeout.
   */
  private static boolean finished(@NotNull Process process) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    try {
      while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
        ProgressManager.checkCanceled();
        if (System.nanoTime() > deadline) {
          stop(process);
          return false;
        }
      }
      return true;
    } catch (ProcessCanceledException cancelled) {
      stop(process);
      throw cancelled;
    }
  }

  /** The launcher forwards to a compiler of its own: both go, not just the one started. */
  private static void stop(@NotNull Process process) {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
  }

  /**
   * What belongs on this file: its own diagnostics, those the compiler could place nowhere (a
   * broken toolchain, "too many errors"), and one line saying so when other files of the program
   * have errors -- they can stop the check before it reaches this file's.
   */
  static @NotNull List<PrismioDiagnostic> forFile(@NotNull List<PrismioDiagnostic> all, @NotNull Path file,
                                                  @NotNull Path entry) {
    List<PrismioDiagnostic> shown = new ArrayList<>();
    for (PrismioDiagnostic d : all) {
      if (d.code() != null && LAUNCHER_STATUS.contains(d.code())) {
        continue;
      }
      if (d.file() == null) {
        shown.add(d);
      } else if (sameFile(d.file(), file)) {
        shown.add(d);
      }
    }
    List<PrismioDiagnostic> elsewhere = errorsElsewhere(all, file);
    if (!elsewhere.isEmpty()) {
      PrismioDiagnostic first = elsewhere.get(0);
      String where = first.file() + ":" + first.line();
      String message = elsewhere.size() == 1
          ? "1 error in another file of the program `" + entry.getFileName() + "`: " + where + ": " + first.message()
          : elsewhere.size() + " errors in other files of the program `" + entry.getFileName() + "`; the first, "
              + where + ": " + first.message();
      shown.add(new PrismioDiagnostic("diagnostic", 1, "info", null, null, 0, 0, 0, message));
    }
    return shown;
  }

  static @NotNull List<PrismioDiagnostic> forFile(@NotNull Checked checked, @NotNull Path file) {
    return forFile(checked.diagnostics(), file, checked.target().entry());
  }

  static @NotNull List<PrismioDiagnostic> errorsElsewhere(@NotNull List<PrismioDiagnostic> all, @NotNull Path file) {
    List<PrismioDiagnostic> elsewhere = new ArrayList<>();
    for (PrismioDiagnostic d : all) {
      if (d.file() != null && "error".equalsIgnoreCase(d.severity()) && !sameFile(d.file(), file)) {
        elsewhere.add(d);
      }
    }
    return elsewhere;
  }

  static boolean sameFile(@NotNull String reported, @NotNull Path file) {
    Path path;
    try {
      path = Path.of(reported).toAbsolutePath().normalize();
    } catch (InvalidPathException e) {
      return false;
    }
    if (path.equals(file)) {
      return true;
    }
    try {
      return Files.exists(path) && Files.isSameFile(path, file);
    } catch (IOException e) {
      return false;
    }
  }

  private static @NotNull Path canonical(@NotNull Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      return path.toAbsolutePath().normalize();
    }
  }

  @Override
  public void apply(
      @NotNull PsiFile file,
      @Nullable AnnotationResult result,
      @NotNull AnnotationHolder holder) {
    if (result == null || result.diagnostics().isEmpty()) {
      return;
    }

    Document doc = result.document();
    for (PrismioDiagnostic diagnostic : result.diagnostics()) {
      HighlightSeverity severity = switch (diagnostic.severity().toLowerCase()) {
        case "error" -> HighlightSeverity.ERROR;
        case "warning" -> HighlightSeverity.WARNING;
        default -> HighlightSeverity.WEAK_WARNING;
      };

      String message = formatMessage(diagnostic);
      int line = diagnostic.line();
      int lineIndex = line - 1;

      // Nowhere in the file to point at: a banner over the editor, not a mark on its first
      // character that reads as a problem there.
      if (lineIndex < 0 || lineIndex >= doc.getLineCount()) {
        holder.newAnnotation(severity, message)
            .fileLevel()
            .tooltip(tooltip(message))
            .create();
        continue;
      }

      int lineStart = doc.getLineStartOffset(lineIndex);
      int lineEnd = doc.getLineEndOffset(lineIndex);
      CharSequence lineChars = doc.getCharsSequence().subSequence(lineStart, lineEnd);
      byte[] lineBytes = lineChars.toString().getBytes(StandardCharsets.UTF_8);

      int col = diagnostic.column();
      int byteStart = Math.max(0, Math.min(col > 0 ? col - 1 : 0, lineBytes.length));
      int length = diagnostic.length();
      int byteEnd = Math.max(byteStart, Math.min(byteStart + (length > 0 ? length : 1), lineBytes.length));

      int charStart = new String(lineBytes, 0, byteStart, StandardCharsets.UTF_8).length();
      int charEnd = new String(lineBytes, 0, byteEnd, StandardCharsets.UTF_8).length();
      int startOffset = lineStart + charStart;
      int endOffset = lineStart + charEnd;

      if (endOffset <= startOffset) {
        endOffset = Math.min(startOffset + 1, doc.getTextLength());
      }
      startOffset = Math.min(startOffset, doc.getTextLength());
      endOffset = Math.min(endOffset, doc.getTextLength());

      if (startOffset >= endOffset) {
        continue;
      }

      AnnotationBuilder builder = holder.newAnnotation(severity, message)
          .range(new TextRange(startOffset, endOffset))
          .tooltip(tooltip(message));
      if (severity == HighlightSeverity.ERROR) {
        for (AddImportFix fix : AddImportFix.forMessage(file.getProject(), diagnostic.message())) {
          builder = builder.withFix(fix);
        }
      }
      builder.create();
    }
  }

  /** A tooltip is HTML: the compiler's backticks and angle brackets are text, a folded note a new line. */
  private static String tooltip(@NotNull String message) {
    return "<html>" + StringUtil.escapeXmlEntities(message).replace("\n", "<br>") + "</html>";
  }

  private static String formatMessage(@NotNull PrismioDiagnostic diagnostic) {
    if (diagnostic.code() != null && !diagnostic.code().isBlank()) {
      return "[" + diagnostic.code() + "] " + diagnostic.message();
    }
    return diagnostic.message();
  }
}
