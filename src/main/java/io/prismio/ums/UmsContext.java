package io.prismio.ums;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Where an identifier sits in a manifest, and therefore what it means.
 *
 * <p>The PSI is a flat token list, so "which block am I in" is answered by
 * walking from the start of the file and tracking brace depth. That is cheap —
 * a manifest is tens of lines — and it is the only way to tell {@code library}
 * the target kind from {@code library} the linker input, which are the same word
 * one nesting level apart.
 */
public final class UmsContext {

  /** What an identifier is being used as. */
  public enum Role {
    /** Opens a block: `project {`, `executable("x") {`. */
    BLOCK,
    /** Left of an `=`. */
    KEY,
    /** A call that opens no block: `library("m")`, `build("app")`. */
    CALL,
    /** The `args` marker inside a command step. */
    ARGS,
    /** The `const` or `enum` that starts a declaration: `const NAME = ...`, `enum Name { ... }`. */
    KEYWORD,
    /** The name a `const` or `enum` declares. */
    DECLARATION,
    /** A use of a declared name: `VERSION`, or `Channel.BETA` for a declared enum. */
    REFERENCE,
    /** An identifier in value position, or one nothing recognises. */
    VALUE
  }

  /** A literal or bare word written as a value or argument, with where it sits in the file. */
  public record Value(IElementType type, String text, int start, int end) {}

  /** The enclosing block names, outermost first. Empty at the top level. */
  public final List<String> path;
  public final Role role;
  public final String name;
  /** What a {@link Role#KEY} is assigned; null when nothing follows the {@code =}. */
  public final Value value;
  /** The arguments of a {@link Role#BLOCK} or {@link Role#CALL} written with parentheses. */
  public final List<Value> arguments;

  private UmsContext(List<String> path, Role role, String name, Value value, List<Value> arguments) {
    this.path = List.copyOf(path);
    this.role = role;
    this.name = name;
    this.value = value;
    this.arguments = List.copyOf(arguments);
  }

  /** True when the manifest system gives this identifier, here, a meaning. */
  public boolean isRecognised() {
    Map<String, String> expected = expectedHere();
    if (expected == null) {
      // Inside a block this plugin does not model. Say nothing rather than
      // marking every identifier unknown: `ums/ARCHITECTURE.md` is explicit that
      // new blocks are parsed before the model learns them, so a manifest may
      // legitimately contain a block newer than this plugin.
      return true;
    }
    return role == Role.ARGS || role == Role.VALUE || expected.containsKey(name);
  }

  /** The identifiers valid at this position, or null when the position is unmodelled. */
  public Map<String, String> expectedHere() {
    if (role == Role.VALUE || role == Role.ARGS || role == Role.KEYWORD
        || role == Role.DECLARATION || role == Role.REFERENCE) {
      return null;
    }
    return completionsFor(path);
  }

  /** What may be written inside the block named by {@code path}. */
  public static Map<String, String> completionsFor(List<String> path) {
    if (path.isEmpty()) {
      return UmsWords.TOP_LEVEL_BLOCKS;
    }
    String outer = path.get(0);
    switch (outer) {
      case "project":
        return path.size() == 1 ? UmsWords.PROJECT_KEYS : null;
      case "toolchain":
        return path.size() == 1 ? UmsWords.TOOLCHAIN_KEYS : null;
      case "dependencies":
        return path.size() == 1 ? UmsWords.DEPENDENCY_SCOPES : null;
      case "profiles":
        if (path.size() == 1) {
          return UmsWords.PROFILE_NAMES;
        }
        return path.size() == 2 ? UmsWords.PROFILE_KEYS : null;
      case "targets":
        if (path.size() == 1) {
          return UmsWords.TARGET_KINDS;
        }
        if (path.size() == 2) {
          return UmsWords.TARGET_BODY;
        }
        if (path.size() != 3) {
          return null;
        }
        if ("native".equals(path.get(2))) {
          return UmsWords.NATIVE_KINDS;
        }
        return "link".equals(path.get(2)) ? UmsWords.LINK_KINDS : null;
      case "commands":
        if (path.size() == 1) {
          return UmsWords.COMMAND_KINDS;
        }
        return path.size() == 2 ? UmsWords.COMMAND_BODY : null;
      default:
        return null;
    }
  }

