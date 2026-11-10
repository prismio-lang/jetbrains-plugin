package io.prismio.lsp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspIntegrationProvider;
import io.prismio.toolchain.PrismioProjectSettings;
import io.prismio.toolchain.PrismioToolchainService;
import org.jetbrains.annotations.NotNull;

/**
 * Starts {@code prismio lsp} for a project the first time one of its Prismio files is opened.
 *
 * <p>The server is the toolchain's own: it checks each file as the program it belongs to with every
 * unsaved buffer, and answers what the editor asks from the compiler's semantic index. Which
 * features the editor takes from it is {@link PrismioLspClientDescriptor#getLspCustomization()}.
 * Loaded only where the platform has an LSP client ({@code prismio-lsp.xml}).
 */
public final class PrismioLspIntegrationProvider implements LspIntegrationProvider {

  @Override
  public void fileOpened(
      @NotNull Project project, @NotNull VirtualFile file, @NotNull LspClientStarter clientStarter) {
    if (!PrismioLspClientDescriptor.handles(file)) {
      return;
    }
    if (!PrismioProjectSettings.getInstance(project).getState().useLanguageServer) {
      return;
    }
    if (PrismioToolchainService.getInstance(project).getCompilerExecutable() == null) {
      return;
    }
    clientStarter.ensureClientStarted(new PrismioLspClientDescriptor(project));
  }
}
