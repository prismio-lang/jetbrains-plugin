package io.prismio.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * A flat parse: every token becomes a leaf under the file node.
 *
 * <p>Prismio's real parser lives in the compiler and produces an AST this plugin
 * has no access to. What the plugin does itself — highlighting, folding, the typing
 * handlers — is derived from the token stream; what needs meaning (completion,
 * navigation, hover, structure) is the language server's, which has the compiler's
 * own parse. A tree with shape would be a second, poorer answer to a question already answered. This replaced
 * a Grammar-Kit grammar that said exactly the same thing in 121 generated lines
 * and a {@code .bnf} that had to be regenerated to change a token name.
 */
public final class PrismioParser implements PsiParser {

  @Override
  public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    PsiBuilder.Marker file = builder.mark();
    while (!builder.eof()) {
      builder.advanceLexer();
    }
    file.done(root);
    return builder.getTreeBuilt();
  }
}
