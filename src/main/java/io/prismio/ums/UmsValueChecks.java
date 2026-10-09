package io.prismio.ums;

import com.intellij.psi.tree.IElementType;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * The manifest checks the compiler makes on a value, made where they can be made without
 * reading another file: a name's spelling, a version's form, a fixed set of words, a boolean.
 *
 * <p>Each mirrors one rule of {@code ums/model/lowering.psm} or {@code validation.psm} and names
 * its diagnostic code, so a squiggle in the editor and the compiler's refusal say the same thing.
 * A check that needs the file system -- a source that must exist, a response file -- stays the
 * compiler's: an editor that guessed at the project root would be wrong about it more often
 * than it was useful.
 */
public final class UmsValueChecks {

  private UmsValueChecks() {}

  /** A refused value: the compiler's code, its message, and the token to underline. */
  public record Problem(@NotNull String code, @NotNull String message, int start, int end) {}

  /**
   * Everything wrong with the identifier {@code context} describes, most specific first. Empty
   * for a name in a position this file does not model.
   */
  public static @NotNull List<Problem> problemsFor(@NotNull UmsContext context) {
    List<Problem> problems = new ArrayList<>();
    switch (context.role) {
      case KEY -> checkKey(context, problems);
      case CALL -> {
        checkNative(context, problems);
        checkDeclaredName(context, problems);
      }
      case BLOCK -> checkDeclaredName(context, problems);
      default -> { }
    }
    return problems;
  }

  private static void checkKey(UmsContext context, List<Problem> out) {
    List<String> path = context.path;
    UmsContext.Value value = context.value;
    String key = context.name;
    if (value == null) {
      return;
    }

    if (path.equals(List.of("project"))) {
      switch (key) {
        case "name" -> {
          if (isString(value) && !validName(value.text())) {
            out.add(problem("UMS2302", "project.name may contain only letters, digits, '.', '_' and '-', "
                + "and starts with a letter, digit or _", value));
          }
        }
        case "version" -> {
          if (isString(value) && !validSemver(value.text())) {
            out.add(problem("UMS2304",
                "project.version must be a semantic version, such as 1.2.0 or 1.0.0-beta.1", value));
          }
        }
        case "prismio" -> {
          if (isString(value) && !validNumericVersion(value.text(), 2, 3)) {
            out.add(problem("UMS2306",
                "project.prismio must be MAJOR.MINOR or MAJOR.MINOR.PATCH, such as 0.1", value));
          }
        }
        default -> { }
      }
      if (isStringKey(key) && !isString(value)) {
        out.add(problem("UMS2001", "project." + key + " must be a string literal", value));
      }
      return;
    }

    if (path.equals(List.of("toolchain")) && key.equals("host")) {
      if (!isString(value)) {
        out.add(problem("UMS2001", "toolchain.host must be a string literal", value));
      } else if (value.text().isEmpty()) {
        out.add(problem("UMS2404", "toolchain.host cannot be empty", value));
      }
      return;
    }

    if (path.size() == 2 && path.get(0).equals("targets")) {
      switch (key) {
        case "runtime" -> {
          if (!isString(value)) {
            out.add(problem("UMS2001", "target runtime must be a string literal", value));
          } else if (!value.text().equals("installed") && !value.text().equals("none")) {
            out.add(problem("UMS2114",
                "target runtime is \"installed\" or \"none\", not \"" + value.text() + "\"", value));
          }
        }
        case "exportDynamic", "freestanding", "noRedZone" -> {
          if (!isBoolean(value)) {
            out.add(problem("UMS2115", "target " + key + " must be true or false", value));
          }
        }
        case "triple", "features" -> {
          if (!isString(value)) {
            out.add(problem("UMS2001", "target " + key + " must be a string literal", value));
          }
        }
        case "codeModel" -> {
          if (!isString(value)) {
            out.add(problem("UMS2001", "target codeModel must be a string literal", value));
          } else if (!List.of("small", "kernel", "medium", "large").contains(value.text())) {
            out.add(problem("UMS2116",
                "target codeModel is small, kernel, medium or large, not \"" + value.text() + "\"", value));
          }
        }
        case "entry" -> {
          if (!isString(value)) {
            out.add(problem("UMS2001", "target entry must be a string literal", value));
          }
        }
        default -> { }
      }
      return;
    }

    if (path.size() == 2 && path.get(0).equals("profiles")
        && (key.equals("debugInfo") || key.equals("overflowChecks")) && !isBoolean(value)) {
      out.add(problem("UMS2704", "profile setting '" + key + "' must be true or false", value));
    }
  }

  private static void checkNative(UmsContext context, List<Problem> out) {
    List<String> path = context.path;
    // `native { source("x.c") }`: the driver compiles C and links no C++ runtime.
    if (path.size() == 3 && path.get(0).equals("targets") && path.get(2).equals("native")
        && context.name.equals("source")) {
      for (UmsContext.Value argument : context.arguments) {
        if (isString(argument) && !argument.text().isEmpty() && !argument.text().endsWith(".c")) {
          out.add(problem("UMS2324", "native source '" + argument.text() + "' is not a C file (.c)", argument));
        }
      }
    }
  }

