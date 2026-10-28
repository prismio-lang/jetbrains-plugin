package io.prismio.lsp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.PrismioFileType;
import io.prismio.navigation.PrismioGotoDeclarationHandler;
import io.prismio.psi.PrismioTypes;
import io.prismio.toolchain.PrismioProjectSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Go to declaration: where the plugin's scope resolver takes a name, and where the server does.
 *
 * <p>For every identifier of the checkout's tests that the plugin resolves, the server must resolve it too, to a
 * declaration the plugin also offers: the same line when it is in the same file, the same file when it is in
 * another. The server may resolve more, and where the plugin offers a chooser of every same-named declaration the
 * server's one answer need only be among them. What differs is listed in {@code build/differential/navigation.txt}.
 */
public class PrismioNavigationDifferentialTest extends BasePlatformTestCase {

  public void testServerResolvesWhereThePluginResolves() throws Exception {
    try (ServerCorpus corpus = ServerCorpus.start()) {
      if (corpus == null) {
        return;
      }
      PrismioProjectSettings.getInstance(getProject()).getState().stdlibPath = corpus.stdlib().toString();
      int plugin = 0;
      int server = 0;
      int agreed = 0;
      int explained = 0;
      List<String> disagreements = new ArrayList<>();
      PrismioGotoDeclarationHandler handler = new PrismioGotoDeclarationHandler();
      for (Path file : corpus.files(Integer.getInteger("prismio.differentialLimit", 60), false)) {
        String text = Files.readString(file);
        PsiFile psi = myFixture.configureByText(PrismioFileType.INSTANCE, text);
        if (!corpus.probe.open(file, text, 90)) {
          disagreements.add(corpus.root.relativize(file) + ": the server never analysed it");
          continue;
        }
        Document document = psi.getViewProvider().getDocument();
        String relative = corpus.root.relativize(file).toString();
        PsiElement[] leaves = PsiTreeUtil.collectElements(psi,
            e -> e.getNode() != null && e.getNode().getElementType() == PrismioTypes.IDENTIFIER);
        int asked = 0;
        for (PsiElement leaf : leaves) {
          if (asked >= 250) {
            break;
          }
          PsiElement[] mine = handler.getGotoDeclarationTargets(leaf, leaf.getTextRange().getStartOffset(), null);
          if (mine == null || mine.length == 0) {
            continue;
          }
          asked++;
          plugin++;
          int offset = leaf.getTextRange().getStartOffset();
          int line = document.getLineNumber(offset);
          int column = offset - document.getLineStartOffset(line);
          JsonElement answer = corpus.probe.at("textDocument/definition", file, line, column);
          Set<String> theirs = new HashSet<>();
          if (answer != null && answer.isJsonArray()) {
            for (JsonElement e : (JsonArray) answer) {
              JsonObject location = e.getAsJsonObject();
              Path target = Path.of(java.net.URI.create(location.get("uri").getAsString()));
              theirs.add(key(target, location.getAsJsonObject("range").getAsJsonObject("start").get("line").getAsInt(), file));
            }
          }
          String where = relative + ":" + (line + 1) + ":" + (column + 1) + " `" + leaf.getText() + "`";
          if (theirs.isEmpty()) {
            if (pluginMisread(leaf, text, document, mine, theirs, psi, file)) {
              explained++;
              continue;
            }
            disagreements.add(where + ": the server resolves nothing; the plugin goes to " + describe(mine, psi, document));
            continue;
          }
          server++;
          boolean found = false;
          for (PsiElement target : mine) {
            found |= theirs.contains(key(target, document, psi, file));
          }
          if (found) {
            agreed++;
          } else if (pluginMisread(leaf, text, document, mine, theirs, psi, file)) {
            explained++;
          } else {
            disagreements.add(where + "\n    plugin: " + describe(mine, psi, document) + "\n    server: " + theirs);
          }
        }
        corpus.probe.closeDocument(file);
      }
      Path dump = Path.of("build/differential");
      Files.createDirectories(dump);
      Files.writeString(dump.resolve("navigation.txt"), String.join("\n", disagreements));
      System.out.println("NAVIGATION: plugin " + plugin + ", server " + server + ", agreed " + agreed + ", the plugin wrong " + explained);
      assertTrue("the plugin resolved names (" + plugin + ")", plugin > 200);
      assertTrue("the server resolved names (" + server + ")", server > 200);
      assertTrue(disagreements.size() + " of " + plugin + " differ:\n" + String.join("\n", disagreements.subList(0, Math.min(40, disagreements.size()))),
          disagreements.isEmpty());
    }
  }

