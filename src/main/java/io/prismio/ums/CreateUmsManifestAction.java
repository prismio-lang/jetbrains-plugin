package io.prismio.ums;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import io.prismio.icons.PrismioIcons;
import io.prismio.toolchain.PrismioToolchainService;
import io.prismio.wizard.PrismioProjectFiles;
import java.nio.file.Path;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * New > UMS Manifest.
 *
 * <p>Creates {@code build.ums} in the chosen directory, with no name to type: it is the only
 * file name {@code prismio} and this plugin read, so a prompt could only produce a manifest
 * nothing finds. The project's name is the directory's, and {@code prismio = "..."} is the version
 * of the compiler the project is configured with, as `prismio init` writes it.
 *
 * <p>The template is the shape `prismio init` writes, minus the parts that only
 * make sense once a project has them: no `dependencies` block, because there is
 * no registry to fetch from, and no `toolchain` block, because a project only
 * needs one when it builds its own compiler.
 */
public final class CreateUmsManifestAction extends AnAction {
  static final String FILE_NAME = "build.ums";
  private static final String TEMPLATE = "UmsManifest";

  public CreateUmsManifestAction() {
    super(PrismioIcons.UMS);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    event.getPresentation().setEnabledAndVisible(
        event.getProject() != null && view != null && view.getDirectories().length > 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (project == null || view == null) {
      return;
    }
    PsiDirectory directory = view.getOrChooseDirectory();
    if (directory == null) {
      return;
    }
    if (directory.findFile(FILE_NAME) != null) {
      Messages.showErrorDialog(project, FILE_NAME + " already exists in " + directory.getName(),
          "Create UMS Manifest");
      return;
    }
    // Asking the compiler its version runs a process: not on the UI thread.
    String version = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> compilerVersion(project), "Reading the Prismio Compiler's Version", false, project);
    try {
      PsiElement created = WriteAction.compute(() -> create(project, directory, version));
      view.selectElement(created);
      if (created instanceof Navigatable navigatable) {
        navigatable.navigate(true);
      }
    } catch (Exception failure) {
      Messages.showErrorDialog(project, String.valueOf(failure.getMessage()), "Create UMS Manifest");
    }
  }

  /** The version of the compiler the project is configured with, or the fallback. Runs the compiler. */
  static @NotNull String compilerVersion(@NotNull Project project) {
    return versionOf(PrismioToolchainService.getInstance(project).getCompilerExecutable());
  }

  /** What {@code compiler --version} says, or {@link PrismioProjectFiles#FALLBACK_VERSION} when there is no compiler or it does not answer. */
  static @NotNull String versionOf(@Nullable Path compiler) {
    String version = compiler == null ? null : PrismioToolchainService.compilerVersion(compiler);
    return version != null ? version : PrismioProjectFiles.FALLBACK_VERSION;
  }

  /** {@code build.ums} in {@code directory}, from the template. Needs a write action. */
  static PsiElement create(@NotNull Project project, @NotNull PsiDirectory directory, @NotNull String version)
      throws Exception {
    FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate(TEMPLATE);
    Properties properties = FileTemplateManager.getInstance(project).getDefaultProperties();
    properties.setProperty("NAME", directory.getName());
    properties.setProperty("PRISMIO_VERSION", version);
    return FileTemplateUtil.createFromTemplate(template, FILE_NAME, properties, directory);
  }
}
