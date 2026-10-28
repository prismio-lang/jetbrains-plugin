package io.prismio.lsp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.PrismioFileType;
import io.prismio.symbols.FnSig;
import io.prismio.symbols.ProjectSymbols;
import io.prismio.symbols.TypeInference;
import io.prismio.toolchain.PrismioProjectSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parameter info: the signatures over a call's arguments and the one the caret is in, from the plugin and from
 * the server.
 *
 * <p>For each call the plugin recognises, at the first argument and after its first comma, the server must
 * answer too, name the same active parameter, and offer every parameter list the plugin offers. It may offer
 * more overloads.
 */
public class PrismioSignatureDifferentialTest extends BasePlatformTestCase {

  public void testServerSignatureHelpCoversTheParameterInfo() throws Exception {
    try (ServerCorpus corpus = ServerCorpus.start()) {
      if (corpus == null) {
        return;
      }
      PrismioProjectSettings.getInstance(getProject()).getState().stdlibPath = corpus.stdlib().toString();
      int plugin = 0;
      int agreed = 0;
      List<String> disagreements = new ArrayList<>();
      for (Path file : corpus.files(Integer.getInteger("prismio.differentialLimit", 60), false)) {
        String text = Files.readString(file);
        PsiFile psi = myFixture.configureByText(PrismioFileType.INSTANCE, text);
        if (!corpus.probe.open(file, text, 90)) {
          disagreements.add(corpus.root.relativize(file) + ": the server never analysed it");
          continue;
        }
        Document document = psi.getViewProvider().getDocument();
        String relative = corpus.root.relativize(file).toString();
        for (int open = text.indexOf('('); open >= 0; open = text.indexOf('(', open + 1)) {
          for (int offset : new int[] {open + 1, afterFirstComma(text, open)}) {
            if (offset < 0) {
              continue;
            }
            TypeInference.Call call = TypeInference.callAt(text, offset, ProjectSymbols.scope(psi), ProjectSymbols.summary(psi).imports());
            if (call == null || call.overloads().isEmpty()) {
              continue;
            }
            if (insideLiteralOrComment(text, call.openParen())) {
              continue; // the plugin read a call in a string
            }
            if (declaresFunction(text, call.openParen())) {
              continue; // the plugin offered parameter info over a declaration's own parameter list
            }
            plugin++;
            int line = document.getLineNumber(offset);
            int column = offset - document.getLineStartOffset(line);
            String where = relative + ":" + (line + 1) + ":" + (column + 1);
            JsonElement help = corpus.probe.at("textDocument/signatureHelp", file, line, column);
            if (help == null || !help.isJsonObject() || !help.getAsJsonObject().has("signatures")) {
              disagreements.add(where + ": the server says nothing; the plugin offers " + describe(call));
              continue;
            }
            JsonObject answer = help.getAsJsonObject();
            Set<String> offered = parameterLists(answer);
            if (precededByDot(text, call.openParen())) {
              // `x.f(a)` fills the first parameter with `x`; the plugin's popup leaves that one out.
              offered.addAll(parameterLists(answer, true));
            }
            List<String> missing = new ArrayList<>();
            List<String> imported = new ArrayList<>();
            for (io.prismio.symbols.ModuleSummary.Import i : ProjectSymbols.summary(psi).imports()) {
              imported.add(i.wildcard() ? i.path() + "." : i.path());
            }
            for (FnSig fn : call.overloads()) {
              // An overload of a module the file does not import is one the program cannot call.
              if (!fn.module().isEmpty() && imported.stream().noneMatch(i -> i.endsWith(".") ? fn.module().startsWith(i) : i.equals(fn.module()))) {
                continue;
              }
              String list = fn.callParams(call.asMethod() || fn.owner() == null && precededByDot(text, call.openParen()))
                  .stream().map(p -> p.type() == null ? p.name() : p.name() + ": " + p.type()).collect(java.util.stream.Collectors.joining(", "));
              if (!covered(normalise(list), offered)) {
                missing.add(list);
              }
            }
            int active = answer.has("activeParameter") ? answer.get("activeParameter").getAsInt() : 0;
            if (!missing.isEmpty()) {
              disagreements.add(where + " (" + text.substring(Math.max(0, offset - 12), offset).replace('\n', ' ') + ") only the plugin offers " + missing);
            } else if (active != call.argument() && !(precededByDot(text, call.openParen()) && active == call.argument() + 1)) {
              disagreements.add(where + ": active parameter " + call.argument() + " in the plugin, " + active + " in the server");
            } else {
              agreed++;
            }
          }
        }
        corpus.probe.closeDocument(file);
      }
      Path dump = Path.of("build/differential");
      Files.createDirectories(dump);
      Files.writeString(dump.resolve("signature.txt"), String.join("\n", disagreements));
      System.out.println("SIGNATURE: plugin " + plugin + ", agreed " + agreed);
      assertTrue("the plugin offered signatures (" + plugin + ")", plugin > 50);
      assertTrue(disagreements.size() + " of " + plugin + " differ:\n" + String.join("\n", disagreements),
          disagreements.isEmpty());
    }
  }

