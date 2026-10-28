package io.prismio;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.navigation.PrismioGotoDeclarationHandler;

/**
 * Cmd+Click resolves a name to where it is declared.
 *
 * <p>The handler is called directly rather than through the action, so the test
 * asserts what was resolved rather than where an editor ended up.
 */
public class PrismioGotoDeclarationTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    io.prismio.toolchain.PrismioProjectSettings.getInstance(getProject()).getState().stdlibPath =
        io.prismio.lsp.PrismioServerCompletionTest.STDLIB;
  }

  /** The targets Cmd+Click would offer for the name at `<caret>`. */
  private PsiElement[] targetsAtCaret(String source) {
    myFixture.configureByText(PrismioFileType.INSTANCE, source);
    int offset = myFixture.getCaretOffset();
    PsiElement at = myFixture.getFile().findElementAt(offset);
    return new PrismioGotoDeclarationHandler()
        .getGotoDeclarationTargets(at, offset, myFixture.getEditor());
  }

  private static String lineOf(PsiElement element) {
    String text = element.getContainingFile().getText();
    int start = text.lastIndexOf('\n', element.getTextRange().getStartOffset()) + 1;
    int end = text.indexOf('\n', element.getTextRange().getStartOffset());
    return text.substring(start, end < 0 ? text.length() : end).trim();
  }

  public void testCallResolvesToItsFunction() {
    PsiElement[] targets = targetsAtCaret("""
        fn helper(value: Int) -> Int {
            return value * 2
        }

        fn main() -> Int {
            return hel<caret>per(21)
        }
        """);
    assertNotNull("a call should resolve", targets);
    assertEquals(1, targets.length);
    assertEquals("fn helper(value: Int) -> Int {", lineOf(targets[0]));
  }

  public void testStructEnumAndTraitNamesResolve() {
    String source = """
        struct Point { x: Int }
        enum Direction { North }
        trait Drawable { fn draw(self) -> Int }

        fn use() -> Int {
            let p: Po<caret>int = Point { x: 1 }
            return p.x
        }
        """;
    PsiElement[] targets = targetsAtCaret(source);
    assertNotNull(targets);
    assertEquals(1, targets.length);
    assertEquals("struct Point { x: Int }", lineOf(targets[0]));

    targets = targetsAtCaret(source.replace("Po<caret>int", "Point").replace(
        "enum Direction", "enum Direc<caret>tion"));
    assertNull("a declaration should not offer to jump to itself", targets);
  }

  public void testClickingTheDeclarationItselfResolvesNowhere() {
    assertNull(targetsAtCaret("""
        fn help<caret>er(value: Int) -> Int {
            return value
        }
        """));
  }

  public void testAKeywordIsNotANavigableName() {
    assertNull(targetsAtCaret("""
        fn main() -> Int {
            ret<caret>urn 0
        }
        """));
  }

  public void testUnknownNamesResolveToNothing() {
    assertNull(targetsAtCaret("""
        fn main() -> Int {
            return absent<caret>Name(1)
        }
        """));
  }

  public void testImportNavigatesToTheModule() {
    myFixture.addFileToProject("std/io.psm", "fn println(text: String) { }\n");
    PsiElement[] targets = targetsAtCaret("""
        import std.i<caret>o

        fn main() -> Int { return 0 }
        """);
    assertNotNull("an import should navigate", targets);
    assertEquals(1, targets.length);
    assertEquals("io.psm", targets[0].getContainingFile().getName());
  }

  public void testAnySegmentOfAnImportNavigatesToTheSameModule() {
    myFixture.addFileToProject("ir/expr.psm", "fn emit() { }\n");
    PsiElement[] targets = targetsAtCaret("""
        import i<caret>r.expr

        fn main() -> Int { return 0 }
        """);
    assertNotNull(targets);
    assertEquals("expr.psm", targets[0].getContainingFile().getName());
  }

  public void testAnImportOfSomethingAbsentResolvesToNothing() {
    assertNull(targetsAtCaret("""
        import no.such.mod<caret>ule

        fn main() -> Int { return 0 }
        """));
  }

  public void testResolutionCrossesFiles() {
    myFixture.addFileToProject("helpers.psm", """
        fn shared(value: Int) -> Int {
            return value + 1
        }
        """);
    PsiElement[] targets = targetsAtCaret("""
        fn main() -> Int {
            return sha<caret>red(1)
        }
        """);
    assertNotNull("a name declared in another file should resolve", targets);
    assertEquals(1, targets.length);
    assertEquals("helpers.psm", targets[0].getContainingFile().getName());
  }

  public void testTheCurrentFileShadowsTheProject() {
    myFixture.addFileToProject("other.psm", """
        fn shared(value: Int) -> Int {
            return value + 1
        }
        """);
    PsiElement[] targets = targetsAtCaret("""
        fn shared(value: Int) -> Int {
            return value * 2
        }

        fn main() -> Int {
            return sha<caret>red(1)
        }
        """);
    assertNotNull(targets);
    assertEquals("the local declaration wins outright", 1, targets.length);
    assertEquals("the local declaration wins outright",
        "fn shared(value: Int) -> Int {", lineOf(targets[0]));
  }

  public void testMethodCallResolvesThroughTheReceiversType() {
    myFixture.addFileToProject("other.psm", "fn contains(a: Int) -> Bool { return true }\n");
    PsiElement[] targets = targetsAtCaret("""
        import std.vec

        fn main() -> Int {
            let xs: Vec<Int> = [1, 2, 3]
            xs.cont<caret>ains(1)
            return 0
        }
        """);
    assertNotNull(targets);
    assertEquals(1, targets.length);
    assertEquals("vec.psm", targets[0].getContainingFile().getName());
    assertTrue(lineOf(targets[0]).startsWith("public fn contains<T: Eq>"));
  }
}
