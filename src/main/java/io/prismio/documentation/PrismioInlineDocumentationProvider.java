package io.prismio.documentation;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.InlineDocumentation;
import com.intellij.platform.backend.documentation.InlineDocumentationProvider;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.XmlStringUtil;
import io.prismio.lexer.PrismioLexer;
import io.prismio.psi.PrismioFile;
import io.prismio.psi.PrismioTypes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Supplies in-editor rendered views for Prismio documentation comments. */
public final class PrismioInlineDocumentationProvider implements InlineDocumentationProvider {
  @Override
  public @NotNull Collection<InlineDocumentation> inlineDocumentationItems(@NotNull PsiFile file) {
    if (!(file instanceof PrismioFile)) {
      return List.of();
    }

    List<InlineDocumentation> documentation = new ArrayList<>();
    CharSequence source = file.getViewProvider().getContents();
    Lexer lexer = new PrismioLexer();
    lexer.start(source);

    while (lexer.getTokenType() != null) {
      // The lexer already decided this: `/**` with something in it is a
      // DOC_COMMENT, while `/**/` and `/***/` stay ordinary block comments.
      if (lexer.getTokenType() == PrismioTypes.DOC_COMMENT
          && startsWith(source, lexer.getTokenStart(), "/**")) {
        TextRange range = new TextRange(lexer.getTokenStart(), lexer.getTokenEnd());
        TextRange owner = ownerOf(source, range.getEndOffset());
        String comment =
            source.subSequence(range.getStartOffset(), range.getEndOffset()).toString();
        documentation.add(new PrismioInlineDocumentation(range, owner, render(comment)));
      }
      lexer.advance();
    }
    return documentation;
  }

  @Override
  public @Nullable InlineDocumentation findInlineDocumentation(
      @NotNull PsiFile file, @NotNull TextRange range) {
    for (InlineDocumentation item : inlineDocumentationItems(file)) {
      if (item instanceof PrismioInlineDocumentation prismioDocumentation
          && prismioDocumentation.contains(range)) {
        return item;
      }
    }
    return null;
  }

  /** What the comment documents: the line of the first code after it. */
  private static @Nullable TextRange ownerOf(@NotNull CharSequence source, int commentEnd) {
    int start = commentEnd;
    while (start < source.length() && Character.isWhitespace(source.charAt(start))) {
      start++;
    }
    if (start >= source.length()) {
      return null;
    }
    int end = start;
    while (end < source.length() && source.charAt(end) != '\n') {
      end++;
    }
    return new TextRange(start, end);
  }

  private static @NotNull String render(@NotNull String comment) {
    String body = comment.substring(3, Math.max(3, comment.length() - 2));
    String[] lines = body.split("\\R", -1);
    List<String> renderedLines = new ArrayList<>();
    for (String line : lines) {
      String content = line.strip();
      if (content.startsWith("*")) {
        content = content.substring(1).stripLeading();
      }
      renderedLines.add(XmlStringUtil.escapeString(content));
    }

    while (!renderedLines.isEmpty() && renderedLines.getFirst().isBlank()) {
      renderedLines.removeFirst();
    }
    while (!renderedLines.isEmpty() && renderedLines.getLast().isBlank()) {
      renderedLines.removeLast();
    }
    return "<div class='content'>" + String.join("<br>", renderedLines) + "</div>";
  }

  private static boolean startsWith(
      @NotNull CharSequence text, int offset, @NotNull String prefix) {
    if (offset < 0 || offset + prefix.length() > text.length()) {
      return false;
    }
    for (int index = 0; index < prefix.length(); index++) {
      if (text.charAt(offset + index) != prefix.charAt(index)) {
        return false;
      }
    }
    return true;
  }

  private record PrismioInlineDocumentation(@NotNull TextRange documentationRange,
      @Nullable TextRange owner, @NotNull String renderedText) implements InlineDocumentation {
    @Override
    public @NotNull TextRange getDocumentationRange() {
      return documentationRange;
    }

    @Override
    public @NotNull TextRange getDocumentationOwnerRange() {
      return owner == null ? documentationRange : owner;
    }

    @Override
    public @NotNull String renderText() {
      return renderedText;
    }

    @Override
    public @Nullable DocumentationTarget getOwnerTarget() {
      return null;
    }

    private boolean contains(@NotNull TextRange range) {
      return documentationRange.intersects(range)
          || documentationRange.contains(range.getStartOffset());
    }
  }
}
