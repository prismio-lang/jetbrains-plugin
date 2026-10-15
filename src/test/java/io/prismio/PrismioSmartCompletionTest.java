package io.prismio;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.imports.AddImportFix;
import io.prismio.toolchain.PrismioProjectSettings;
import java.nio.file.Path;
import java.util.List;

/**
 * Completion driven by the toolchain's standard library: members from the
 * receiver's inferred type, functions and types from modules not yet imported,
 * and the import each choice needs.
 *
 * <p>The library is {@code src/test/testData/stdlib}, a miniature one in the real
 * module shapes, so what is asserted here does not depend on which compiler is
 * installed.
 */
public class PrismioSmartCompletionTest extends BasePlatformTestCase {

  static final String STDLIB = Path.of("src/test/testData/stdlib").toAbsolutePath().toString();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PrismioProjectSettings.getInstance(getProject()).getState().stdlibPath = STDLIB;
  }

  /**
   * The lookup's strings. Single-candidate insertion is switched off for the call,
   * or a prefix only one item matches would read as an empty list.
   */
  private List<String> completeAt(String text) {
    myFixture.configureByText(PrismioFileType.INSTANCE, text);
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean before = settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      myFixture.completeBasic();
    } finally {
      settings.AUTOCOMPLETE_ON_CODE_COMPLETION = before;
    }
    List<String> strings = myFixture.getLookupElementStrings();
    return strings == null ? List.of() : strings;
  }

  private void select(String lookupString) {
    LookupElement[] elements = myFixture.getLookupElements();
    if (elements == null) {
      return; // the only candidate was inserted already
    }
    for (LookupElement element : elements) {
      if (element.getLookupString().equals(lookupString)) {
        myFixture.getLookup().setCurrentItem(element);
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        return;
      }
    }
    fail("no lookup element " + lookupString + " in " + myFixture.getLookupElementStrings());
  }

  // ------------------------------------------------------------ freestanding

  /**
   * The builtins a kernel uses are in no module, so the library index cannot offer
   * them; they come from a table written out from {@code src/common/mem_builtins.psm}.
   */
  public void testFreestandingBuiltinsAreOffered() {
    List<String> names = completeAt("""
        fn main() -> Int {
            __builtin_mem_v<caret>
            return 0
        }
        """);
    assertContainsElements(names, "__builtin_mem_vstore_u32", "__builtin_mem_vload_u8");
    assertDoesntContain(names, "__builtin_mem_aadd_u32");
  }

  public void testFunctionModifiersAreOfferedAtTopLevel() {
    List<String> words = completeAt("<caret>\n");
    assertContainsElements(words, "export", "naked", "section", "packed");
  }

  public void testAsyncIsOfferedAtTopLevelAndAwaitAndScopeInBodies() {
    assertContainsElements(completeAt("<caret>\n"), "async");
    List<String> statement = completeAt("""
        fn main() -> Int {
            <caret>
            return 0
        }
        """);
    assertContainsElements(statement, "scope");
    List<String> expression = completeAt("""
        fn main() -> Int {
            let x = <caret>
            return 0
        }
        """);
    assertContainsElements(expression, "await", "spawn");
  }

  // ------------------------------------------------------------ members

  public void testStringLiteralLocalOffersStringMembers() {
    List<String> members = completeAt("""
        import std.string

        fn main() -> Int {
            let x = "Hello"
            x.<caret>
            return 0
        }
        """);
    assertContainsElements(members, "length", "isEmpty", "contains", "trim", "find", "show");
    assertDoesntContain(members, "hidden", "append", "countOnes");
  }

  public void testMemberFromUnimportedModuleAddsTheImport() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        fn main() -> Int {
            let x = "Hello"
            let y = x.tri<caret>
            return 0
        }
        """);
    myFixture.completeBasic();
    myFixture.checkResult("""
        import std.string

        fn main() -> Int {
            let x = "Hello"
            let y = x.trim()<caret>
            return 0
        }
        """);
  }

  public void testAnnotatedLocalAndParameter() {
    assertContainsElements(completeAt("""
        fn f(name: String) {
            name.<caret>
        }
        """), "length", "trim");
    assertContainsElements(completeAt("""
        fn f() {
            let n: Int = compute()
            n.<caret>
        }
        """), "countOnes", "show");
  }

  public void testStaticCallResultAndChain() {
    List<String> members = completeAt("""
        fn main() {
            let b = StringBuilder.new()
            b.<caret>
        }
        """);
    assertContainsElements(members, "append", "length", "toString");
    assertDoesntContain(members, "new");

    assertContainsElements(completeAt("""
        fn main() {
            let b = StringBuilder.new()
            b.toString().<caret>
        }
        """), "trim", "find");
  }

  public void testGenericReturnIsSubstituted() {
    assertContainsElements(completeAt("""
        fn main() {
            let s = "abc"
            let found = s.find("b")
            found.<caret>
        }
        """), "unwrapOr", "expect");
    // `unwrapOr` on an `Int?` returns `T`, which is `Int` here.
    assertContainsElements(completeAt("""
        fn main() {
            let s = "abc"
            s.find("b").unwrapOr(0).<caret>
        }
        """), "countOnes");
  }

  /** A reference's `T?` has no intrinsics: `unwrapOr`, `expect` and `map` are std.option's functions over `T?`. */
  public void testAReferenceOptionalOffersTheOptionFunctions() {
    List<String> members = completeAt("""
        import std.option
        import std.process
        fn main() {
            let home = process.env("HOME")
            home.<caret>
        }
        """);
    assertContainsElements(members, "unwrapOr", "expect", "map");
    assertDoesntContain(members, "isSome", "isNone");
  }

  public void testStaticMembersOfAType() {
    List<String> ofInt = completeAt("""
        fn main() {
            let top = Int.<caret>
        }
        """);
    assertContainsElements(ofInt, "MAX", "MIN");
    assertDoesntContain(ofInt, "countOnes");

    assertContainsElements(completeAt("""
        fn main() {
            let b = StringBuilder.<caret>
        }
        """), "new");
    assertContainsElements(completeAt("""
        fn main() {
            let o = Result<Int, String>.<caret>
        }
        """), "Ok", "Err");
  }

  public void testVecOffersLoweredMembersAndLibraryFunctions() {
    List<String> members = completeAt("""
        fn main() {
            let mut v: Vec<Int> = []
            v.<caret>
        }
        """);
    // Lowered by the compiler (src/sema/vec.psm)...
    assertContainsElements(members, "push", "length", "isEmpty");
    // ...and free functions taking a Vec first, by uniform call syntax.
    assertContainsElements(members, "sort", "contains", "get");
  }

  /** `let x = [1, 2, 3, 4]` is an `Array<Int, 4>`: it has `length` and the searches, and no `push`. */
  public void testArrayLiteralIsAnArrayNotAVec() {
    List<String> members = completeAt("""
        import std.vec

        fn main() {
            let x = [1, 2, 3, 4]
            x.<caret>
        }
        """);
    assertContainsElements(members, "length", "contains", "indexOf", "lastIndexOf", "countOf");
    assertDoesntContain(members, "push", "pop", "isEmpty");
    // An annotation says the same: `[Int]` and `Array<Int, 3>` are arrays too.
    List<String> annotated = completeAt("""
        import std.vec

        fn main() {
            let y: Array<Int, 3> = [1, 2, 3]
            y.<caret>
        }
        """);
    assertContainsElements(annotated, "length", "contains");
    assertDoesntContain(annotated, "push");
    // An empty literal is a Vec, as sema gives `let v: Vec<T>` no initializer.
    assertContainsElements(completeAt("""
        fn main() {
            let z = []
            z.<caret>
        }
        """), "push");
  }

  public void testVectorLiteralAndForBinder() {
    assertContainsElements(completeAt("""
        fn main() {
            let names = ["a", "b"]
            for name in names {
                name.<caret>
            }
        }
        """), "trim", "length");
    assertContainsElements(completeAt("""
        fn main() {
            for i in 0..<10 {
                i.<caret>
            }
        }
        """), "countOnes");
  }

  public void testOwnStructFieldsThroughSelfAndConstructor() {
    String source = """
        struct Point {
            x: Int,
            y: Int
        }

        impl Point {
            fn norm(self) -> Int {
                return self.<caret>
            }
        }
        """;
    assertContainsElements(completeAt(source), "x", "y", "norm");

    assertContainsElements(completeAt("""
        struct Point {
            x: Int,
            y: Int
        }

        fn main() {
            let p = Point { x: 1, y: 2 }
            p.<caret>
        }
        """), "x", "y");
  }

  public void testModuleLevelGlobal() {
    assertContainsElements(completeAt("""
        import std.process

        fn main() {
            process.<caret>
        }
        """), "env");
  }

  public void testArithmeticAndConcatenation() {
    assertContainsElements(completeAt("""
        import std.string

        fn main() {
            let s = "a"
            let n = s.length + 1
            n.<caret>
        }
        """), "countOnes");
    assertContainsElements(completeAt("""
        fn main() {
            let t = "a" + "b"
            t.<caret>
        }
        """), "trim");
  }

  public void testUnknownReceiverOffersNothingRatherThanGuessing() {
    List<String> members = completeAt("""
        fn main() {
            mystery().<caret>
        }
        """);
    assertDoesntContain(members, "trim", "push", "countOnes");
  }

  // ------------------------------------------------------------ auto-import

  public void testLibraryFunctionAddsItsImport() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        // A program that prints.

        fn main() -> Int {
            printl<caret>
            return 0
        }
        """);
    myFixture.completeBasic();
    myFixture.checkResult("""
        // A program that prints.

        import std.io

        fn main() -> Int {
            println(<caret>)
            return 0
        }
        """);
  }

  public void testImportGoesInSortedPosition() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        import std.io
        import std.vec

        fn main() {
            let b = StringBui<caret>
        }
        """);
    myFixture.completeBasic();
    myFixture.checkResult("""
        import std.io
        import std.string
        import std.vec

        fn main() {
            let b = StringBuilder<caret>
        }
        """);
  }

  public void testImportedModuleIsNotImportedTwice() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        import {io, vec} from std

        fn main() {
            printl<caret>
        }
        """);
    myFixture.completeBasic();
    myFixture.checkResult("""
        import {io, vec} from std

        fn main() {
            println(<caret>)
        }
        """);
  }

  public void testQuickFixImportsTheDeclaringModule() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        fn main() {
            println("hi")
        }
        """);
    List<AddImportFix> fixes = AddImportFix.forMessage(getProject(), "unknown function `println`");
    assertEquals(1, fixes.size());
    assertEquals("Import std.io (for println)", fixes.get(0).getText());
    WriteCommandAction.runWriteCommandAction(getProject(),
        () -> fixes.get(0).invoke(getProject(), myFixture.getEditor(), myFixture.getFile()));
    assertTrue(myFixture.getFile().getText().startsWith("import std.io\n\nfn main()"));

    List<AddImportFix> vector = AddImportFix.forMessage(getProject(), "a vector literal needs `import std.vec`");
    assertEquals("Import std.vec", vector.get(0).getText());
    assertEmpty(AddImportFix.forMessage(getProject(), "unknown function `nothingDeclaresThis`"));
  }

  // ------------------------------------------------------------ positions

  public void testKeywordsAtTheStartOfEveryLine() {
    assertContainsElements(completeAt("""
        import std.io
        imp<caret>
        """), "import");
    assertContainsElements(completeAt("""
        fn main() -> Int {
            let x = 1
            ret<caret>
        }
        """), "return");
    assertContainsElements(completeAt("""
        fn main() {
            if (true) {
            } el<caret>
        }
        """), "else");
  }

  public void testImportListsTheToolchainModules() {
    List<String> after = completeAt("import <caret>\n");
    assertContainsElements(after, "std", "std.io", "std.string", "std.vec");

    List<String> leaves = completeAt("import std.<caret>\n");
    assertContainsElements(leaves, "io", "string", "vec", "option", "math", "process");
    assertDoesntContain(leaves, "list");

    assertContainsElements(completeAt("import {<caret>} from std\n"), "io", "string");
    assertContainsElements(completeAt("import std.string.<caret>\n"), "join", "StringBuilder");
  }

  /** `from std import {...}` is the same group with the directory first. */
  public void testFromImportGroupListsTheModulesOfItsDirectory() {
    assertContainsElements(completeAt("from std import {<caret>}\n"), "io", "string");
    myFixture.addFileToProject("project/ums_cli.psm", "fn run() {}\n");
    myFixture.addFileToProject("project/host.psm", "fn run() {}\n");
    List<String> names = completeAt("from project import {ums_cli, <caret>}\n");
    assertContainsElements(names, "ums_cli", "host");
    assertContainsElements(completeAt("from <caret>\n"), "std");
  }

  public void testImportGroupListsTheModulesOfAProjectDirectory() {
    myFixture.addFileToProject("project/ums_cli.psm", "fn run() {}\n");
    myFixture.addFileToProject("project/host.psm", "fn run() {}\n");
    myFixture.addFileToProject("project/deep/inner.psm", "fn run() {}\n");
    List<String> names = completeAt("import {ums_cli, <caret>} from project\n");
    assertContainsElements(names, "ums_cli", "host");
    assertDoesntContain(names, "deep");
    // the directory can be written after the caret, or still be missing
    assertContainsElements(completeAt("import {<caret>} from project\n"), "host", "ums_cli");
    assertContainsElements(completeAt("import {<caret>}\n"), "io", "string");
  }

  public void testClosedImportGroupOffersFrom() {
    assertEquals(List.of("from"), completeAt("import {io} <caret>\n"));
    assertEquals(List.of("from"), completeAt("import {io} f<caret>\n"));
    assertEquals(List.of("from"), completeAt("import * <caret>\n"));
    assertDoesntContain(completeAt("import {io} from std <caret>\n"), "from");
    myFixture.configureByText(PrismioFileType.INSTANCE, "import {io} fr<caret>\n");
    myFixture.completeBasic();
    myFixture.checkResult("import {io} from <caret>\n");
  }

  public void testImportKeywordInsertsSpace() {
    myFixture.configureByText(PrismioFileType.INSTANCE, "impo<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("import <caret>");
  }

  public void testForTakesNoParentheses() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        fn main() {
            fo<caret>
        }
        """);
    myFixture.completeBasic();
    select("for");
    myFixture.checkResult("""
        fn main() {
            for <caret>
        }
        """);
  }

  public void testNothingInsideStringsOrComments() {
    assertEmpty(completeAt("""
        fn main() {
            let s = "pri<caret>"
        }
        """));
    assertEmpty(completeAt("""
        // pri<caret>
        """));
  }

  public void testNameBeingDeclaredGetsNoSuggestions() {
    assertEmpty(completeAt("""
        fn main() {
            let pri<caret>
        }
        """));
  }

  public void testTypePositionOffersLibraryTypes() {
    List<String> types = completeAt("""
        fn f(b: StringB<caret>) {
        }
        """);
    assertContainsElements(types, "StringBuilder");
    List<String> annotation = completeAt("""
        fn main() {
            let v: <caret>
        }
        """);
    assertContainsElements(annotation, "Int", "String", "Vec", "Result", "StringBuilder");
    assertDoesntContain(annotation, "println", "return");
  }

  // ------------------------------------------------------------ parameter info and hints

  private io.prismio.symbols.TypeInference.Call callAtCaret(String text) {
    myFixture.configureByText(PrismioFileType.INSTANCE, text);
    var file = myFixture.getFile();
    return io.prismio.symbols.TypeInference.callAt(file.getText(), myFixture.getCaretOffset(),
        io.prismio.symbols.ProjectSymbols.scope(file), io.prismio.symbols.ProjectSymbols.summary(file).imports());
  }

  public void testParameterInfoFindsOverloadsAndArgument() {
    var call = callAtCaret("""
        fn main() {
            let s = "abc"
            s.contains(<caret>)
        }
        """);
    assertNotNull(call);
    assertEquals(2, call.overloads().size());
    assertEquals(0, call.argument());

    var free = callAtCaret("""
        fn main() {
            join(parts, <caret>)
        }
        """);
    assertNotNull(free);
    assertEquals("join", free.overloads().get(0).name());
    assertEquals(1, free.argument());

    // A nested call's commas are its own.
    var outer = callAtCaret("""
        fn main() {
            join(vecOf("a", "b"), <caret>)
        }
        """);
    assertEquals(1, outer.argument());
  }

  public void testParameterInfoOnVecDropsTheReceiver() {
    var call = callAtCaret("""
        fn main() {
            let v: Vec<Int> = []
            v.contains(<caret>)
        }
        """);
    assertNotNull(call);
    assertTrue(call.asMethod());
    assertEquals("needle: T", call.overloads().get(0).callParams(true).get(0).toString());
  }

  public void testLetTypeHints() {
    myFixture.configureByText(PrismioFileType.INSTANCE, """
        fn main() {
            let s = "abc"
            let found = s.find("b")
            let b = StringBuilder.new()
            let n = found.unwrapOr(0)
            let unknown = mystery()
            let typed: Int = 3
        }
        """);
    var file = myFixture.getFile();
    var types = io.prismio.symbols.TypeInference.letTypes(file.getText(),
        io.prismio.symbols.ProjectSymbols.scope(file), io.prismio.symbols.ProjectSymbols.summary(file).imports());
    java.util.Map<String, String> byName = new java.util.TreeMap<>();
    String text = file.getText();
    types.forEach((end, type) -> {
      int start = end;
      while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
        start--;
      }
      byName.put(text.substring(start, end), type.toString());
    });
    assertEquals(java.util.Map.of("found", "Int?", "b", "StringBuilder", "n", "Int"), byName);
  }

  /** docs/PTR_PLAN.md: `p.` through a `Ptr<Struct>` offers the struct's fields, and `Ptr<mut Int>` offers `load`. */
  public void testTypedPointerOffersFieldsOrLoad() {
    String declarations = """
        struct Header {
            magic: U32,
            payloadLength: U32,
        }

        """;
    List<String> fields = completeAt(declarations + """
        fn verify(p: Ptr<mut Header>) -> Bool {
            return p.<caret>
        }
        """);
    assertContainsElements(fields, "magic", "payloadLength");
    assertDoesntContain(fields, "load");
    List<String> scalar = completeAt(declarations + """
        fn bump(counter: Ptr<mut volatile Int>) {
            counter.<caret>
        }
        """);
    assertContainsElements(scalar, "load");
    assertDoesntContain(scalar, "magic");
  }

  /** The three reports this completion work started from, in the file they were made in. */
  public void testTheSandboxReports() {
    String sandbox = """
        import std.display
        import std.io
        import std.string
        %s

        fn main() -> Int {
            let greeting = "Hello, World"

            println(greeting.%s)
            %s
            return 0
        }
        """;
    assertContainsElements(completeAt(sandbox.formatted("im<caret>", "", "")), "import");
    assertContainsElements(completeAt(sandbox.formatted("", "<caret>", "")), "length", "trim", "contains");
    assertContainsElements(completeAt(sandbox.formatted("", "", "pri<caret>")), "print", "println");
  }
}
