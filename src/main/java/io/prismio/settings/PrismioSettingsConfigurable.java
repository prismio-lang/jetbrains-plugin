package io.prismio.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import io.prismio.toolchain.PrismioProjectSettings;
import io.prismio.toolchain.PrismioToolchainService;
import java.nio.file.Path;
import com.intellij.ui.components.JBCheckBox;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PrismioSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private final Project project;
  private TextFieldWithBrowseButton compilerPathField;
  private JBCheckBox useServerBox;
  private JPanel mainPanel;

  public PrismioSettingsConfigurable(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public @NotNull String getId() {
    return "io.prismio.settings";
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return "Prismio";
  }

  @Override
  public @Nullable JComponent createComponent() {
    compilerPathField = new TextFieldWithBrowseButton();
    compilerPathField.addBrowseFolderListener(
        project,
        FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
            .withTitle("Select Prismio Compiler Executable")
    );

    JBLabel detectStatus = new JBLabel();
    JButton autoDetectButton = new JButton("Auto-Detect");
    autoDetectButton.addActionListener(e -> {
      // Detection starts from what the field holds, so look as if it were empty.
      Path compiler = PrismioToolchainService.getInstance(project).detectCompilerExecutable();
      if (compiler != null) {
        compilerPathField.setText(compiler.toString());
        detectStatus.setText("Found " + compiler);
      } else {
        detectStatus.setText("No prismio found on PATH, in PRISMIO / PRISMIO_HOME or in the usual install folders.");
      }
    });

    useServerBox = new JBCheckBox("Use the Prismio language server (errors as you type, quick fixes and the features it provides; restart the IDE to apply)");

    mainPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Compiler executable:", compilerPathField)
        .addComponent(autoDetectButton)
        .addComponent(detectStatus)
        .addComponent(useServerBox)
        .addComponentFillVertically(new JPanel(), 0)
        .getPanel();

    return mainPanel;
  }

  @Override
  public boolean isModified() {
    PrismioProjectSettings.State state = PrismioProjectSettings.getInstance(project).getState();
    return !compilerPathField.getText().trim().equals(state.compilerPath)
        || useServerBox.isSelected() != state.useLanguageServer;
  }

  @Override
  public void apply() {
    PrismioProjectSettings.State state = PrismioProjectSettings.getInstance(project).getState();
    state.compilerPath = compilerPathField.getText().trim();
    state.useLanguageServer = useServerBox.isSelected();
  }

  @Override
  public void reset() {
    PrismioProjectSettings.State state = PrismioProjectSettings.getInstance(project).getState();
    compilerPathField.setText(state.compilerPath != null ? state.compilerPath : "");
    useServerBox.setSelected(state.useLanguageServer);
  }
}
