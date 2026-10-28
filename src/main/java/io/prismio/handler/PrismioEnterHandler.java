package io.prismio.handler;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import io.prismio.lexer.PrismioLexer;
import io.prismio.psi.PrismioFile;
import io.prismio.psi.PrismioTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Enter handler for Prismio to provide proper indentation when pressing Enter.
 * Handles indentation inside braces with proper closing brace alignment.
 */
public class PrismioEnterHandler extends EnterHandlerDelegateAdapter {
  private static final String COMMENT_CONTENT_INDENT = "   ";

  @Override
  public Result postProcessEnter(
      @NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext) {
    if (!(file instanceof PrismioFile)) {
      return Result.Continue;
    }

    Document document = editor.getDocument();
    int caretOffset = editor.getCaretModel().getOffset();
    CharSequence text = document.getCharsSequence();

    // Get current line number (where caret is after Enter)
    int currentLineNumber = document.getLineNumber(caretOffset);
    if (currentLineNumber == 0) {
      return Result.Continue;
    }

    // Get previous line info
    int prevLineNumber = currentLineNumber - 1;
    int prevLineStart = document.getLineStartOffset(prevLineNumber);
    int prevLineEnd = document.getLineEndOffset(prevLineNumber);
    String prevLine = text.subSequence(prevLineStart, prevLineEnd).toString();
    String trimmedPrevLine = prevLine.trim();

    // Calculate previous line's indentation
    String prevIndent = getIndentation(prevLine);

    // Get current line info
    int currentLineStart = document.getLineStartOffset(currentLineNumber);
    int currentLineEnd = document.getLineEndOffset(currentLineNumber);
    String currentLine = currentLineEnd > currentLineStart
        ? text.subSequence(currentLineStart, currentLineEnd).toString()
        : "";
    String trimmedCurrentLine = currentLine.trim();

    BlockCommentContext blockComment = findOpenBlockComment(document, caretOffset);
    if (blockComment != null) {
      String contentIndent = blockComment.baseIndent() + COMMENT_CONTENT_INDENT;
      int closingDelimiter = currentLine.indexOf("*/");

      if (closingDelimiter >= 0 && currentLine.substring(0, closingDelimiter).isBlank()) {
        String replacement = contentIndent + "\n" + blockComment.baseIndent() + "*/";
        document.replaceString(
            currentLineStart, currentLineStart + closingDelimiter + 2, replacement);
      } else {
        int contentStart = currentLineStart + leadingWhitespaceLength(currentLine);
        document.replaceString(currentLineStart, contentStart, contentIndent);
      }

      editor.getCaretModel().moveToOffset(currentLineStart + contentIndent.length());
      return Result.Stop;
    }

    // Check if previous line ends with { (need to increase indent)
    if (trimmedPrevLine.endsWith("{")) {
      String newIndent = prevIndent + "    "; // 4 spaces

      // Check if current line starts with } (we pressed Enter between { and })
      if (trimmedCurrentLine.startsWith("}")) {
        // Insert the indented cursor line, then newline with closing brace at correct
        // indent
        // Current state: cursor is at start of line with }
        // We need: cursor on indented line, } on next line with original indent

        // Find where } starts on current line
        int bracePos = currentLine.indexOf('}');
        if (bracePos >= 0) {
          // Replace from line start to after brace with: indent + newline + prevIndent +
          // }
          String replacement = newIndent + "\n" + prevIndent + "}";
          int replaceEnd = currentLineStart + bracePos + 1;

          document.replaceString(currentLineStart, replaceEnd, replacement);
          // Move caret to end of indented line (before the newline)
          editor.getCaretModel().moveToOffset(currentLineStart + newIndent.length());

          return Result.Stop;
        }
      } else {
        // The line already has the indentation the platform gave it, so the new level
        // replaces that and is not added to it. Inserting here put the code twice as far
        // in as it belongs when Enter was pressed after `{` with code still after the caret.
        setIndentation(document, currentLineStart, currentLine, newIndent);
        int trimmed = trimTrailingWhitespace(document, prevLineStart, prevLineEnd);
        editor.getCaretModel().moveToOffset(currentLineStart - trimmed + newIndent.length());
        return Result.Stop;
      }
    }

    // Check if current line starts with } (dedent for closing brace)
    if (trimmedCurrentLine.startsWith("}")) {
      // Closing brace should have one less indent level than previous line content
      String dedentedIndent = dedent(prevIndent);

      // Replace current line's indentation
      int contentStart = currentLineStart;
      for (int i = currentLineStart; i < currentLineEnd && i < text.length(); i++) {
        char c = text.charAt(i);
        if (c != ' ' && c != '\t') {
          contentStart = i;
          break;
        }
      }

      if (contentStart > currentLineStart) {
        document.replaceString(currentLineStart, contentStart, dedentedIndent);
      } else {
        document.insertString(currentLineStart, dedentedIndent);
      }
      editor.getCaretModel().moveToOffset(currentLineStart + dedentedIndent.length());
      return Result.Stop;
    }

    // Maintain current indentation for other cases
    if (!prevIndent.isEmpty()) {
      setIndentation(document, currentLineStart, currentLine, prevIndent);
      editor.getCaretModel().moveToOffset(currentLineStart + prevIndent.length());
      return Result.Default;
    }

    return Result.Continue;
  }

