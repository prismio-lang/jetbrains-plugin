package io.prismio.execution;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.ums.UmsManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The run widget made from build.ums: what it lists, and that it follows the manifest. */
public class UmsRunConfigurationsTest extends BasePlatformTestCase {

  private static final String MANIFEST = """
      project {
          name = "app"
          version = "0.1.0"
      }

      targets {
          executable("app") {
              entry = "src/main.psm"
              native {
                  source("c/fast.c")
              }
          }
          executable("tool") {
              entry = "src/tool.psm"
          }
          test("unit") {
              entry = "tests/unit.psm"
          }
      }

      commands {
          command("dist") {
              description = "Package it"
              run("tools/package.py", args)
          }
          command("lint") {
              run("tools/lint.py")
          }
      }
      """;

  public void testManifestReading() {
    UmsManifest manifest = UmsManifest.read(MANIFEST);
    assertEquals("app", manifest.projectName());
    assertEquals(List.of("app", "tool", "unit"), manifest.targets().stream().map(UmsManifest.Target::name).toList());
    assertEquals("src/main.psm", manifest.targets().get(0).entry());
    assertEquals(List.of("dist", "lint"), manifest.commands().stream().map(UmsManifest.Command::name).toList());
    assertEquals("Package it", manifest.commands().get(0).description());
  }

  /** The compiler's own manifest, with -Dprismio.checkout. */
  public void testTheCheckoutManifest() throws Exception {
    String checkout = System.getProperty("prismio.checkout");
    if (checkout == null) {
      return;
    }
    UmsManifest manifest = UmsManifest.read(Files.readString(Path.of(checkout, "build.ums")));
    assertEquals("prismio", manifest.projectName());
    // The compiler and its language server are the two targets; either order is the manifest's own business.
    assertContainsElements(manifest.targets().stream().map(UmsManifest.Target::entry).toList(),
        "src/main.psm", "lsp/main.psm");
    assertContainsElements(manifest.commands().stream().map(UmsManifest.Command::name).toList(),
        "release", "suite", "bench", "lists", "verify", "gate");
  }

  private Map<String, String> widget() {
    Map<String, String> out = new TreeMap<>();
    for (RunnerAndConfigurationSettings settings :
        RunManager.getInstance(getProject()).getConfigurationSettingsList(PrismioRunConfigurationType.getInstance())) {
      PrismioRunConfiguration c = (PrismioRunConfiguration) settings.getConfiguration();
      out.put(settings.getName(), c.getCommand() + " " + c.getTarget());
    }
    return out;
  }

  private void sync() {
    var manifests = UmsRunConfigurations.manifests(getProject());
    UmsRunConfigurations.sync(getProject(), UmsRunConfigurations.specs(manifests),
        UmsRunConfigurations.entryPaths(manifests));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RunManager runManager = RunManager.getInstance(getProject());
      for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
        runManager.removeConfiguration(settings);
      }
    } finally {
      super.tearDown();
    }
  }

  public void testWidgetListsTargetsAndCommandsAndFollowsTheManifest() {
    PsiFile manifest = myFixture.addFileToProject("build.ums", MANIFEST);
    myFixture.addFileToProject("src/main.psm", "fn main() -> Int {\n    return 0\n}\n");
    // A fixture's manifest is not the project's.
    myFixture.addFileToProject("tests/fixtures/valid/build.ums", "targets {\n    executable(\"fixture\") {\n    }\n}\n");
    sync();
    assertEquals(Map.of(
        "Run app", "run app",
        "Run tool", "run tool",
        "Build", "build ",
        "Test", "test ",
        "dist", "dist ",
        "lint", "lint "), widget());
    assertEquals("Run app", RunManager.getInstance(getProject()).getSelectedConfiguration().getName());

    // A configuration the user made is theirs, even with a name the manifest drops.
    RunManager runManager = RunManager.getInstance(getProject());
    RunnerAndConfigurationSettings mine = runManager.createConfiguration("mine",
        PrismioRunConfigurationType.getInstance().getConfigurationFactories()[0]);
    runManager.addConfiguration(mine);

    // Drop `lint` and `tool` from the manifest: their entries go, nothing else does.
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      var document = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(manifest);
      String text = document.getText()
          .replace("    command(\"lint\") {\n        run(\"tools/lint.py\")\n    }\n", "")
          .replace("    executable(\"tool\") {\n        entry = \"src/tool.psm\"\n    }\n", "");
      assertFalse("the edit must land", text.contains("lint") || text.contains("tool.psm"));
      document.setText(text);
      com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(document);
    });
    sync();
    Map<String, String> after = widget();
    assertFalse(after.containsKey("lint"));
    assertFalse(after.containsKey("Run tool"));
    assertTrue(after.containsKey("dist"));
    assertTrue(after.containsKey("mine"));
  }

  public void testAnEntryFileRunsItsTarget() {
    myFixture.addFileToProject("build.ums", MANIFEST);
    PsiFile main = myFixture.addFileToProject("src/main.psm", "fn main() -> Int {\n    return 0\n}\n");
    PsiFile loose = myFixture.addFileToProject("scratch/try.psm", "fn main() -> Int {\n    return 0\n}\n");
    UmsRunConfigurations.Spec spec = UmsRunConfigurations.runSpecForEntry(getProject(), main.getVirtualFile().getPath());
    assertNotNull(spec);
    assertEquals("Run app", spec.name());
    assertEquals("app", spec.target());
    assertNull(UmsRunConfigurations.runSpecForEntry(getProject(), loose.getVirtualFile().getPath()));
  }

  public void testGutterIconsOnRunnableDeclarations() {
    PsiFile file = myFixture.configureByText("build.ums", MANIFEST);
    UmsRunLineMarkerContributor gutter = new UmsRunLineMarkerContributor();
    String text = file.getText();
    for (String word : List.of("executable(\"app\")", "test(\"unit\")", "command(\"dist\")")) {
      PsiElement element = file.findElementAt(text.indexOf(word));
      RunLineMarkerContributor.Info info = gutter.getInfo(element);
      assertNotNull(word, info);
    }
    for (String word : List.of("project {", "source(", "run(\"tools")) {
      assertNull(word, gutter.getInfo(file.findElementAt(text.indexOf(word))));
    }
  }

  public void testAnOldFileRunOfAnEntryGivesWayUnlessCustomised() {
    myFixture.addFileToProject("build.ums", MANIFEST);
    PsiFile main = myFixture.addFileToProject("src/main.psm", "fn main() -> Int {\n    return 0\n}\n");
    PsiFile tool = myFixture.addFileToProject("src/tool.psm", "fn main() -> Int {\n    return 0\n}\n");
    RunManager runManager = RunManager.getInstance(getProject());
    var factory = PrismioRunConfigurationType.getInstance().getConfigurationFactories()[0];
    RunnerAndConfigurationSettings plain = runManager.createConfiguration("Run main.psm", factory);
    ((PrismioRunConfiguration) plain.getConfiguration()).setFilePath(main.getVirtualFile().getPath());
    runManager.addConfiguration(plain);
    RunnerAndConfigurationSettings customised = runManager.createConfiguration("Run tool.psm", factory);
    ((PrismioRunConfiguration) customised.getConfiguration()).setFilePath(tool.getVirtualFile().getPath());
    ((PrismioRunConfiguration) customised.getConfiguration()).setProgramArgs("--verbose");
    runManager.addConfiguration(customised);
    sync();
    Map<String, String> widget = widget();
    assertFalse(widget.containsKey("Run main.psm"));
    assertTrue(widget.containsKey("Run tool.psm"));
    assertTrue(widget.containsKey("Run app"));
  }
}
