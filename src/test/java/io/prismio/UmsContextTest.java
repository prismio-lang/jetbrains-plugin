package io.prismio;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.ums.UmsContext;
import io.prismio.ums.UmsFileType;
import io.prismio.ums.UmsWords;
import java.util.List;

/**
 * Position decides meaning in a manifest, so this is the part worth testing.
 *
 * <p>{@code library} is a target kind inside {@code targets} and a linker input
 * inside {@code link} — the same word, one nesting level apart, and the only
 * thing that tells them apart is the enclosing block path.
 */
public class UmsContextTest extends BasePlatformTestCase {

  private static final String MANIFEST = """
      toolchain {
          host = ".prismio/build/debug/prismio"
      }

      project {
          name = "app"
      }

      targets {
          executable("app") {
              entry = "src/main.psm"
              link {
                  library("sqlite3")
              }
          }
      }

      commands {
          command("dist") {
              description = "Package"
              run("tools/package.py", args)
          }
      }
      """;

  private PsiFile manifest() {
    return myFixture.configureByText(UmsFileType.INSTANCE, MANIFEST);
  }

  /** The block path at the offset just after the given snippet's opening brace. */
  private List<String> pathInsideBlockOpenedBy(String snippet) {
    PsiFile file = manifest();
    int brace = MANIFEST.indexOf(snippet) + snippet.length();
    return UmsContext.pathAt(file, brace + 1);
  }

  private static final String DECLARING = """
      const MAJOR = 3
      const VERSION = "${MAJOR}.4.5"
      enum Channel { STABLE = "stable", BETA }

      project {
          name = "app"
          version = VERSION
          description = Channel.BETA
          prismio = "0.1.0"
      }
      """;

  private UmsContext contextOf(String text, String word, int occurrence) {
    PsiFile file = myFixture.configureByText(UmsFileType.INSTANCE, text);
    int from = -1;
    for (int i = 0; i <= occurrence; i++) {
      from = text.indexOf(word, from + 1);
    }
    var leaf = file.findElementAt(from);
    assertNotNull(leaf);
    return UmsContext.of(leaf);
  }

  public void testConstAndEnumStartADeclaration() {
    assertEquals(UmsContext.Role.KEYWORD, contextOf(DECLARING, "const", 0).role);
    assertEquals(UmsContext.Role.KEYWORD, contextOf(DECLARING, "enum", 0).role);
    assertTrue(contextOf(DECLARING, "const", 0).isRecognised());
  }

  public void testTheNameAfterTheKeywordIsADeclaration() {
    assertEquals(UmsContext.Role.DECLARATION, contextOf(DECLARING, "MAJOR", 0).role);
    assertEquals(UmsContext.Role.DECLARATION, contextOf(DECLARING, "VERSION", 0).role);
    assertEquals(UmsContext.Role.DECLARATION, contextOf(DECLARING, "Channel", 0).role);
    assertTrue(contextOf(DECLARING, "MAJOR", 0).isRecognised());
  }

  public void testAUseOfADeclaredNameIsAReference() {
    assertEquals(UmsContext.Role.REFERENCE, contextOf(DECLARING, "VERSION", 1).role);
    assertEquals(UmsContext.Role.REFERENCE, contextOf(DECLARING, "Channel.BETA", 0).role);
  }

  public void testAnUndeclaredNameStaysAnOrdinaryValue() {
    String text = "project { version = VERSION }";
    assertEquals(UmsContext.Role.VALUE, contextOf(text, "VERSION", 0).role);
    // `const` with no `NAME =` after it is not a declaration.
    assertEquals(UmsContext.Role.VALUE, contextOf("project { version = const }", "const", 0).role);
  }

  public void testADeclarationDoesNotChangeWhatTheBlocksAccept() {
    PsiFile file = myFixture.configureByText(UmsFileType.INSTANCE, DECLARING);
    int brace = DECLARING.indexOf("project {") + "project {".length();
    assertEquals(List.of("project"), UmsContext.pathAt(file, brace + 1));
    // The declarations are not blocks, so the top level still offers exactly the blocks.
    assertEquals(UmsWords.TOP_LEVEL_BLOCKS, UmsContext.completionsFor(List.of()));
    assertTrue(UmsWords.TOP_LEVEL_DECLARATIONS.containsKey("const"));
    assertTrue(UmsWords.ALL_KNOWN.contains("enum"));
  }

  public void testTopLevelOffersTheBlockNames() {
    PsiFile file = manifest();
    assertEquals(List.of(), UmsContext.pathAt(file, 0));
    assertEquals(UmsWords.TOP_LEVEL_BLOCKS, UmsContext.completionsFor(List.of()));
  }

  public void testPathTracksNesting() {
    assertEquals(List.of("project"), pathInsideBlockOpenedBy("project {"));
    assertEquals(List.of("targets"), pathInsideBlockOpenedBy("targets {"));
    // A block opened by a call carries the call's name, not its argument.
    assertEquals(List.of("targets", "executable"),
        pathInsideBlockOpenedBy("executable(\"app\") {"));
    assertEquals(List.of("targets", "executable", "link"),
        pathInsideBlockOpenedBy("link {"));
    assertEquals(List.of("commands", "command"),
        pathInsideBlockOpenedBy("command(\"dist\") {"));
  }

