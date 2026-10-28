package io.prismio.ums;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Semantic colours for a manifest.
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
 * <p>A name the manifest system does not recognise is not judged here either: the compiler says so
 * ({@code UMS2004} and its neighbours) through the language server, and a table kept in this plugin is
 * wrong exactly when the toolchain is newer than the plugin. It keeps the ordinary identifier colour.
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
  }

  private static TextAttributesKey colourFor(UmsContext context) {
    // Only recognised names are coloured. An unknown one keeps the ordinary
    // identifier colour, so the server's error is what draws the eye rather than a
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
