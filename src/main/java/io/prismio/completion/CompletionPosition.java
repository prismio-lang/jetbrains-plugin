package io.prismio.completion;

import com.intellij.psi.tree.IElementType;
import io.prismio.psi.PrismioTypes;
import io.prismio.symbols.Tok;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where the caret is, grammatically, for the purpose of deciding what to offer.
 *
 * <p>Read from tokens, and from line breaks in particular. Prismio has no
 * {@code ;}: a statement ends where its line does. Deciding from the previous
 * token alone — as this plugin once did — puts the start of every line after
 * {@code import std.io} or {@code let x = 1} "in an expression", so neither
 * {@code import} nor {@code return} was ever offered there.
 */
record CompletionPosition(
    @NotNull Kind kind,
    int prefixStart,
    int braceDepth,
    /** For {@link Kind#IMPORT}: the dotted path before the last {@code .}, or null right after {@code import}. */
    @Nullable String importQualifier,
    /** For {@link Kind#IMPORT}: inside {@code import { ... }}, the directory after {@code from} (default {@code std}). */
    @Nullable String importGroupDirectory,
    /** For {@link Kind#IMPORT}: right after {@code from}. */
    boolean importDirectory,
    /** For {@link Kind#STATEMENT}: the line starts after a {@code }}, so {@code else} fits. */
    boolean afterBlock) {

  enum Kind {
    /** Inside a comment or string, or where a new name is being written: offer nothing. */
    NONE,
    TOP_LEVEL,
    STATEMENT,
    EXPRESSION,
    TYPE,
    MEMBER,
    IMPORT,
    /** After a closed {@code import {...}} or {@code import *}: only {@code from} fits. */
    IMPORT_FROM
  }

  static @NotNull CompletionPosition at(@NotNull CharSequence text, int offset) {
    List<Tok> all = Tok.lex(text);
    for (Tok tok : all) {
      if (tok.start() >= offset) {
        break;
      }
      if (insideOf(tok, offset)) {
        return simple(Kind.NONE, offset, 0);
      }
    }

    List<Tok> code = Tok.code(all);
    int p = -1;
    for (int k = 0; k < code.size() && code.get(k).start() < offset; k++) {
      p = k;
    }
    int prefixStart = offset;
    boolean firstOnLine;
    int prev = p;
    if (p >= 0 && code.get(p).end() == offset && isWord(code.get(p))) {
      prefixStart = code.get(p).start();
      firstOnLine = code.get(p).newlineBefore();
      prev = p - 1;
    } else {
      firstOnLine = p < 0 || containsNewline(text, code.get(p).end(), offset);
    }
    int depth = 0;
    for (int k = 0; k <= prev; k++) {
      if (code.get(k).type() == PrismioTypes.LBRACE) {
        depth++;
      } else if (code.get(k).type() == PrismioTypes.RBRACE && depth > 0) {
        depth--;
      }
    }
    if (prev < 0) {
      return simple(Kind.TOP_LEVEL, prefixStart, 0);
    }
    Tok before = code.get(prev);

    if (!firstOnLine) {
      int lineStart = prev;
      while (lineStart > 0 && !code.get(lineStart).newlineBefore()) {
        lineStart--;
      }
      if (code.get(lineStart).is("import")) {
        return importPosition(code, lineStart, prev, prefixStart, depth, text, offset);
      }
      if (isFromImportLine(code, lineStart, prev)) {
        return fromImportPosition(code, lineStart, prev, prefixStart, depth);
      }
    }

    if (before.type() == PrismioTypes.DOT) {
      return simple(Kind.MEMBER, prefixStart, depth);
    }
    if (!firstOnLine) {
      switch (before.text()) {
        case "let", "fn", "struct", "enum", "trait", "for", "prop" -> {
          return simple(Kind.NONE, prefixStart, depth);
        }
        case "mut" -> {
          return simple(prev > 0 && code.get(prev - 1).is("let") ? Kind.NONE : Kind.EXPRESSION,
              prefixStart, depth);
        }
        case "impl", "as", "dyn" -> {
          return simple(Kind.TYPE, prefixStart, depth);
        }
        default -> {
        }
      }
      if (before.type() == PrismioTypes.ARROW) {
        return simple(Kind.TYPE, prefixStart, depth);
      }
      if (before.type() == PrismioTypes.COLON) {
        return simple(colonIntroducesType(code, prev) ? Kind.TYPE : Kind.EXPRESSION, prefixStart, depth);
      }
      if (before.is("<") && prev > 0 && startsUpper(code.get(prev - 1))) {
        return simple(Kind.TYPE, prefixStart, depth);
      }
    }

    boolean statementStart = firstOnLine && !continues(before)
        || before.type() == PrismioTypes.LBRACE || before.type() == PrismioTypes.RBRACE;
    if (statementStart) {
      Kind kind = depth == 0 ? Kind.TOP_LEVEL : Kind.STATEMENT;
      return new CompletionPosition(kind, prefixStart, depth, null, null, false,
          before.type() == PrismioTypes.RBRACE);
    }
    return simple(Kind.EXPRESSION, prefixStart, depth);
  }

  private static CompletionPosition simple(Kind kind, int prefixStart, int depth) {
    return new CompletionPosition(kind, prefixStart, depth, null, null, false, false);
  }

  /**
   * {@code import std.<caret>}, {@code import {io, <caret>} from std},
   * {@code import * from <caret>}.
   */
  private static CompletionPosition importPosition(List<Tok> code, int importIndex, int prev,
      int prefixStart, int depth, CharSequence text, int offset) {
    Tok before = code.get(prev);
    boolean inGroup = false;
    boolean sawFrom = false;
    for (int k = importIndex + 1; k <= prev; k++) {
      Tok tok = code.get(k);
      if (tok.type() == PrismioTypes.LBRACE) {
        inGroup = true;
      } else if (tok.type() == PrismioTypes.RBRACE) {
        inGroup = false;
      } else if (tok.is("from")) {
        sawFrom = true;
      }
    }
    if (before.is("from")) {
      return new CompletionPosition(Kind.IMPORT, prefixStart, depth, null, null, true, false);
    }
    if (inGroup) {
      // The directory is written after the group, so it is ahead of the caret.
      String directory = "std";
      for (int k = prev + 1; k < code.size(); k++) {
        Tok tok = code.get(k);
        if (tok.start() < offset) {
          continue;
        }
        if (tok.newlineBefore()) {
          break;
        }
        if (tok.is("from") && k + 1 < code.size() && !code.get(k + 1).newlineBefore()) {
          directory = dottedFrom(code, k + 1);
          break;
        }
      }
      return new CompletionPosition(Kind.IMPORT, prefixStart, depth, null, directory, false, false);
    }
    if (before.type() == PrismioTypes.DOT) {
      StringBuilder qualifier = new StringBuilder();
      int k = prev - 1;
      while (k > importIndex) {
        Tok tok = code.get(k);
        if (tok.type() == PrismioTypes.DOT) {
          qualifier.insert(0, '.');
        } else if (isWord(tok)) {
          qualifier.insert(0, tok.text());
        } else {
          break;
        }
        k--;
      }
      String dir = sawFrom ? null : qualifier.toString();
      return new CompletionPosition(Kind.IMPORT, prefixStart, depth, dir, null, sawFrom, false);
    }
    if (before.is("import")) {
      return new CompletionPosition(Kind.IMPORT, prefixStart, depth, null, null, false, false);
    }
    if (!sawFrom && (before.type() == PrismioTypes.RBRACE || before.is("*"))) {
      return simple(Kind.IMPORT_FROM, prefixStart, depth);
    }
    // After a complete path: `as`, or nothing.
    return simple(Kind.NONE, prefixStart, depth);
  }

  /** A line that starts {@code from <directory>}, where nothing else can: the directory-first import. */
  private static boolean isFromImportLine(List<Tok> code, int lineStart, int prev) {
    if (!code.get(lineStart).is("from")) {
      return false;
    }
    // `from <caret>`: nothing has followed it yet, and nothing else starts so.
    if (lineStart == prev) {
      return true;
    }
    if (lineStart + 1 >= code.size()) {
      return false;
    }
    Tok next = code.get(lineStart + 1);
    return !next.newlineBefore() && isWord(next);
  }

  /**
   * {@code from <caret>}, and {@code from std import {io, <caret>}}: the directory
   * after {@code from}, then the modules of it between the braces.
   */
  private static CompletionPosition fromImportPosition(List<Tok> code, int fromIndex, int prev,
      int prefixStart, int depth) {
    Tok before = code.get(prev);
    if (before.is("from")) {
      return new CompletionPosition(Kind.IMPORT, prefixStart, depth, null, null, true, false);
    }
    boolean inGroup = false;
    for (int k = fromIndex + 1; k <= prev; k++) {
      if (code.get(k).type() == PrismioTypes.LBRACE) {
        inGroup = true;
      } else if (code.get(k).type() == PrismioTypes.RBRACE) {
        inGroup = false;
      }
    }
    if (inGroup) {
      return new CompletionPosition(Kind.IMPORT, prefixStart, depth, null,
          dottedFrom(code, fromIndex + 1), false, false);
    }
    return simple(Kind.NONE, prefixStart, depth);
  }

  private static String dottedFrom(List<Tok> code, int k) {
    StringBuilder out = new StringBuilder();
    while (k < code.size()) {
      Tok tok = code.get(k);
      if (tok.type() == PrismioTypes.DOT) {
        out.append('.');
      } else if (isWord(tok) && (out.length() == 0 || out.charAt(out.length() - 1) == '.')) {
        out.append(tok.text());
      } else {
        break;
      }
      k++;
    }
    return out.length() == 0 ? "std" : out.toString();
  }

  /**
   * A {@code :} starts a type after a {@code let} name, a parameter, or a field in
   * a {@code struct} declaration; in a struct or map literal it starts a value.
   */
  private static boolean colonIntroducesType(List<Tok> code, int colon) {
    if (colon >= 2) {
      Tok twoBack = code.get(colon - 2);
      if (twoBack.is("let") || twoBack.is("mut")) {
        return true;
      }
    }
    int level = 0;
    for (int k = colon - 1; k >= 0; k--) {
      IElementType type = code.get(k).type();
      if (type == PrismioTypes.RPAREN || type == PrismioTypes.RBRACE || type == PrismioTypes.RBRACKET) {
        level++;
      } else if (type == PrismioTypes.LPAREN || type == PrismioTypes.LBRACKET) {
        if (level-- == 0) {
          return type == PrismioTypes.LPAREN;
        }
      } else if (type == PrismioTypes.LBRACE) {
        if (level-- == 0) {
          // `struct Name {` or `struct Name<T> {`
          for (int m = k - 1; m >= 0 && m >= k - 12; m--) {
            Tok tok = code.get(m);
            if (tok.is("struct")) {
              return true;
            }
            if (tok.type() == PrismioTypes.LBRACE || tok.type() == PrismioTypes.RBRACE) {
              break;
            }
          }
          return false;
        }
      }
    }
    return true;
  }

  /** A token after which the next line carries on the same expression. */
  private static boolean continues(Tok before) {
    IElementType type = before.type();
    return type == PrismioTypes.COMMA || type == PrismioTypes.LPAREN || type == PrismioTypes.LBRACKET
        || type == PrismioTypes.ASSIGNMENT_OP || type == PrismioTypes.ARITHMETIC_OP
        || type == PrismioTypes.RELATIONAL_OP || type == PrismioTypes.LOGICAL_OP
        || type == PrismioTypes.ARROW || type == PrismioTypes.FAT_ARROW || type == PrismioTypes.DOT
        || before.is("and") || before.is("or") || before.is("return") || before.is("in");
  }

  private static boolean insideOf(Tok tok, int offset) {
    IElementType type = tok.type();
    if (type == PrismioTypes.LINE_COMMENT || type == PrismioTypes.DOC_COMMENT) {
      return offset > tok.start() && offset <= tok.end();
    }
    if (type == PrismioTypes.BLOCK_COMMENT) {
      return offset > tok.start() && (offset < tok.end() || !tok.text().endsWith("*/"));
    }
    if (type == PrismioTypes.STRING_LITERAL || type == PrismioTypes.CHARACTER_LITERAL) {
      char quote = tok.text().charAt(0);
      boolean closed = tok.text().length() > 1 && tok.text().charAt(tok.text().length() - 1) == quote;
      return offset > tok.start() && (offset < tok.end() || !closed);
    }
    return false;
  }

  private static boolean isWord(Tok tok) {
    return tok.isName() || tok.type() == PrismioTypes.KEYWORD || tok.type() == PrismioTypes.BOOLEAN;
  }

  private static boolean startsUpper(Tok tok) {
    return tok.isName() && !tok.text().isEmpty() && Character.isUpperCase(tok.text().charAt(0));
  }

  private static boolean containsNewline(CharSequence text, int from, int to) {
    for (int k = from; k < to && k < text.length(); k++) {
      if (text.charAt(k) == '\n') {
        return true;
      }
    }
    return false;
  }
}
