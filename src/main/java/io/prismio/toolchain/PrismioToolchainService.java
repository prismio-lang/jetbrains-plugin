package io.prismio.toolchain;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.EnvironmentUtil;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service responsible for discovering and validating the Prismio compiler executable
 * and the standard library distribution.
 */
@Service(Service.Level.PROJECT)
public final class PrismioToolchainService {

  private final Project project;

  public PrismioToolchainService(@NotNull Project project) {
    this.project = project;
  }

  public static PrismioToolchainService getInstance(@NotNull Project project) {
    return project.getService(PrismioToolchainService.class);
  }

  /**
   * Finds the Prismio compiler executable (`prismio` or `prismio.exe`).
   */
  public @Nullable Path getCompilerExecutable() {
    PrismioProjectSettings.State settings = PrismioProjectSettings.getInstance(project).getState();
    if (settings.compilerPath != null && !settings.compilerPath.isBlank()) {
      Path configured = Path.of(settings.compilerPath);
      if (isValidExecutable(configured)) {
        return configured;
      }
    }
    return detectCompilerExecutable();
  }

  /**
   * The compiler found without the configured path: the project's own, the environment, PATH,
   * then the usual install folders. What Auto-Detect in the settings shows.
   */
  public @Nullable Path detectCompilerExecutable() {
    String binaryName = SystemInfo.isWindows ? "prismio.exe" : "prismio";

    // 1. The project's own compiler: the manifest's `toolchain.host`, then its build output
    // under `.prismio/`, which a checkout does not carry. A binary at the project root could
    // have come with a cloned repository, and opening that repository would then run it.
    // Not in a project the IDE does not trust yet. Only a checkout that has been
    // built carries `.prismio/`, but nothing stops a repository from committing
    // one, and the compiler found here runs on every edit, before the user has
    // done anything but open the project.
    String basePath = project.getBasePath();
    if (basePath != null && TrustedProjects.isProjectTrusted(project)) {
      Path projectRoot = Path.of(basePath);
      // The manifest's own `toolchain.host` first, resolved as the launcher resolves it:
      // relative to `build.ums`, possibly outside the project (`../.prismio/...` for a
      // sub-project), or absolute. Only a host this machine promoted -- one with the
      // `.trusted` stamp beside it -- is run, which is the launcher's rule too.
      Path declared = declaredHost(projectRoot);
      if (declared != null && Files.isRegularFile(Path.of(declared + ".trusted"))
          && isValidExecutable(declared)) {
        return declared;
      }
      List<Path> localCandidates = List.of(
          projectRoot.resolve(".prismio/build/debug").resolve(binaryName),
          projectRoot.resolve(".prismio/build/release").resolve(binaryName),
          projectRoot.resolve(".prismio/bin").resolve(binaryName)
      );
      for (Path candidate : localCandidates) {
        if (isValidExecutable(candidate)) {
          return candidate;
        }
      }
    }
    return detectInstalledCompiler();
  }

  /**
   * The compiler this machine has installed, with no project in mind: {@code PRISMIO},
   * {@code PRISMIO_HOME}, PATH, then the usual install folders. What the New Project wizard
   * starts from, before there is a project to look in.
   */
  public static @Nullable Path detectInstalledCompiler() {
    String binaryName = SystemInfo.isWindows ? "prismio.exe" : "prismio";

    // 2. Check PRISMIO or PRISMIO_HOME environment variables
    String envPrismio = EnvironmentUtil.getValue("PRISMIO");
    if (envPrismio != null && !envPrismio.isBlank()) {
      Path envPath = Path.of(envPrismio);
      if (isValidExecutable(envPath)) {
        return envPath;
      }
    }

    String envHome = EnvironmentUtil.getValue("PRISMIO_HOME");
    if (envHome != null && !envHome.isBlank()) {
      Path envBin = Path.of(envHome, "bin", binaryName);
      if (isValidExecutable(envBin)) {
        return envBin;
      }
    }

    // 3. Check system PATH -- the user's shell's, not the IDE process's: started from the Dock or
    // Spotlight, an IDE on macOS has launchd's PATH and never sees what a profile added.
    String pathEnv = EnvironmentUtil.getValue("PATH");
    if (pathEnv != null) {
      for (String segment : pathEnv.split(File.pathSeparator)) {
        if (!segment.isBlank()) {
          Path candidate = Path.of(segment, binaryName);
          if (isValidExecutable(candidate)) {
            return candidate;
          }
        }
      }
    }

    // 4. Common install locations
    String userHome = System.getProperty("user.home");
    List<Path> commonLocations = new ArrayList<>();
    if (userHome != null) {
      commonLocations.add(Path.of(userHome, ".prismio/bin", binaryName));
      commonLocations.add(Path.of(userHome, ".local/bin", binaryName));
    }
    commonLocations.add(Path.of("/usr/local/bin", binaryName));
    commonLocations.add(Path.of("/opt/homebrew/bin", binaryName));

    for (Path candidate : commonLocations) {
      if (isValidExecutable(candidate)) {
        return candidate;
      }
    }

    return null;
  }

  /**
   * The version {@code compiler --version} reports ({@code prismio 0.1.0} on its first line), or
   * null when it is not a Prismio compiler that answers within a few seconds. Runs the compiler:
   * not for the UI thread's hot paths.
   */
  public static @Nullable String compilerVersion(@NotNull Path compiler) {
    if (!isValidExecutable(compiler)) {
      return null;
    }
    try {
      GeneralCommandLine command = new GeneralCommandLine(compiler.toString(), "--version");
      ProcessOutput output = new CapturingProcessHandler(command).runProcess(5_000);
      if (output.isTimeout() || output.getExitCode() != 0) {
        return null;
      }
      String first = output.getStdout().lines().findFirst().orElse("").trim();
      if (!first.startsWith("prismio ")) {
        return null;
      }
      String version = first.substring("prismio ".length()).trim();
      return version.isEmpty() ? null : version;
    } catch (ExecutionException | RuntimeException e) {
      return null;
    }
  }

  private static final Pattern TOOLCHAIN_HOST = Pattern.compile(
      "\\btoolchain\\s*\\{[^}]*?\\bhost\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

  /**
   * {@code toolchain.host} from the project's {@code build.ums}, as the file the launcher
   * runs, or null when there is no manifest or no host. {@code umsProjectHost} in the
   * compiler's {@code ums/model/workspace.psm} is the rule this mirrors.
   */
  static @Nullable Path declaredHost(@NotNull Path projectRoot) {
    Path manifest = projectRoot.resolve("build.ums");
    if (!Files.isRegularFile(manifest)) {
      return null;
    }
    String source;
    try {
      source = Files.readString(manifest);
    } catch (java.io.IOException e) {
      return null;
    }
    Matcher m = TOOLCHAIN_HOST.matcher(source);
    if (!m.find()) {
      return null;
    }
    String host = m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    if (host.isEmpty()) {
      return null;
    }
    if (SystemInfo.isWindows && !host.endsWith(".exe")) {
      host = host + ".exe";
    }
    Path path = Path.of(host);
    return (path.isAbsolute() ? path : projectRoot.resolve(path)).normalize();
  }

  private static boolean isValidExecutable(@NotNull Path path) {
    return Files.isRegularFile(path) && Files.isExecutable(path);
  }

}
