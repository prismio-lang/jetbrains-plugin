package io.prismio.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import io.prismio.lang.FreestandingBuiltins;
import org.jetbrains.annotations.NotNull;

/**
 * The language's own words, offered where the grammar accepts them.
 *
 * <p>These are syntax, not library: they come from the parser
 * ({@code src/parse/decl.psm}, {@code src/parse/stmt.psm}), the same place
 * {@code PrismioWords} follows for highlighting.
 */
final class Keywords {

  private Keywords() {}

  static void topLevel(@NotNull CompletionResultSet result) {
    add(result, "import", IMPORT, 90);
    for (String word : new String[] {"fn", "struct", "enum", "trait", "impl", "let", "extern"}) {
      add(result, word, SPACE, 80);
    }
    for (String word : new String[] {"public", "private", "internal"}) {
      add(result, word, SPACE, 75);
    }
    add(result, "async", SPACE, 78);
    add(result, "inline", SPACE, 40);
    add(result, "inline(always)", SPACE, 40);
    for (String word : new String[] {"noinline", "hot", "cold", "noreturn", "const"}) {
      add(result, word, SPACE, 40);
    }
    // Before `fn` and `struct` in a program with no operating system under it.
    for (String word : new String[] {"export", "naked", "repr(C)", "packed"}) {
      add(result, word, SPACE, 40);
    }
    add(result, "section", SECTION_NAME, 40);
    add(result, "align", ALIGNMENT, 40);
  }

  /** {@code __builtin_mem_vstore_u32(address: Usize, value: U32)}, offered by name. */
  static void freestanding(@NotNull CompletionResultSet result) {
    for (FreestandingBuiltins.Entry entry : FreestandingBuiltins.all()) {
      LookupElementBuilder builder = LookupElementBuilder.create(entry.name())
          .withTailText("(" + entry.parameters() + ")", true)
          .withTypeText(entry.result().isEmpty() ? entry.help() : entry.result() + " \u2014 " + entry.help(), true)
          .withInsertHandler(PARENS);
      result.addElement(PrioritizedLookupElement.withPriority(builder, 10));
    }
  }

  /** {@code import {io} from}: the directory list opens straight after it. */
  static void importFrom(@NotNull CompletionResultSet result) {
    add(result, "from", IMPORT, 100);
  }

  static void statement(@NotNull CompletionResultSet result, boolean afterBlock) {
    add(result, "let", SPACE, 45);
    add(result, "let mut", SPACE, 44);
    add(result, "if", CONDITION_BLOCK, 40);
    if (afterBlock) {
      add(result, "else", BLOCK, 95);
    }
    add(result, "while", CONDITION_BLOCK, 40);
    // `for x in xs {` takes no parentheses (src/parse/stmt.psm).
    add(result, "for", SPACE, 40);
    add(result, "loop", BLOCK, 40);
    add(result, "repeat", CONDITION_BLOCK, 40);
    add(result, "match", CONDITION_BLOCK, 40);
    add(result, "return", SPACE, 45);
    add(result, "break", null, 40);
    add(result, "continue", null, 40);
    add(result, "region", SPACE, 30);
    add(result, "scope", BLOCK, 30);
  }

  static void expression(@NotNull CompletionResultSet result) {
    add(result, "if", CONDITION_BLOCK, 30);
    add(result, "match", CONDITION_BLOCK, 30);
    add(result, "none", null, 30);
    add(result, "spawn", SPACE, 30);
    add(result, "await", SPACE, 30);
    add(result, "join", SPACE, 20);
  }

  static void literals(@NotNull CompletionResultSet result) {
    add(result, "true", null, 35);
    add(result, "false", null, 35);
  }

  static void templates(@NotNull CompletionResultSet result) {
    result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create("fn main()")
        .withPresentableText("main")
        .withTailText(" fn main() -> Int { }", true)
        .withTypeText("template")
        .withInsertHandler((context, item) -> {
          String indent = indentAt(context);
          context.getDocument().insertString(context.getTailOffset(),
              " -> Int {\n" + indent + "    \n" + indent + "    return 0\n" + indent + "}");
          context.getEditor().getCaretModel().moveToOffset(
              context.getStartOffset() + "fn main() -> Int {\n".length() + indent.length() + 4);
        }), 70));
  }

  private static void add(@NotNull CompletionResultSet result, @NotNull String word,
      InsertHandler<LookupElement> handler, double priority) {
    LookupElementBuilder builder = LookupElementBuilder.create(word).bold();
    if (handler != null) {
      builder = builder.withInsertHandler(handler);
    }
    result.addElement(PrioritizedLookupElement.withPriority(builder, priority));
  }

  /** {@code section("|")}: the name is a string literal, so the quotes go in. */
  private static final InsertHandler<LookupElement> SECTION_NAME = (context, item) -> {
    int tail = context.getTailOffset();
    context.getDocument().insertString(tail, "(\"\") ");
    context.getEditor().getCaretModel().moveToOffset(tail + 2);
  };

  /** {@code align(|)}: a power of two. */
  private static final InsertHandler<LookupElement> ALIGNMENT = (context, item) -> {
    int tail = context.getTailOffset();
    context.getDocument().insertString(tail, "() ");
    context.getEditor().getCaretModel().moveToOffset(tail + 1);
  };

  /** A call: parentheses with the caret between them. */
  private static final InsertHandler<LookupElement> PARENS = (context, item) -> {
    int tail = context.getTailOffset();
    context.getDocument().insertString(tail, "()");
    context.getEditor().getCaretModel().moveToOffset(tail + 1);
  };

  private static final InsertHandler<LookupElement> SPACE = (context, item) -> {
    insertSpace(context);
  };

  /** {@code import } and straight into the module list, as the function list opens on a name. */
  private static final InsertHandler<LookupElement> IMPORT = (context, item) -> {
    insertSpace(context);
    AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
  };

  /** {@code if (|) {\n}} — Prismio's conditions are parenthesised. */
  private static final InsertHandler<LookupElement> CONDITION_BLOCK = (context, item) -> {
    String indent = indentAt(context);
    int tail = context.getTailOffset();
    context.getDocument().insertString(tail, " () {\n" + indent + "    \n" + indent + "}");
    context.getEditor().getCaretModel().moveToOffset(tail + 2);
  };

  private static final InsertHandler<LookupElement> BLOCK = (context, item) -> {
    String indent = indentAt(context);
    int tail = context.getTailOffset();
    String inner = indent + "    ";
    context.getDocument().insertString(tail, " {\n" + inner + "\n" + indent + "}");
    context.getEditor().getCaretModel().moveToOffset(tail + 3 + inner.length());
  };

  private static void insertSpace(@NotNull InsertionContext context) {
    int tail = context.getTailOffset();
    CharSequence chars = context.getDocument().getCharsSequence();
    if (tail >= chars.length() || chars.charAt(tail) != ' ') {
      context.getDocument().insertString(tail, " ");
    }
    context.getEditor().getCaretModel().moveToOffset(tail + 1);
  }

  private static @NotNull String indentAt(@NotNull InsertionContext context) {
    int offset = context.getStartOffset();
    CharSequence text = context.getDocument().getCharsSequence();
    int lineStart = offset;
    while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
      lineStart--;
    }
    StringBuilder indent = new StringBuilder();
    for (int i = lineStart; i < offset; i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t') {
        break;
      }
      indent.append(c);
    }
    return indent.toString();
  }
}
