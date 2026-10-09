package io.prismio;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.prismio.ums.UmsFileType;
import io.prismio.ums.UmsValueChecks;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A manifest value the compiler refuses is an error in the editor, with the compiler's own
 * code; one it accepts is left alone. Every case here is a rule of {@code ums/model/}.
 */
public class UmsValueChecksTest extends BasePlatformTestCase {

  private List<String> errors(String manifest) {
    myFixture.configureByText(UmsFileType.INSTANCE, manifest);
    return myFixture.doHighlighting().stream()
        .filter(info -> HighlightSeverity.ERROR.equals(info.getSeverity()))
        .map(info -> info.getDescription())
        .collect(Collectors.toList());
  }

  private static String target(String body) {
    return "targets {\n    executable(\"app\") {\n        entry = \"src/main.psm\"\n" + body + "    }\n}\n";
  }

  public void testAValidManifestHasNoErrors() {
    assertEquals(List.of(), errors("""
        toolchain {
            host = ".prismio/build/debug/prismio"
        }
        project {
            name = "app"
            version = "1.0.0-beta.1"
            prismio = "0.1"
        }
        profiles {
            debug { debugInfo = true overflowChecks = true }
            release { overflowChecks = false }
        }
        targets {
            executable("app") {
                entry = "src/main.psm"
                runtime = "none"
                exportDynamic = true
                native {
                    source("c/a.c")
                    include("c")
                    define("X=1")
                    flag("-O2")
                    responseFile("flags.rsp")
                }
                link {
                    responseFile("link.rsp")
                }
            }
        }
        """));
  }

  public void testRuntimeIsInstalledOrNone() {
    assertEquals(List.of(), errors(target("        runtime = \"installed\"\n")));
    assertEquals(List.of("[UMS2114] target runtime is \"installed\" or \"none\", not \"system\""),
        errors(target("        runtime = \"system\"\n")));
  }

  public void testExportDynamicIsABoolean() {
    assertEquals(List.of(), errors(target("        exportDynamic = false\n")));
    // The properties of a target with no operating system under it.
    assertEquals(List.of(), errors(target("        freestanding = true\n        noRedZone = true\n")));
    assertEquals(List.of("[UMS2115] target freestanding must be true or false"),
        errors(target("        freestanding = \"yes\"\n")));
    assertEquals(List.of("[UMS2001] target triple must be a string literal"),
        errors(target("        triple = 64\n")));
    assertEquals(List.of(), errors(target("        codeModel = \"kernel\"\n")));
    assertEquals(List.of("[UMS2116] target codeModel is small, kernel, medium or large, not \"huge\""),
        errors(target("        codeModel = \"huge\"\n")));
    assertEquals(List.of("[UMS2115] target exportDynamic must be true or false"),
        errors(target("        exportDynamic = \"yes\"\n")));
    assertEquals(List.of("[UMS2115] target exportDynamic must be true or false"),
        errors(target("        exportDynamic = yes\n")));
  }

  public void testProfileSettingsAreBooleans() {
    assertEquals(List.of("[UMS2704] profile setting 'debugInfo' must be true or false"),
        errors("profiles {\n    debug {\n        debugInfo = \"on\"\n    }\n}\n"));
    assertEquals(List.of("[UMS2704] profile setting 'overflowChecks' must be true or false"),
        errors("profiles {\n    release {\n        overflowChecks = 1\n    }\n}\n"));
    assertEquals(List.of(), errors("profiles {\n    release {\n        overflowChecks = true\n    }\n}\n"));
  }

  public void testAnUnknownProfileNameIsFlaggedNotColoured() {
    myFixture.configureByText(UmsFileType.INSTANCE, "profiles {\n    fast {\n    }\n}\n");
    var weak = myFixture.doHighlighting().stream()
        .filter(info -> HighlightSeverity.WEAK_WARNING.equals(info.getSeverity()))
        .map(info -> info.getDescription())
        .toList();
    assertTrue(weak.toString(), weak.stream().anyMatch(d -> d != null && d.contains("`fast` is not a name UMS recognises")));
  }

  public void testAnyHostPathIsAccepted() {
    // The compiler accepts any host path; the `.trusted` stamp decides what runs.
    for (String host : List.of(".prismio/build/debug/prismio", "/usr/bin/prismio", "bin/prismio",
        "../.prismio/x", "C:\\\\p\\\\prismio")) {
      assertEquals(host, List.of(), errors("toolchain {\n    host = \"" + host + "\"\n}\n"));
    }
  }

