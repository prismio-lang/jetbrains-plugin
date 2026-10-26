package io.prismio.toolchain;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Persistent project-level settings for Prismio (toolchain path, stdlib path).
 */
@Service(Service.Level.PROJECT)
@State(name = "PrismioProjectSettings", storages = @Storage("prismio.xml"))
public final class PrismioProjectSettings implements PersistentStateComponent<PrismioProjectSettings.State> {

  public static class State {
    public String compilerPath = "";
    public String stdlibPath = "";
    /** Take diagnostics (and the features listed in io.prismio.lsp.PrismioLspFeatures) from `prismio lsp`. */
    public boolean useLanguageServer = true;
  }

  private State state = new State();

  public static PrismioProjectSettings getInstance(@NotNull Project project) {
    return project.getService(PrismioProjectSettings.class);
  }

  @Override
  public @NotNull State getState() {
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
  }
}
