package io.prismio.lsp;

import com.intellij.openapi.project.Project;
import com.intellij.platform.lsp.api.LspServerManager;
import io.prismio.settings.PrismioSettingsListener;
import org.jetbrains.annotations.NotNull;

/**
 * Stops the server when the settings turn it off, and starts it again (with the compiler now
 * configured) when they leave it on: {@link PrismioLspServerSupportProvider} only decides at the
 * moment a file is opened, so without this a changed setting would wait for the next IDE start.
 */
final class PrismioLspSettingsListener implements PrismioSettingsListener {

  private final Project project;

  PrismioLspSettingsListener(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void toolchainSettingsChanged() {
    LspServerManager.getInstance(project).stopAndRestartIfNeeded(PrismioLspServerSupportProvider.class);
  }
}
