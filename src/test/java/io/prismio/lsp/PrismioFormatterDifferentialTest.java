package io.prismio.lsp;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.PrismioFileType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The plugin's formatter against the compiler's.
 *
 * <p>{@code prismio fmt} is the formatter; the plugin's {@code PrismioBlock} is a second one that must agree with it
 * wherever it is used. Each file of the checkout is formatted by the compiler, and the result is given to the
 * plugin's formatter, which has nothing to change in text that is already formatted. Every file it does change is a
 * disagreement, and a change of the token stream is a corruption.
 */
public class PrismioFormatterDifferentialTest extends BasePlatformTestCase {

  public void testThePluginLeavesWhatTheCompilerFormattedAlone() throws Exception {
    String checkout = System.getProperty("prismio.checkout");
    String compiler = System.getProperty("prismio.compiler");
    if (checkout == null || compiler == null) {
      return;
    }
    Path root = Path.of(checkout).toRealPath();
    List<Path> files = new ArrayList<>();
    for (String directory : List.of("tests", "lsp", "std")) {
      try (Stream<Path> walk = Files.walk(root.resolve(directory))) {
        walk.filter(p -> p.toString().endsWith(".psm") && !p.getFileName().toString().startsWith("neg_")).sorted().forEach(files::add);
      }
    }
    int stride = Math.max(1, files.size() / Integer.getInteger("prismio.differentialLimit", 150));
    List<String> changed = new ArrayList<>();
    List<String> corrupted = new ArrayList<>();
    int compared = 0;
    for (int i = 0; i < files.size(); i += stride) {
      Path file = files.get(i);
      Process process = new ProcessBuilder(compiler, "fmt", file.toString()).redirectErrorStream(false).start();
      String formatted = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (process.waitFor() != 0 || formatted.isEmpty()) {
        continue;
      }
      // The fixture reads {@code <caret>} as a marker and removes it, so a file that writes one in a comment is compared without it.
      formatted = formatted.replace("<caret>", "");
      PsiFile psi = myFixture.configureByText(PrismioFileType.INSTANCE, formatted);
      WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () ->
          CodeStyleManager.getInstance(getProject()).reformat(psi));
      compared++;
      String relative = root.relativize(file).toString();
      if (!tokens(formatted).equals(tokens(psi.getText()))) {
        corrupted.add(relative);
        Files.createDirectories(Path.of("build/differential"));
        Files.writeString(Path.of("build/differential/" + file.getFileName() + ".before"), formatted);
        Files.writeString(Path.of("build/differential/" + file.getFileName() + ".after"), psi.getText());
      } else if (!formatted.equals(psi.getText())) {
        changed.add(relative);
        Files.createDirectories(Path.of("build/differential"));
        Files.writeString(Path.of("build/differential/" + file.getFileName() + ".before"), formatted);
        Files.writeString(Path.of("build/differential/" + file.getFileName() + ".after"), psi.getText());
      }
    }
    Files.createDirectories(Path.of("build/differential"));
    Files.writeString(Path.of("build/differential/formatter.txt"),
        "changed:\n" + String.join("\n", changed) + "\n\ncorrupted:\n" + String.join("\n", corrupted));
    System.out.println("FORMATTER: compared " + compared + ", the plugin changed " + changed.size() + ", of those corrupted "
        + corrupted.size());
    assertTrue("compared files (" + compared + ")", compared > 50);
    assertTrue(changed.size() + " files the compiler formatted are changed by the plugin, " + corrupted.size() + " corrupted: "
        + corrupted.subList(0, Math.min(10, corrupted.size())), changed.isEmpty() && corrupted.isEmpty());
  }

  private static String tokens(String source) {
    io.prismio.lexer.PrismioLexer lexer = new io.prismio.lexer.PrismioLexer();
    lexer.start(source, 0, source.length(), 0);
    StringBuilder out = new StringBuilder();
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() != com.intellij.psi.TokenType.WHITE_SPACE) {
        out.append(source, lexer.getTokenStart(), lexer.getTokenEnd()).append('\u0001');
      }
      lexer.advance();
    }
    return out.toString();
  }
}