  /**
   * Resolves the context of one identifier token.
   *
   * <p>Returns null when the element is not an identifier, so callers can skip
   * it without a second type test.
   */
  public static UmsContext of(PsiElement element) {
    ASTNode node = element.getNode();
    if (node == null || !UmsTypes.IDENTIFIER.equals(node.getElementType())) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return null;
    }

    List<Token> tokens = tokensOf(file);
    int index = indexOf(tokens, element.getTextRange().getStartOffset());
    return index < 0 ? null : contextAt(tokens, index);
  }

  private static UmsContext contextAt(List<Token> tokens, int index) {
    List<String> path = pathBefore(tokens, index);
    Role role = roleOf(tokens, index, path);
    Value value = role == Role.KEY ? valueAt(tokens, index + 2) : null;
    List<Value> arguments = role == Role.BLOCK || role == Role.CALL
        ? argumentsAfter(tokens, index + 1) : List.of();
    return new UmsContext(path, role, tokens.get(index).text, value, arguments);
  }

  private static Value valueAt(List<Token> tokens, int index) {
    Token token = at(tokens, index);
    if (token == null || !isValueToken(token.type)) {
      return null;
    }
    return valueOf(token);
  }

  /** The top-level arguments of the parenthesised list opening at {@code openIndex}, if there is one. */
  private static List<Value> argumentsAfter(List<Token> tokens, int openIndex) {
    Token open = at(tokens, openIndex);
    if (open == null || !UmsTypes.LEFT_PAREN.equals(open.type)) {
      return List.of();
    }
    int close = matchingParen(tokens, openIndex);
    List<Value> arguments = new ArrayList<>();
    for (int i = openIndex + 1; i < close && i < tokens.size(); i++) {
      if (isValueToken(tokens.get(i).type)) {
        arguments.add(valueOf(tokens.get(i)));
      }
    }
    return arguments;
  }

  private static boolean isValueToken(IElementType type) {
    return UmsTypes.LITERALS.contains(type) || UmsTypes.IDENTIFIER.equals(type);
  }

  /** The text of a string literal token without its quotes and escapes; other tokens unchanged. */
  private static @NotNull String unquote(@NotNull IElementType type, @NotNull String raw) {
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

  private static Value valueOf(Token token) {
    return new Value(token.type, unquote(token.type, token.text),
        token.start, token.start + token.text.length());
  }

  /** The completions valid at an arbitrary offset, used before a name is typed. */
  public static List<String> pathAt(PsiFile file, int offset) {
    List<Token> tokens = tokensOf(file);
    int index = 0;
    while (index < tokens.size() && tokens.get(index).start < offset) {
      index++;
    }
    return pathBefore(tokens, index);
  }

  private static Role roleOf(List<Token> tokens, int index, List<String> path) {
    String text = tokens.get(index).text;
    Token next = at(tokens, index + 1);
    if (path.isEmpty() && startsDeclaration(tokens, index)) {
      return Role.KEYWORD;
    }
    if (path.isEmpty() && startsDeclaration(tokens, index - 1)) {
      return Role.DECLARATION;
    }
    if (next == null) {
      return isDeclared(tokens, text) ? Role.REFERENCE : Role.VALUE;
    }

    if (UmsTypes.EQUAL.equals(next.type)) {
      return Role.KEY;
    }
    if (UmsTypes.LEFT_BRACE.equals(next.type)) {
      return Role.BLOCK;
    }
    if (UmsTypes.LEFT_PAREN.equals(next.type)) {
      // `name(...)` is a block when a `{` follows the closing paren, and a plain
      // call otherwise. Scanning to that paren is what separates
      // `executable("a") { ... }` from `library("m")`.
      int close = matchingParen(tokens, index + 1);
      Token after = at(tokens, close + 1);
      return after != null && UmsTypes.LEFT_BRACE.equals(after.type) ? Role.BLOCK : Role.CALL;
    }
    if (UmsWords.ARGS_MARKER.equals(text) && !path.isEmpty()) {
      return Role.ARGS;
    }
    return isDeclared(tokens, text) ? Role.REFERENCE : Role.VALUE;
  }

  /**
   * Whether the identifier at {@code index} is the {@code const} or {@code enum} of a declaration:
   * {@code const NAME =} or {@code enum Name} and an opening brace. The shape is checked, not just the word, because
   * {@code const} alone is an ordinary identifier to the lexer.
   */
  private static boolean startsDeclaration(List<Token> tokens, int index) {
    Token word = at(tokens, index);
    Token name = at(tokens, index + 1);
    Token after = at(tokens, index + 2);
    if (word == null || name == null || after == null || !UmsTypes.IDENTIFIER.equals(word.type)
        || !UmsTypes.IDENTIFIER.equals(name.type)) {
      return false;
    }
    if (word.text.equals("const")) {
      return UmsTypes.EQUAL.equals(after.type);
    }
    return word.text.equals("enum") && UmsTypes.LEFT_BRACE.equals(after.type);
  }

  /**
   * Whether {@code text} names a constant declared somewhere in the manifest, or is {@code Enum.VARIANT}
   * of a declared enum. Position-free on purpose: a use may come before its declaration.
   */
  private static boolean isDeclared(List<Token> tokens, String text) {
    int dot = text.indexOf('.');
    String wanted = dot < 0 ? text : text.substring(0, dot);
    String keyword = dot < 0 ? "const" : "enum";
    for (int i = 0; i + 1 < tokens.size(); i++) {
      if (tokens.get(i).text.equals(keyword) && startsDeclaration(tokens, i)
          && tokens.get(i + 1).text.equals(wanted)) {
        return true;
      }
    }
    return false;
  }

  /** The block names enclosing the token at {@code index}, outermost first. */
  private static List<String> pathBefore(List<Token> tokens, int index) {
    List<String> stack = new ArrayList<>();
    for (int i = 0; i < index && i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (UmsTypes.LEFT_BRACE.equals(token.type)) {
        stack.add(openerBefore(tokens, i));
      } else if (UmsTypes.RIGHT_BRACE.equals(token.type) && !stack.isEmpty()) {
        stack.remove(stack.size() - 1);
      }
    }
    return stack;
  }

  /** The identifier that opened the block whose `{` sits at {@code braceIndex}. */
  private static String openerBefore(List<Token> tokens, int braceIndex) {
    int i = braceIndex - 1;
    if (i >= 0 && UmsTypes.RIGHT_PAREN.equals(tokens.get(i).type)) {
      int depth = 0;
      while (i >= 0) {
        IElementType type = tokens.get(i).type;
        if (UmsTypes.RIGHT_PAREN.equals(type)) {
          depth++;
        } else if (UmsTypes.LEFT_PAREN.equals(type)) {
          depth--;
          if (depth == 0) {
            i--;
            break;
          }
        }
        i--;
      }
    }
    return i >= 0 && UmsTypes.IDENTIFIER.equals(tokens.get(i).type) ? tokens.get(i).text : "";
  }

  private static int matchingParen(List<Token> tokens, int openIndex) {
    int depth = 0;
    for (int i = openIndex; i < tokens.size(); i++) {
      IElementType type = tokens.get(i).type;
      if (UmsTypes.LEFT_PAREN.equals(type)) {
        depth++;
      } else if (UmsTypes.RIGHT_PAREN.equals(type)) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return tokens.size();
  }

  private static Token at(List<Token> tokens, int index) {
    return index >= 0 && index < tokens.size() ? tokens.get(index) : null;
  }

  private static int indexOf(List<Token> tokens, int startOffset) {
    for (int i = 0; i < tokens.size(); i++) {
      if (tokens.get(i).start == startOffset) {
        return i;
      }
    }
    return -1;
  }

  /** Significant tokens only: whitespace and comments carry no structure. */
  private static List<Token> tokensOf(PsiFile file) {
    List<Token> tokens = new ArrayList<>();
    for (ASTNode node : file.getNode().getChildren(null)) {
      IElementType type = node.getElementType();
      if (UmsTypes.COMMENTS.contains(type) || com.intellij.psi.TokenType.WHITE_SPACE.equals(type)) {
        continue;
      }
      tokens.add(new Token(type, node.getText(), node.getStartOffset()));
    }
    return tokens;
  }

  private record Token(IElementType type, String text, int start) {}
}
