package io.prismio.ums;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Completion driven by position.
 *
 * <p>What may be written in a manifest depends entirely on the enclosing block,
 * so the suggestion list comes from {@link UmsContext#completionsFor} — the same
 * table the annotator validates against. One table means the editor cannot
 * suggest something it would then underline.
 */
public final class UmsCompletionContributor extends CompletionContributor {

  /** Names that take a block body, so accepting one should open it. */
  private static final List<String> OPENS_A_BLOCK =
      List.of("toolchain", "project", "targets", "dependencies", "commands", "profiles",
          "link", "native", "debug", "release");

  /** Names that take a parenthesised argument before their body, if any. */
  private static final List<String> TAKES_ARGUMENTS = List.of(
      "executable", "library", "test", "command",
      "search", "file", "framework", "responseFile", "script", "arg",
      "source", "include", "define", "flag",
      "implementation", "api", "testImplementation",
      "build", "run", "shell");

  public UmsCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
        new CompletionProvider<>() {
          @Override
          protected void addCompletions(@NotNull CompletionParameters parameters,
              @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            PsiFile file = parameters.getOriginalFile();
            if (!(file instanceof UmsFile)) {
              return;
            }

            List<String> path =
                UmsContext.pathAt(file, parameters.getOffset());
            Map<String, String> available = UmsContext.completionsFor(path);
            if (available == null) {
              return;
            }

            for (Map.Entry<String, String> entry : available.entrySet()) {
              result.addElement(element(entry.getKey(), entry.getValue()));
            }

            // `args` is only meaningful inside a command's step list.
            if (path.size() == 2 && "commands".equals(path.get(0))) {
              result.addElement(LookupElementBuilder.create(UmsWords.ARGS_MARKER)
                  .withTypeText("forwarded arguments")
                  .withTailText("  everything typed after the command name", true)
                  .bold());
            }
          }
        });
  }

  private static LookupElement element(String name, String description) {
    LookupElementBuilder builder = LookupElementBuilder.create(name)
        .withTailText("  " + description, true)
        .bold();

    if (TAKES_ARGUMENTS.contains(name)) {
      return builder.withTypeText("declaration").withInsertHandler(new ArgumentsInsertHandler());
    }
    if (OPENS_A_BLOCK.contains(name)) {
      return builder.withTypeText("block").withInsertHandler(new BlockInsertHandler());
    }
    return builder.withTypeText("key").withInsertHandler(new AssignmentInsertHandler());
  }

  /** `name { <caret> }` — the block forms take no arguments. */
  private static class BlockInsertHandler implements InsertHandler<LookupElement> {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      context.getDocument().insertString(context.getTailOffset(), " {\n\n}");
      context.getEditor().getCaretModel().moveToOffset(context.getTailOffset() - 2);
    }
  }

  /** `name("<caret>")` — every one of these takes at least one string. */
  private static class ArgumentsInsertHandler implements InsertHandler<LookupElement> {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      context.getDocument().insertString(context.getTailOffset(), "(\"\")");
      context.getEditor().getCaretModel().moveToOffset(context.getTailOffset() - 2);
    }
  }

  /** `key = <caret>` — assignments are the leaves of a manifest. */
  private static class AssignmentInsertHandler implements InsertHandler<LookupElement> {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      context.getDocument().insertString(context.getTailOffset(), " = ");
      context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
    }
  }
}
