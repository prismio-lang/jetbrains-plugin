package io.prismio.lsp;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What completion must offer, asked of the language server: members from the receiver's type, the names in scope,
 * the library's names from modules not yet imported, imports, types, associated functions, the compiler's own
 * builtins and keywords.
 *
 * <p>These were the plugin's own completion tests; the plugin's completion was deleted once every one of them held of
 * the server (24 of 35 failed on the first run). The fixture is only a place to write project files (a test adds
 * {@code project/host.psm}); they are copied beside the document the server is asked about. Needs a toolchain with the
 * compiler repository's 01ed914 or later, and is skipped without {@code -Dprismio.checkout} and
 * {@code -Dprismio.compiler}.
 */
public class PrismioServerCompletionTest extends BasePlatformTestCase {

  public static final String STDLIB = Path.of("src/test/testData/stdlib").toAbsolutePath().toString();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    corpus = ServerCorpus.start();
  }

  private ServerCorpus corpus;

  @Override
  protected void tearDown() throws Exception {
    try {
      if (corpus != null) {
        corpus.close();
      }
    } finally {
      super.tearDown();
    }
  }

  private void copyProjectFiles(Path directory) throws java.io.IOException {
    com.intellij.openapi.vfs.VirtualFile root = myFixture.getTempDirFixture().getFile("/");
    if (root == null) {
      return;
    }
    com.intellij.openapi.vfs.VfsUtilCore.visitChildrenRecursively(root, new com.intellij.openapi.vfs.VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(com.intellij.openapi.vfs.VirtualFile f) {
        if (!f.isDirectory() && "psm".equals(f.getExtension())) {
          try {
            Path target = directory.resolve(com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(f, root));
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.write(target, f.contentsToByteArray());
          } catch (java.io.IOException e) {
            throw new RuntimeException(e);
          }
        }
        return true;
      }
    });
  }

  private static void deleteTree(Path directory) throws java.io.IOException {
    try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(directory)) {
      walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  protected List<String> completeAt(String text) {
    if (corpus == null) {
      return List.of();
    }
    try {
      int caret = text.indexOf("<caret>");
      String clean = text.replace("<caret>", "");
      // Files a test added to the fixture's project are written beside the document, where the server can read them.
      Path directory = java.nio.file.Files.createTempDirectory("prismio-completion");
      copyProjectFiles(directory);
      Path file = directory.resolve("server_completion.psm");
      if (!corpus.probe.open(file, clean, 90)) {
        fail("the server never analysed the buffer");
      }
      int line = (int) clean.substring(0, caret).chars().filter(c -> c == '\n').count();
      int column = caret - (clean.lastIndexOf('\n', caret - 1) + 1);
      com.google.gson.JsonElement result = corpus.probe.at("textDocument/completion", file, line, column);
      corpus.probe.closeDocument(file);
      deleteTree(directory);
      List<String> labels = new ArrayList<>();
      com.google.gson.JsonArray items = null;
      if (result != null && result.isJsonArray()) {
        items = result.getAsJsonArray();
      } else if (result != null && result.isJsonObject() && result.getAsJsonObject().has("items")) {
        items = result.getAsJsonObject().getAsJsonArray("items");
      }
      if (items != null) {
        for (com.google.gson.JsonElement e : items) {
          labels.add(e.getAsJsonObject().get("label").getAsString());
        }
      }
      // The editor narrows the list to what has been typed; the server offers everything that could be written.
      int start = caret;
      while (start > 0 && Character.isJavaIdentifierPart(clean.charAt(start - 1))) {
        start--;
      }
      String prefix = clean.substring(start, caret).toLowerCase();
      labels.removeIf(label -> !label.toLowerCase().startsWith(prefix));
      return labels;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
    // A bare `[]` is an array, not a Vec: nothing is pushed onto it.
    assertDoesntContain(completeAt("""
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
