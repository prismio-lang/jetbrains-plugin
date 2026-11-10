package io.prismio.wizard;

import com.intellij.ide.wizard.AbstractNewProjectWizardStep;
import com.intellij.ide.wizard.NewProjectWizardBaseData;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.dsl.builder.Panel;
import io.prismio.toolchain.PrismioProjectSettings;
import java.io.IOException;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The compiler to use, then the project files, in IntelliJ IDEA's New Project dialog. The other
 * IDEs' dialog is {@link PrismioDirectoryProjectGenerator}; both share {@link CompilerSelector} and
 * {@link PrismioProjectFiles}.
 *
 * <p>The compiler comes first because the project is only as good as it: Create is refused until
 * the field holds one that answers {@code --version}, and the manifest's {@code prismio} line is
 * that version, as {@code init} writes its own.
 */
final class PrismioProjectStep extends AbstractNewProjectWizardStep {

  private final CompilerSelector compiler = new CompilerSelector();

  PrismioProjectStep(@NotNull NewProjectWizardStep parent) {
    super(parent);
  }

  @Override
  public void setupUI(@NotNull Panel panel) {
    compiler.addRows(panel);
  }

  @Override
  public void setupProject(@NotNull Project project) {
    NewProjectWizardBaseData base = NewProjectWizardBaseData.getBaseData(this);
    if (base == null) {
      return;
    }
    Path root = Path.of(base.getContentEntryPath());
    String name = PrismioProjectFiles.projectName(base.getName());
    String version = compiler.version() != null ? compiler.version() : PrismioProjectFiles.FALLBACK_VERSION;

    // Only a compiler other than the one this machine finds by itself is written down: the
    // default stays a project with no setting to go stale when the toolchain moves.
    String chosen = compiler.path();
    Path detected = compiler.detected();
    if (!chosen.isEmpty() && (detected == null || !chosen.equals(detected.toString()))) {
      PrismioProjectSettings.getInstance(project).getState().compilerPath = chosen;
    }
    try {
      VirtualFile main = WriteAction.compute(() -> PrismioProjectFiles.create(root, name, version));
      open(project, main);
    } catch (IOException e) {
      // The IDE reports a failed generator; the project itself still opens.
      throw new IllegalStateException("Could not create the Prismio project files in " + root, e);
    }
  }

  static void open(@NotNull Project project, @Nullable VirtualFile main) {
    if (main != null) {
      DumbService.getInstance(project).runWhenSmart(
          () -> new OpenFileDescriptor(project, main).navigate(true));
    }
  }
}
