package io.prismio.execution;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.prismio.psi.PrismioFile;
import org.jetbrains.annotations.NotNull;

public final class PrismioRunConfigurationProducer extends LazyRunConfigurationProducer<PrismioRunConfiguration> {

  /** A program declares {@code fn main}; a module does not, and has nothing to run. */
  private static final java.util.regex.Pattern DECLARES_MAIN =
      java.util.regex.Pattern.compile("^\\s*(?:public\\s+)?fn\\s+main\\s*\\(", java.util.regex.Pattern.MULTILINE);

  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return PrismioRunConfigurationType.getInstance().getConfigurationFactories()[0];
  }

  @Override
  protected boolean setupConfigurationFromContext(
      @NotNull PrismioRunConfiguration configuration,
      @NotNull ConfigurationContext context,
      @NotNull Ref<PsiElement> sourceElement) {
    PsiElement location = context.getPsiLocation();
    if (location == null) {
      return false;
    }
    PsiFile file = location.getContainingFile();
    if (!(file instanceof PrismioFile)) {
      return false;
    }
    VirtualFile vf = file.getVirtualFile();
    if (vf == null) {
      return false;
    }
    // Only a program runs: `prismio run` on a module with no `main` -- most files of a
    // multi-file program -- fails, and offering it on every file hid the one that works.
    if (!DECLARES_MAIN.matcher(file.getText()).find()) {
      return false;
    }

    // A program that is a target's entry runs as that target -- `prismio run sandbox`,
    // through its manifest, with the profile, native sources and runtime it declares --
    // and is the same configuration the manifest put in the run widget.
    UmsRunConfigurations.Spec spec = entrySpec(file.getProject(), vf);
    if (spec != null) {
      UmsRunConfigurations.apply(configuration, spec);
      configuration.setGenerated(true);
      sourceElement.set(file);
      return true;
    }
    configuration.setFilePath(vf.getPath());
    configuration.setName("Run " + file.getName());
    sourceElement.set(file);
    return true;
  }

  private static UmsRunConfigurations.Spec entrySpec(com.intellij.openapi.project.Project project, VirtualFile vf) {
    if (com.intellij.openapi.project.DumbService.isDumb(project)) {
      return null;
    }
    return UmsRunConfigurations.runSpecForEntry(project, vf.getPath());
  }

  @Override
  public boolean isConfigurationFromContext(
      @NotNull PrismioRunConfiguration configuration,
      @NotNull ConfigurationContext context) {
    PsiElement location = context.getPsiLocation();
    if (location == null) {
      return false;
    }
    PsiFile file = location.getContainingFile();
    if (!(file instanceof PrismioFile)) {
      return false;
    }
    VirtualFile vf = file.getVirtualFile();
    if (vf == null) {
      return false;
    }
    UmsRunConfigurations.Spec spec = entrySpec(file.getProject(), vf);
    if (spec != null) {
      return configuration.getFilePath().isBlank()
          && PrismioRunConfiguration.RUN.equals(configuration.getCommand())
          && spec.target().equals(configuration.getTarget())
          && spec.workingDirectory().equals(configuration.getWorkingDirectory());
    }
    return vf.getPath().equals(configuration.getFilePath());
  }
}