  private static BlockCommentContext findOpenBlockComment(
      @NotNull Document document, int caretOffset) {
    CharSequence text = document.getCharsSequence();
    Lexer lexer = new PrismioLexer();
    lexer.start(text);
    while (lexer.getTokenType() != null) {
      if ((lexer.getTokenType() == PrismioTypes.BLOCK_COMMENT
              || lexer.getTokenType() == PrismioTypes.DOC_COMMENT)
          && lexer.getTokenStart() < caretOffset && lexer.getTokenEnd() >= caretOffset) {
        int openLine = document.getLineNumber(lexer.getTokenStart());
        int openLineStart = document.getLineStartOffset(openLine);
        String openLineText =
            text.subSequence(openLineStart, document.getLineEndOffset(openLine)).toString();
        return new BlockCommentContext(getIndentation(openLineText));
      }
      lexer.advance();
    }
    return null;
  }

  /** Makes `indent` the whole of the line's leading whitespace, whatever is there already. */
  private static void setIndentation(
      @NotNull Document document, int lineStart, @NotNull String line, @NotNull String indent) {
    int existing = leadingWhitespaceLength(line);
    if (line.substring(0, existing).equals(indent)) {
      return;
    }
    document.replaceString(lineStart, lineStart + existing, indent);
  }

  /** Removes the spaces a line ends with, and says how many there were: `{ ` + Enter leaves `{`. */
  private static int trimTrailingWhitespace(@NotNull Document document, int lineStart, int lineEnd) {
    CharSequence text = document.getCharsSequence();
    int end = lineEnd;
    while (end > lineStart && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) {
      end--;
    }
    if (end < lineEnd) {
      document.deleteString(end, lineEnd);
    }
    return lineEnd - end;
  }

  private static int leadingWhitespaceLength(@NotNull String line) {
    int index = 0;
    while (index < line.length() && (line.charAt(index) == ' ' || line.charAt(index) == '\t')) {
      index++;
    }
    return index;
  }

  private static String getIndentation(String line) {
    StringBuilder indent = new StringBuilder();
    for (char c : line.toCharArray()) {
      if (c == ' ' || c == '\t') {
        indent.append(c);
      } else {
        break;
      }
    }
    return indent.toString();
  }

  private static String dedent(String indent) {
    // Remove 4 spaces or 1 tab from indent
    if (indent.endsWith("    ")) {
      return indent.substring(0, indent.length() - 4);
    } else if (indent.endsWith("\t")) {
      return indent.substring(0, indent.length() - 1);
    }
    return indent;
  }

  private record BlockCommentContext(@NotNull String baseIndent) {}
}
