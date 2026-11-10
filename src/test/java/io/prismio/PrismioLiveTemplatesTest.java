package io.prismio;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import junit.framework.TestCase;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Every live template, expanded, against the compiler. A template that writes {@code ;} (which is not in the language)
 * or a {@code match} without parentheses gives the user code that does not build, and nothing else in the suite looks
 * at the template file. The compile half runs with {@code -Dprismio.compiler}; without it the file is still held to the
 * rule that no template contains {@code ;}.
 */
public class PrismioLiveTemplatesTest extends TestCase {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\$([A-Z0-9_]+)\\$");

  /** A template whose text is a declaration is checked as a file; any other is a statement and goes inside main. */
  private static final Pattern DECLARATION = Pattern.compile("^(fn|struct|enum|codeset|impl|extern)\\b.*", Pattern.DOTALL);

  /** Valid text for every placeholder, by name. A template may override one below. */
  private static final Map<String, String> VALUES = Map.ofEntries(
      Map.entry("NAME", "name"), Map.entry("PARAMS", ""), Map.entry("TYPE", "Int"),
      Map.entry("FIELD", "field"), Map.entry("VARIANT1", "First"), Map.entry("VARIANT2", "Second"),
      Map.entry("ENTRY1", "One"), Map.entry("ENTRY2", "Two"), Map.entry("VALUE1", "0"), Map.entry("VALUE2", "1"),
      Map.entry("METHOD", "method"), Map.entry("CONDITION", "true"), Map.entry("THEN", ""),
      Map.entry("VAR", "i"), Map.entry("ITER", "collection"), Map.entry("FROM", "0"), Map.entry("TO", "3"),
      Map.entry("VALUE", "0"), Map.entry("TEXT", "\"hello\""), Map.entry("PATTERN", "0"),
      Map.entry("RESULT", "println(\"a\")"), Map.entry("DEFAULT", "println(\"b\")"));

  private static final Map<String, Map<String, String>> OVERRIDES = Map.of(
      "struct", Map.of("NAME", "Shape", "TYPE", "Int"),
      "enum", Map.of("NAME", "Shape"),
      "codeset", Map.of("NAME", "Shape", "TYPE", "U16"),
      "impl", Map.of("NAME", "Holder"),
      "match", Map.of("VALUE", "scrutinee"));

  /** What the user types at {@code $END$} where the template cannot compile without it: a body for a function that returns. */
  private static final Map<String, String> AT_CURSOR = Map.of("fn", "return 0");

  /** Declarations a template's text refers to, put before it. */
  private static final Map<String, String> PRELUDE = Map.of("impl", "struct Holder { n: Int }\n\n");

  private static Map<String, String> templates() throws Exception {
    try (InputStream in = PrismioLiveTemplatesTest.class.getResourceAsStream("/liveTemplates/Prismio.xml")) {
      assertNotNull("the live template file is on the classpath", in);
      NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in)
          .getElementsByTagName("template");
      Map<String, String> result = new LinkedHashMap<>();
      for (int i = 0; i < nodes.getLength(); i++) {
        Element element = (Element) nodes.item(i);
        result.put(element.getAttribute("name"), element.getAttribute("value"));
      }
      return result;
    }
  }

  private static String expand(String name, String text) {
    Matcher matcher = PLACEHOLDER.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String variable = matcher.group(1);
      String value = variable.equals("END") ? AT_CURSOR.getOrDefault(name, "") : OVERRIDES.getOrDefault(name, Map.of()).getOrDefault(variable, VALUES.get(variable));
      assertNotNull("template " + name + " has a placeholder $" + variable + "$ this test has no value for", value);
      matcher.appendReplacement(out, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  public void testNoTemplateContainsASemicolon() throws Exception {
    Map<String, String> templates = templates();
    assertFalse(templates.isEmpty());
    List<String> bad = new ArrayList<>();
    for (Map.Entry<String, String> e : templates.entrySet()) {
      if (e.getValue().contains(";")) {
        bad.add(e.getKey());
      }
    }
    assertTrue("`;` is not in the language; templates that write it: " + bad, bad.isEmpty());
  }

  public void testEveryTemplateExpandsToCodeTheCompilerAccepts() throws Exception {
    if (CompilerCheck.compiler() == null) {
      return;
    }
    List<String> failures = new ArrayList<>();
    for (Map.Entry<String, String> e : templates().entrySet()) {
      String name = e.getKey();
      String body = expand(name, e.getValue());
      String file;
      if (DECLARATION.matcher(body).matches()) {
        file = "import std.io\n\n" + PRELUDE.getOrDefault(name, "") + body + "\n";
      } else {
        file = "import std.io\n\nfn main() -> Int {\n    let collection = [1, 2]\n    let scrutinee = 0\n"
            + indent(body) + "\n    return 0\n}\n";
      }
      String result = CompilerCheck.check(file);
      if (!result.isEmpty()) {
        failures.add("template `" + name + "` expands to\n" + file + "\nand the compiler says:\n" + result);
      }
    }
    assertTrue(String.join("\n----\n", failures), failures.isEmpty());
  }

  private static String indent(String body) {
    return "    " + body.replace("\n", "\n    ");
  }
}
