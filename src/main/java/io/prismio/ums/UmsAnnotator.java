package io.prismio.ums;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Semantic colours for a manifest, and a weak warning for a name it does not recognise.
 *
 * <p>The lexer emits a plain identifier for every name in a manifest, because
 * UMS has no keywords. This decides which of them the manifest system actually
 * recognises *at that position* — `library` is a target kind inside `targets`
 * and a linker input inside `link`, and only one of the two is right in a given
 * place.
 *
 * <p>A value the compiler would refuse -- {@code runtime = "yes"}, a host outside
 * {@code .prismio/}, a {@code .cpp} native source -- is not judged here: the language server
 * publishes the compiler's own {@code UMS####} diagnostics for the manifest, which cannot drift from
 * what the build accepts (this class once said a {@code .cpp} source was an error after the build
 * began to take them).
 *
 * <p>An unrecognised name is a weak warning rather than an error. UMS parses
 * blocks before the semantic model learns them, so a manifest may legitimately
 * hold a block newer than this plugin; saying "unknown here" while offering the
 * list is useful, and refusing the file would be wrong.
 */
public final class UmsAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    UmsContext context = UmsContext.of(element);
    if (context == null) {
      return;
    }

    TextAttributesKey colour = colourFor(context);
    if (colour != null) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(colour)
          .create();
    }

    if (!context.isRecognised()) {
      Map<String, String> expected = context.expectedHere();
      String available = expected == null ? "" : String.join(", ", expected.keySet());
      String where = context.path.isEmpty()
          ? "at the top level of a manifest"
          : "inside `" + String.join(" > ", context.path) + "`";
      holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
              "`" + context.name + "` is not a name UMS recognises " + where)
          .range(element)
          .tooltip("`" + context.name + "` is not recognised " + where
              + (available.isEmpty() ? "" : ".<br/>Available: " + available))
          .create();
    }
  }

  private static TextAttributesKey colourFor(UmsContext context) {
    // Only recognised names are coloured. An unknown one keeps the ordinary
    // identifier colour, so the warning is what draws the eye rather than a
    // colour implying the plugin understood it.
    if (!context.isRecognised()) {
      return null;
    }
    return switch (context.role) {
      case BLOCK -> UmsSyntaxHighlighter.BLOCK;
      case KEY -> UmsSyntaxHighlighter.KEY;
      case CALL -> UmsSyntaxHighlighter.CALL;
      case ARGS -> UmsSyntaxHighlighter.BOOLEAN;
      case VALUE -> null;
    };
  }
}