  /** A target's or command's name, which becomes a file and a word on the command line. */
  private static void checkDeclaredName(UmsContext context, List<Problem> out) {
    List<String> path = context.path;
    if (context.arguments.isEmpty()) {
      return;
    }
    UmsContext.Value name = context.arguments.get(0);
    if (!isString(name) || validName(name.text())) {
      return;
    }
    if (path.equals(List.of("targets"))) {
      out.add(problem("UMS2308", "invalid target name '" + name.text() + "': letters, digits, '.', '_' "
          + "and '-', starting with a letter, digit or _", name));
    } else if (path.equals(List.of("commands")) && context.name.equals("command")) {
      out.add(problem("UMS2601", "invalid command name '" + name.text() + "': letters, digits, '.', '_' "
          + "and '-', starting with a letter, digit or _", name));
    }
  }

  private static Problem problem(String code, String message, UmsContext.Value at) {
    return new Problem(code, message, at.start(), at.end());
  }

  private static boolean isStringKey(String key) {
    return key.equals("name") || key.equals("version") || key.equals("prismio")
        || key.equals("description") || key.equals("license") || key.equals("licenseFile");
  }

  private static boolean isString(UmsContext.Value value) {
    return UmsTypes.STRING.equals(value.type());
  }

  private static boolean isBoolean(UmsContext.Value value) {
    return UmsTypes.BOOLEAN.equals(value.type());
  }

  // ---- the compiler's rules, one function each --------------------------------------------

  /** {@code umsValidName}: a letter or digit (or {@code _}, see {@link #isAlnum}) first, then those or {@code - .}. */
  public static boolean validName(@NotNull String value) {
    if (value.isEmpty() || !isAlnum(value.charAt(0))) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!isAlnum(c) && c != '-' && c != '_' && c != '.') {
        return false;
      }
    }
    return true;
  }

  /** {@code umsValidSemver}: SemVer 2.0.0, three components, optional {@code -pre} and {@code +build}. */
  public static boolean validSemver(@NotNull String value) {
    String core = value;
    String pre = null;
    String build = null;
    int plus = value.indexOf('+');
    if (plus >= 0) {
      build = value.substring(plus + 1);
      core = value.substring(0, plus);
    }
    int dash = core.indexOf('-');
    if (dash >= 0) {
      pre = core.substring(dash + 1);
      core = core.substring(0, dash);
    }
    if (!validNumericVersion(core, 3, 3)) {
      return false;
    }
    if (pre != null && !validIdentifiers(pre, true)) {
      return false;
    }
    return build == null || validIdentifiers(build, false);
  }

  /** {@code umsValidNumericVersion}: {@code min..max} dot-separated numbers, none with a leading zero. */
  public static boolean validNumericVersion(@NotNull String value, int minimum, int maximum) {
    if (value.isEmpty()) {
      return false;
    }
    String[] parts = value.split("\\.", -1);
    for (String part : parts) {
      if (part.isEmpty() || (part.length() > 1 && part.charAt(0) == '0')) {
        return false;
      }
      for (int i = 0; i < part.length(); i++) {
        if (!isDigit(part.charAt(i))) {
          return false;
        }
      }
    }
    return parts.length >= minimum && parts.length <= maximum;
  }

  private static boolean validIdentifiers(String value, boolean rejectLeadingZero) {
    if (value.isEmpty()) {
      return false;
    }
    for (String part : value.split("\\.", -1)) {
      if (part.isEmpty()) {
        return false;
      }
      boolean numeric = true;
      for (int i = 0; i < part.length(); i++) {
        char c = part.charAt(i);
        if (!isAlnum(c) && c != '-') {
          return false;
        }
        if (!isDigit(c)) {
          numeric = false;
        }
      }
      if (rejectLeadingZero && numeric && part.length() > 1 && part.charAt(0) == '0') {
        return false;
      }
    }
    return true;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  /**
   * {@code umsIsAlnum}: letters, digits and {@code _}. A name may start with {@code _}; the
   * compiler's own message says "a letter or a digit", which understates the rule.
   */
  private static boolean isAlnum(char c) {
    return isDigit(c) || c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  /** The text of a string literal token without its quotes and escapes; other tokens unchanged. */
  static @NotNull String unquote(@NotNull IElementType type, @NotNull String raw) {
    if (!UmsTypes.STRING.equals(type) || raw.isEmpty() || raw.charAt(0) != '"') {
      return raw;
    }
    StringBuilder text = new StringBuilder();
    int end = raw.length();
    if (end > 1 && raw.charAt(end - 1) == '"' && !endsInEscapedQuote(raw)) {
      end--;
    }
    for (int i = 1; i < end; i++) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < end) {
        char escaped = raw.charAt(++i);
        text.append(switch (escaped) {
          case 'n' -> '\n';
          case 'r' -> '\r';
          case 't' -> '\t';
          default -> escaped;
        });
      } else {
        text.append(c);
      }
    }
    return text.toString();
  }

  private static boolean endsInEscapedQuote(String raw) {
    int slashes = 0;
    for (int i = raw.length() - 2; i >= 0 && raw.charAt(i) == '\\'; i--) {
      slashes++;
    }
    return slashes % 2 == 1;
  }
}
