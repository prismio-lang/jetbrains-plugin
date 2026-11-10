package io.prismio.ums;

import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.toolchain.PrismioProjectSettings;
import io.prismio.wizard.PrismioProjectFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * New | UMS Manifest writes the version of the compiler the project uses, as `prismio init` does. It wrote
 * {@code prismio = "0.1.0"} whatever the compiler was.
 */
public class CreateUmsManifestActionTest extends BasePlatformTestCase {

  public void testTheFallbackIsThisReleasesCompiler() {
    assertEquals("0.2.0", PrismioProjectFiles.FALLBACK_VERSION);
  }

  public void testNoCompilerOrOneThatDoesNotAnswerGivesTheFallback() {
    assertEquals(PrismioProjectFiles.FALLBACK_VERSION, CreateUmsManifestAction.versionOf(null));
    assertEquals(PrismioProjectFiles.FALLBACK_VERSION,
        CreateUmsManifestAction.versionOf(Path.of("/nonexistent/prismio")));
  }

  public void testTheManifestNamesTheVersionItIsGiven() throws Exception {
    PsiDirectory directory = myFixture.getPsiManager().findDirectory(myFixture.getTempDirFixture().findOrCreateDir("fresh"));
    assertNotNull(directory);
    PsiElement created = WriteAction.compute(() -> CreateUmsManifestAction.create(getProject(), directory, "7.8.9"));
    String text = ((PsiFile) created).getText();
    assertTrue(text, text.contains("prismio = \"7.8.9\""));
    assertTrue(text, text.contains("name = \"fresh\""));
    assertFalse(text, text.contains("${"));
  }

  /** The compiler the run was pointed at is asked, not a number written in the plugin. Needs {@code -Dprismio.compiler}. */
  public void testTheVersionIsTheConfiguredCompilersOwn() throws Exception {
    String compiler = System.getProperty("prismio.compiler");
    if (compiler == null) {
      return;
    }
    Process process = new ProcessBuilder(compiler, "--version").start();
    String first = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElse("");
    assertEquals(0, process.waitFor());
    String expected = first.replaceFirst("^prismio ", "").trim();
    assertFalse(first, expected.isEmpty());

    PrismioProjectSettings.getInstance(getProject()).getState().compilerPath = compiler;
    assertEquals(expected, CreateUmsManifestAction.compilerVersion(getProject()));
    assertEquals(expected, CreateUmsManifestAction.versionOf(Path.of(compiler)));
  }
}