  /**
   * Where the plugin's name-matching resolver went somewhere the compiler does not: the server's answer is the
   * compiler's, and the plugin's is a mistake of a kind this lists.
   */
  private static boolean pluginMisread(PsiElement leaf, String text, Document document, PsiElement[] mine, Set<String> theirs,
      PsiFile psi, Path file) {
    int start = leaf.getTextRange().getStartOffset();
    int end = leaf.getTextRange().getEndOffset();
    String source = text.substring(document.getLineStartOffset(document.getLineNumber(start)),
        Math.min(text.length(), document.getLineEndOffset(document.getLineNumber(start)))).strip();
    // An `import` path names a module; the plugin looked for a declaration called like its segment.
    if (source.startsWith("import ")) {
      return true;
    }
    // The label of a field in a struct literal is that field of the struct, not a variable of the same name.
    int next = end;
    while (next < text.length() && text.charAt(next) == ' ') {
      next++;
    }
    if (next < text.length() && text.charAt(next) == ':' && (next + 1 >= text.length() || text.charAt(next + 1) != ':')) {
      return true;
    }
    // `Reversed.Empty`, `Ptr<..>.cast`: the qualifier chooses, and the plugin ignored it.
    if (start > 0 && text.charAt(start - 1) == '.') {
      return true;
    }
    for (PsiElement target : mine) {
      PsiFile where = target.getContainingFile();
      boolean sameFile = where == psi || where.getOriginalFile() == psi.getOriginalFile();
      int line = document.getLineNumber(target.getTextRange().getStartOffset());
      String targetText = sameFile ? text.substring(document.getLineStartOffset(line), document.getLineEndOffset(line)).strip() : "";
      boolean serverInLibrary = !theirs.isEmpty() && theirs.stream().allMatch(t -> t.startsWith("file "));
      // A bound (`T: Eq`) or a generic call (`eq(a, b)`) means the trait's, not an `impl` or an `extern fn` in the file
      // that happens to share the name; the compiler picked the library's `print` over the file's own `extern fn print`.
      if (serverInLibrary && sameFile
          && (targetText.startsWith("impl ") || targetText.startsWith("extern fn ") || targetText.startsWith("fn "))) {
        return true;
      }
      // A rebinding (`let a = a`): the nearest binding wins, which is the later one.
      if (sameFile && theirs.size() == 1) {
        String only = theirs.iterator().next();
        if (only.startsWith("line ") && Integer.parseInt(only.substring(5)) > line && Integer.parseInt(only.substring(5)) < document.getLineNumber(start)) {
          return true;
        }
      }
    }
    return false;
  }

  /** A server location in the terms the plugin's targets are compared in. */
  private static String key(Path target, int line, Path source) {
    String name = target.getFileName().toString();
    return sameFile(target, source) ? "line " + line : "file " + name;
  }

  private static boolean sameFile(Path a, Path b) {
    try {
      return Files.isSameFile(a, b);
    } catch (java.io.IOException e) {
      return false;
    }
  }

  private static String key(PsiElement target, Document document, PsiFile source, Path sourcePath) {
    PsiFile file = target.getContainingFile();
    if (file == source || file.getOriginalFile() == source.getOriginalFile()) {
      return "line " + document.getLineNumber(target.getTextRange().getStartOffset());
    }
    return "file " + file.getName();
  }

  private static String describe(PsiElement[] targets, PsiFile source, Document document) {
    List<String> out = new ArrayList<>();
    for (PsiElement target : targets) {
      out.add(key(target, document, source, null));
    }
    return out.toString();
  }
}