  public void testTheSameWordMeansDifferentThingsAtDifferentDepths() {
    // `library` is a target kind at one level and a linker input at the next.
    assertTrue(UmsContext.completionsFor(List.of("targets")).containsKey("library"));
    assertTrue(UmsContext.completionsFor(List.of("targets", "executable", "link"))
        .containsKey("library"));
    // ...and `entry` is neither of those places.
    assertFalse(UmsContext.completionsFor(List.of("targets")).containsKey("entry"));
    assertTrue(UmsContext.completionsFor(List.of("targets", "executable")).containsKey("entry"));
  }

  public void testUnmodelledBlocksOfferNothingRatherThanTheWrongThing() {
    // A block this plugin does not know: UMS parses new blocks before the model
    // learns them, so the honest answer is "no suggestions", not the top-level
    // list.
    assertNull(UmsContext.completionsFor(List.of("somethingNew")));
    assertNull(UmsContext.completionsFor(List.of("project", "nested")));
  }

  public void testCommandStepsAreRecognised() {
    var body = UmsContext.completionsFor(List.of("commands", "command"));
    assertNotNull(body);
    assertTrue(body.containsKey("build"));
    assertTrue(body.containsKey("run"));
    assertTrue(body.containsKey("shell"));
    assertTrue(body.containsKey("description"));
  }

  public void testNativeAndProfilesAreModelled() {
    assertTrue(UmsContext.completionsFor(List.of()).containsKey("profiles"));
    assertEquals(UmsWords.PROFILE_NAMES, UmsContext.completionsFor(List.of("profiles")));
    assertEquals(UmsWords.PROFILE_KEYS, UmsContext.completionsFor(List.of("profiles", "debug")));
    assertEquals(UmsWords.PROFILE_KEYS, UmsContext.completionsFor(List.of("profiles", "release")));
    assertNull(UmsContext.completionsFor(List.of("profiles", "debug", "extra")));

    var target = UmsContext.completionsFor(List.of("targets", "executable"));
    assertTrue(target.containsKey("native"));
    assertTrue(target.containsKey("runtime"));
    assertTrue(target.containsKey("exportDynamic"));

    var natives = UmsContext.completionsFor(List.of("targets", "executable", "native"));
    assertEquals(List.of("source", "include", "define", "flag", "responseFile"),
        List.copyOf(natives.keySet().stream().sorted(java.util.Comparator.comparingInt(
            List.of("source", "include", "define", "flag", "responseFile")::indexOf)).toList()));
  }

  public void testResponseFileIsALinkerInputAndACompilerInputAtDifferentDepths() {
    assertTrue(UmsContext.completionsFor(List.of("targets", "executable", "link"))
        .containsKey("responseFile"));
    assertTrue(UmsContext.completionsFor(List.of("targets", "executable", "native"))
        .containsKey("responseFile"));
    // ...and is neither a target property nor a top-level name.
    assertFalse(UmsContext.completionsFor(List.of("targets", "executable")).containsKey("responseFile"));
    assertFalse(UmsContext.completionsFor(List.of()).containsKey("responseFile"));
  }

  public void testTheCompilerIsNotABuiltInComponent() {
    // `component("prismio.backend")` is gone: nothing about the compiler is built into the toolchain.
    for (var table : List.of(UmsWords.TOP_LEVEL_BLOCKS, UmsWords.TARGET_BODY, UmsWords.LINK_KINDS)) {
      assertFalse(table.containsKey("component"));
    }
    assertFalse(UmsWords.ALL_KNOWN.contains("component"));
  }

  public void testANativeBlockInsideAManifestPathsAndReadsItsArguments() {
    String text = """
        targets {
            executable("demo") {
                entry = "src/main.psm"
                runtime = "none"
                native {
                    source("c/a.c", "c/b.c")
                    responseFile("flags.rsp")
                }
                link {
                    responseFile("link.rsp")
                }
            }
        }
        """;
    PsiFile file = myFixture.configureByText(UmsFileType.INSTANCE, text);
    assertEquals(List.of("targets", "executable", "native"),
        UmsContext.pathAt(file, text.indexOf("source(") + 1));
    assertEquals(List.of("targets", "executable", "link"),
        UmsContext.pathAt(file, text.indexOf("responseFile(\"link") + 1));

    UmsContext runtime = contextOf(file, text.indexOf("runtime"));
    assertEquals(UmsContext.Role.KEY, runtime.role);
    assertEquals("none", runtime.value.text());

    UmsContext source = contextOf(file, text.indexOf("source"));
    assertEquals(UmsContext.Role.CALL, source.role);
    assertEquals(List.of("c/a.c", "c/b.c"), source.arguments.stream().map(UmsContext.Value::text).toList());
  }

  private static UmsContext contextOf(PsiFile file, int offset) {
    var element = file.findElementAt(offset);
    assertNotNull(element);
    UmsContext context = UmsContext.of(element);
    assertNotNull(context);
    return context;
  }
}
