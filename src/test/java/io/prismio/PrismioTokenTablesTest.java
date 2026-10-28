package io.prismio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.prismio.lang.PrismioWords;
import io.prismio.lexer.PrismioLexer;
import io.prismio.psi.PrismioTypes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;

/**
 * The plugin's word tables and its lexer against the compiler's own vocabulary, {@code prismio dump-tokens}.
 *
 * <p>The lexer cannot ask a server: it has to colour a file before any answer, so it keeps tables. They are a copy of
 * what the compiler says, and this is what keeps the copy honest: a keyword, a type, an operator or a separator the
 * compiler has and the lexer does not know fails here, instead of showing up as a red or uncoloured word in someone's
 * editor. Skipped without {@code -Dprismio.compiler}.
 */
public class PrismioTokenTablesTest extends TestCase {

  /**
   * Contextual words the compiler reads in one position only and the plugin does not colour, because each is also an
   * ordinary name or type everywhere else: {@code Fn} (a callable type), {@code always} and {@code never}
   * ({@code inline(always)}), {@code C} ({@code repr(C)}).
   */
  private static final Set<String> NOT_COLOURED = Set.of("Fn", "always", "never", "C");

  private JsonObject tokens() throws Exception {
    String compiler = System.getProperty("prismio.compiler");
    if (compiler == null) {
      return null;
    }
    Process process = new ProcessBuilder(compiler, "dump-tokens").start();
    String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, process.waitFor());
    JsonObject root = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("the schema this test reads", 1, root.get("schemaVersion").getAsInt());
    return root;
  }

  private static Set<String> strings(JsonArray array) {
    Set<String> set = new HashSet<>();
    for (JsonElement e : array) {
      set.add(e.getAsString());
    }
    return set;
  }

  public void testTheKeywordsAreTheCompilers() throws Exception {
    JsonObject root = tokens();
    if (root == null) {
      return;
    }
    Set<String> compiler = strings(root.getAsJsonArray("keywords"));
    assertEquals("the lexer's KEYWORDS and the compiler's", compiler, PrismioWords.KEYWORDS);
  }

  public void testTheContextualKeywordsAreTheCompilersExceptWhatIsAnOrdinaryNameElsewhere() throws Exception {
    JsonObject root = tokens();
    if (root == null) {
      return;
    }
    Set<String> compiler = new HashSet<>();
    for (JsonElement e : root.getAsJsonArray("contextualKeywords")) {
      compiler.add(e.getAsJsonObject().get("word").getAsString());
    }
    Set<String> missing = new HashSet<>(compiler);
    missing.removeAll(PrismioWords.CONTEXTUAL_KEYWORDS);
    missing.removeAll(PrismioWords.BUILTIN_TYPES);
    missing.removeAll(NOT_COLOURED);
    assertTrue("contextual words the compiler reads and the lexer does not know: " + missing, missing.isEmpty());
    Set<String> invented = new HashSet<>(PrismioWords.CONTEXTUAL_KEYWORDS);
    invented.removeAll(compiler);
    assertTrue("contextual words the lexer colours and the compiler does not read: " + invented, invented.isEmpty());
  }

  public void testTheBuiltinTypesAreTheCompilers() throws Exception {
    JsonObject root = tokens();
    if (root == null) {
      return;
    }
    Set<String> compiler = new HashSet<>();
    JsonObject types = root.getAsJsonObject("types");
    for (String group : List.of("integer", "float", "other")) {
      compiler.addAll(strings(types.getAsJsonArray(group)));
    }
    assertEquals("the lexer's BUILTIN_TYPES and the compiler's", compiler, PrismioWords.BUILTIN_TYPES);
  }

  /** Every word, as the lexer reads it standing alone, is the token type the table says it is. */
  public void testEveryWordLexesAsItsClass() throws Exception {
    JsonObject root = tokens();
    if (root == null) {
      return;
    }
    List<String> wrong = new ArrayList<>();
    for (String word : strings(root.getAsJsonArray("keywords"))) {
      // `default` is a keyword that is a name when a call follows, and the booleans have a type of their own.
      IElementType expected = word.equals("true") || word.equals("false") ? PrismioTypes.BOOLEAN : PrismioTypes.KEYWORD;
      if (lexOne(word + " ") != expected) {
        wrong.add(word + " -> " + lexOne(word + " "));
      }
    }
    for (String group : List.of("integer", "float", "other")) {
      for (String word : strings(root.getAsJsonObject("types").getAsJsonArray(group))) {
        if (lexOne(word + " ") != PrismioTypes.BUILTIN_TYPE) {
          wrong.add(word + " -> " + lexOne(word + " "));
        }
      }
    }
    assertTrue("words the lexer reads as something other than the compiler's class: " + wrong, wrong.isEmpty());
  }

  /** Every operator and separator the compiler has is one token to the lexer, of a kind it agrees with. */
  public void testEveryOperatorAndSeparatorIsOneToken() throws Exception {
    JsonObject root = tokens();
    if (root == null) {
      return;
    }
    List<String> wrong = new ArrayList<>();
    for (JsonElement e : root.getAsJsonArray("operators")) {
      JsonObject operator = e.getAsJsonObject();
      String text = operator.get("text").getAsString();
      String kind = operator.get("kind").getAsString();
      List<IElementType> tokens = lexAll(" " + text + " ");
      if (tokens.size() != 1 || tokens.get(0) == TokenType.BAD_CHARACTER) {
        wrong.add(text + " lexes as " + tokens);
        continue;
      }
      if (!agrees(kind, tokens.get(0))) {
        wrong.add(text + " is " + kind + " to the compiler and " + tokens.get(0) + " to the lexer");
      }
    }
    for (String separator : strings(root.getAsJsonArray("separators"))) {
      List<IElementType> tokens = lexAll(" " + separator + " ");
      if (tokens.size() != 1 || tokens.get(0) == TokenType.BAD_CHARACTER) {
        wrong.add(separator + " lexes as " + tokens);
      }
    }
    assertTrue("operators and separators the lexer does not read as the compiler's: " + wrong, wrong.isEmpty());
  }

  /** The compiler groups bit and shift operators with the arithmetic ones; the lexer keeps them apart. */
  private static boolean agrees(String compilerKind, IElementType token) {
    return switch (compilerKind) {
      case "ARITHMETIC_OPERATOR" -> token == PrismioTypes.ARITHMETIC_OP || token == PrismioTypes.BITWISE_OP
          || token == PrismioTypes.SHIFT_OP || token == PrismioTypes.LOGICAL_OP;
      case "RELATIONAL_OPERATOR" -> token == PrismioTypes.RELATIONAL_OP;
      case "ASSIGNMENT_OPERATOR" -> token == PrismioTypes.ASSIGNMENT_OP;
      case "NEGATION" -> token == PrismioTypes.NEGATION;
      case "ARROW" -> token == PrismioTypes.ARROW;
      case "FAT_ARROW" -> token == PrismioTypes.FAT_ARROW;
      case "RANGE" -> token == PrismioTypes.RANGE;
      case "ELLIPSIS" -> token == PrismioTypes.ELLIPSIS;
      default -> true;
    };
  }

  private static IElementType lexOne(String text) {
    List<IElementType> all = lexAll(text);
    return all.isEmpty() ? null : all.get(0);
  }

  private static List<IElementType> lexAll(String text) {
    PrismioLexer lexer = new PrismioLexer();
    lexer.start(text, 0, text.length(), 0);
    List<IElementType> tokens = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() != TokenType.WHITE_SPACE) {
        tokens.add(lexer.getTokenType());
      }
      lexer.advance();
    }
    return tokens;
  }
}