  public void testNativeSourcesAreC() {
    assertEquals(List.of(), errors(target("        native {\n            source(\"c/a.c\", \"c/b.c\")\n        }\n")));
    assertEquals(List.of("[UMS2324] native source 'c/a.cpp' is not a C file (.c)"),
        errors(target("        native {\n            source(\"c/a.c\", \"c/a.cpp\")\n        }\n")));
    // Only sources are C; a flag or an include is whatever it is.
    assertEquals(List.of(), errors(target("        native {\n            flag(\"-std=c++17\")\n        }\n")));
  }

  public void testNamesStartWithALetterOrDigit() {
    assertEquals(List.of("[UMS2308] invalid target name '-app': letters, digits, '.', '_' and '-', "
            + "starting with a letter, digit or _"),
        errors("targets {\n    executable(\"-app\") {\n        entry = \"x.psm\"\n    }\n}\n"));
    assertEquals(List.of("[UMS2308] invalid target name '..': letters, digits, '.', '_' and '-', "
            + "starting with a letter, digit or _"),
        errors("targets {\n    executable(\"..\") {\n        entry = \"x.psm\"\n    }\n}\n"));
    assertEquals(List.of("[UMS2601] invalid command name '.x': letters, digits, '.', '_' and '-', "
            + "starting with a letter, digit or _"),
        errors("commands {\n    command(\".x\") {\n        shell(\"echo\")\n    }\n}\n"));
    assertEquals(List.of(), errors("commands {\n    command(\"1-dist_x.y\") {\n        shell(\"echo\")\n    }\n}\n"));
  }

  public void testProjectVersionIsSemVerTwo() {
    for (String good : List.of("0.1.0", "1.0.0-beta.1", "1.2.3+build.5", "1.0.0-rc.1+x")) {
      assertEquals(good, List.of(), errors("project {\n    version = \"" + good + "\"\n}\n"));
    }
    for (String bad : List.of("1.0", "01.02.0003.4", "1.0.0-", "1.0.0-01", "1.0.0.0", "v1.0.0", "")) {
      assertEquals(bad, List.of("[UMS2304] project.version must be a semantic version, such as 1.2.0 or 1.0.0-beta.1"),
          errors("project {\n    version = \"" + bad + "\"\n}\n"));
    }
  }

  public void testProjectNameAndRequiredPrismioVersion() {
    assertEquals(1, errors("project {\n    name = \"-x\"\n}\n").size());
    assertEquals(List.of(), errors("project {\n    name = \"x.y_z-1\"\n    prismio = \"0.1.2\"\n}\n"));
    assertEquals(List.of("[UMS2306] project.prismio must be MAJOR.MINOR or MAJOR.MINOR.PATCH, such as 0.1"),
        errors("project {\n    prismio = \"1\"\n}\n"));
    assertEquals(List.of("[UMS2001] project.name must be a string literal"),
        errors("project {\n    name = 3\n}\n"));
  }

  public void testTheRulesAgreeWithTheCompilersOnTheEdges() {
    assertTrue(UmsValueChecks.validName("a"));
    // The compiler's `umsIsAlnum` counts `_`, so `prismio init _x` works; not underlined.
    assertTrue(UmsValueChecks.validName("_x"));
    assertFalse(UmsValueChecks.validName("-x"));
    assertFalse(UmsValueChecks.validName("."));
    assertFalse(UmsValueChecks.validName(""));
    assertTrue(UmsValueChecks.validNumericVersion("0.1", 2, 3));
    assertFalse(UmsValueChecks.validNumericVersion("0.1.2.3", 2, 3));
    assertFalse(UmsValueChecks.validNumericVersion("0.01", 2, 3));
  }

  /**
   * Every valid manifest in a real checkout -- the compiler's own, which uses native, profiles and
   * response files, and the ones its tests load -- has no error here: a check that refuses what
   * the compiler accepts would paint a working project red. Skipped without
   * {@code -Dprismio.checkout=<dir>}. The {@code invalid} fixtures are the compiler's refusals
   * and are not part of the claim.
   */
  public void testNoErrorsOnAValidManifestOfARealCheckout() throws java.io.IOException {
    String checkout = System.getProperty("prismio.checkout");
    if (checkout == null || !java.nio.file.Files.isDirectory(java.nio.file.Path.of(checkout))) {
      return;
    }
    List<java.nio.file.Path> manifests;
    try (var walk = java.nio.file.Files.walk(java.nio.file.Path.of(checkout))) {
      manifests = walk.filter(p -> p.getFileName().toString().equals("build.ums"))
          .filter(p -> !p.toString().contains("/.prismio/") && !p.toString().contains("/third_party/")
              && !p.toString().contains("/invalid"))
          .toList();
    }
    assertFalse("no manifests found under " + checkout, manifests.isEmpty());
    for (java.nio.file.Path manifest : manifests) {
      assertEquals(manifest.toString(), List.of(), errors(java.nio.file.Files.readString(manifest)));
    }
  }
}