  private static boolean insideLiteralOrComment(String text, int offset) {
    int lineStart = text.lastIndexOf('\n', offset) + 1;
    String before = text.substring(lineStart, offset);
    int quotes = 0;
    for (int i = 0; i < before.length(); i++) {
      if (before.charAt(i) == '"' && (i == 0 || before.charAt(i - 1) != '\\')) {
        quotes++;
      }
    }
    return quotes % 2 == 1 || before.contains("//");
  }

  private static boolean declaresFunction(String text, int openParen) {
    int lineStart = text.lastIndexOf('\n', openParen) + 1;
    return text.substring(lineStart, openParen).matches("\\s*(public |private |internal |extern |export |hot |noinline |inline\\((always|never)\\) |const |async )*fn\\s+.*");
  }

  /**
   * Whether a parameter list is one the server offers: the same text, or the server's generic signature
   * ({@code needle: T}) for the plugin's instance of it ({@code needle: I64}), or any variadic one for an
   * empty list (the plugin reads {@code print(...args: Ts)} as taking none).
   */
  private static boolean covered(String list, Set<String> offered) {
    if (offered.contains(list)) {
      return true;
    }
    for (String candidate : offered) {
      if (list.isEmpty() && candidate.contains("...")) {
        return true;
      }
      if (candidate.matches(".*\\bT\\b.*") && !candidate.contains("(")) {
        String pattern = java.util.regex.Pattern.quote(candidate).replace("T", "\\E[^,]+\\Q");
        if (list.matches(pattern.replace("\\Q\\E", ""))) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean precededByDot(String text, int openParen) {
    int k = openParen - 1;
    while (k >= 0 && Character.isJavaIdentifierPart(text.charAt(k))) {
      k--;
    }
    return k >= 0 && text.charAt(k) == '.';
  }

  /** The offset just after the first comma at the call's own depth, or -1 when it has one argument. */
  private static int afterFirstComma(String text, int open) {
    int depth = 0;
    for (int i = open + 1; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '(' || c == '[' || c == '{') {
        depth++;
      } else if (c == ')' || c == ']' || c == '}') {
        if (depth == 0) {
          return -1;
        }
        depth--;
      } else if (c == '"') {
        i++;
        while (i < text.length() && text.charAt(i) != '"' && text.charAt(i) != '\n') {
          i += text.charAt(i) == '\\' ? 2 : 1;
        }
      } else if (c == '\n') {
        return -1;
      } else if (c == ',' && depth == 0) {
        return i + 1;
      }
    }
    return -1;
  }

  private static String describe(TypeInference.Call call) {
    List<String> lists = new ArrayList<>();
    for (FnSig fn : call.overloads()) {
      lists.add(fn.parameterText(call.asMethod()));
    }
    return lists.toString();
  }

  /** The parameter lists of the server's signatures, from the labels' parameter ranges, in the plugin's spelling. */
  private static Set<String> parameterLists(JsonObject answer) {
    return parameterLists(answer, false);
  }

  private static Set<String> parameterLists(JsonObject answer, boolean withoutFirst) {
    Set<String> lists = new HashSet<>();
    for (JsonElement s : answer.getAsJsonArray("signatures")) {
      JsonObject signature = s.getAsJsonObject();
      String label = signature.get("label").getAsString();
      JsonArray parameters = signature.has("parameters") ? signature.getAsJsonArray("parameters") : new JsonArray();
      List<String> parts = new ArrayList<>();
      for (JsonElement p : parameters) {
        JsonElement range = p.getAsJsonObject().get("label");
        if (range.isJsonArray()) {
          parts.add(label.substring(range.getAsJsonArray().get(0).getAsInt(), range.getAsJsonArray().get(1).getAsInt()));
        } else {
          parts.add(range.getAsString());
        }
      }
      if (withoutFirst && !parts.isEmpty()) {
        parts.remove(0);
      }
      lists.add(normalise(String.join(", ", parts)));
    }
    return lists;
  }

  private static String normalise(String list) {
    // A parameter's mode (`sink t: Tree`) is in the label, not in the range the server highlights.
    // The server's label lists the receiver (`self, value: T`); the popup the plugin shows leaves it out.
    return list.replaceAll("\\s*([<>])\\s*", "$1").replaceAll("Array<([^<>]*)>", "[$1]").replaceAll("^self(, |$)", "").replaceAll("\\b(sink|inout|borrow|unique) ", "").replaceAll("\\s+", " ").strip();
  }
}
