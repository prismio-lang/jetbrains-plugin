package io.prismio.lsp;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor;
import com.intellij.platform.lsp.api.customization.LspCustomization;
import io.prismio.toolchain.PrismioToolchainService;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/**
 * How to run and talk to {@code prismio lsp}: the compiler the project is configured with, started
 * in the project's folder, for {@code .psm} sources and {@code build.ums} manifests.
 */
final class PrismioLspServerDescriptor extends ProjectWideLspServerDescriptor {

  PrismioLspServerDescriptor(@NotNull Project project) {
    super(project, "Prismio");
  }

  static boolean handles(@NotNull VirtualFile file) {
    return "psm".equals(file.getExtension()) || file.getName().endsWith(".ums");
  }

  @Override
  public boolean isSupportedFile(@NotNull VirtualFile file) {
    return handles(file);
  }

  @Override
  public @NotNull String getLanguageId(@NotNull VirtualFile file) {
    return "psm".equals(file.getExtension()) ? "prismio" : "ums";
  }

  @Override
  public @NotNull GeneralCommandLine createCommandLine() throws ExecutionException {
    Path compiler = PrismioToolchainService.getInstance(getProject()).getCompilerExecutable();
    if (compiler == null) {
      throw new ExecutionException("No Prismio compiler is configured (Settings | Languages & Frameworks | Prismio)");
    }
    GeneralCommandLine line = new GeneralCommandLine(compiler.toString(), "lsp");
    // Inside a project that names its own compiler, a launcher named `prismio` hands the command to that
    // project's host, which may be any generation and may not have a server. The editor wants the
    // toolchain it was pointed at, whatever folder is open.
    line.withEnvironment("PRISMIO_INTERNAL_HOSTED", "1");
    String base = getProject().getBasePath();
    if (base != null) {
      line.withWorkingDirectory(Path.of(base));
    }
    return line;
  }

  @Override
  public @NotNull LspCustomization getLspCustomization() {
    return PrismioLspFeatures.customization();
  }
}
