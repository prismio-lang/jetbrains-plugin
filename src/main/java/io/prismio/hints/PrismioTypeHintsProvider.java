package io.prismio.hints;

import com.intellij.codeInsight.hints.declarative.HintFormat;
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector;
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider;
import com.intellij.codeInsight.hints.declarative.InlayTreeSink;
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition;
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.prismio.psi.PrismioFile;
import io.prismio.symbols.ProjectSymbols;
import io.prismio.symbols.TypeInference;
import io.prismio.symbols.TypeRef;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code let found = s.find("x")} shows {@code : Int?} after the name —
 * the type a binding has without writing one, as the Rust and Kotlin plugins
 * show it.
 *
 * <p>Only what {@link TypeInference} can establish from declarations is shown. A
 * literal's binding gets no hint (the literal already says it), nor does one
 * whose type still has an unresolved parameter in it.
 */
public final class PrismioTypeHintsProvider implements InlayHintsProvider {

  @Override
  public @Nullable InlayHintsCollector createCollector(@NotNull PsiFile file, @NotNull Editor editor) {
    if (!(file instanceof PrismioFile)) {
      return null;
    }
    Map<Integer, TypeRef> types = TypeInference.letTypes(file.getViewProvider().getContents(),
        ProjectSymbols.scope(file), ProjectSymbols.summary(file).imports());
    if (types.isEmpty()) {
      return null;
    }
    return new SharedBypassCollector() {
      @Override
      public void collectFromElement(@NotNull PsiElement element, @NotNull InlayTreeSink sink) {
        if (element.getFirstChild() != null) {
          return;
        }
        TypeRef type = types.get(element.getTextRange().getEndOffset());
        if (type == null) {
          return;
        }
        sink.addPresentation(new InlineInlayPosition(element.getTextRange().getEndOffset(), true, 0),
            null, null, HintFormat.Companion.getDefault(), builder -> {
              builder.text(": " + type, null);
              return kotlin.Unit.INSTANCE;
            });
      }
    };
  }
}
