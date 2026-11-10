package io.prismio.wizard;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What {@code prismio init <name>} writes (src/project/ums_cli.psm, {@code initUmsProject}), for
 * both New Project dialogs: the step IntelliJ IDEA shows and the generator the other IDEs show.
 * Nothing that already exists is overwritten.
 */
public final class PrismioProjectFiles {

  /** The compiler version a manifest names when no compiler answers {@code --version}: the one this plugin was built with. */
  public static final String FALLBACK_VERSION = "0.2.0";

  private static final String MAIN = """
      import std.io

      fn main() -> Int {
          println("Hello, Prismio!")
          return 0
      }
      """;

  private PrismioProjectFiles() {}

  /** The entry file, or null when there was a manifest to leave alone. Needs a write action. */
  static @Nullable VirtualFile create(@NotNull Path root, @NotNull String name, @NotNull String version)
      throws IOException {
    VirtualFile dir = VfsUtil.createDirectoryIfMissing(root.toString());
    if (dir == null) {
      throw new IOException("cannot create " + root);
    }
    return create(dir, name, version);
  }

  /** As above, in a directory that already exists. */
  static @Nullable VirtualFile create(@NotNull VirtualFile dir, @NotNull String name, @NotNull String version)
      throws IOException {
    if (dir.findChild("build.ums") != null) {
      return null;
    }
    VirtualFile src = VfsUtil.createDirectoryIfMissing(dir, "src");
    VfsUtil.saveText(dir.createChildData(PrismioProjectFiles.class, "build.ums"), manifest(name, version));
    VirtualFile main = src.findChild("main.psm");
    if (main == null) {
      main = src.createChildData(PrismioProjectFiles.class, "main.psm");
      VfsUtil.saveText(main, MAIN);
    }
    if (dir.findChild(".gitignore") == null) {
      VfsUtil.saveText(dir.createChildData(PrismioProjectFiles.class, ".gitignore"), ".prismio/\n");
    }
    return main;
  }

  static @NotNull String manifest(@NotNull String name, @NotNull String version) {
    return "project {\n    name = \"" + name + "\"\n    version = \"0.1.0\"\n    prismio = \"" + version
        + "\"\n}\n\ntargets {\n    executable(\"" + name + "\") {\n        entry = \"src/main.psm\"\n    }\n}\n";
  }

  /**
   * A name UMS accepts: a letter, digit or {@code _} first, then those plus {@code -} and
   * {@code .} ({@code umsValidName}). What a folder may be called is wider, so the rest becomes
   * {@code _} rather than writing a manifest the compiler then refuses.
   */
  static @NotNull String projectName(@NotNull String chosen) {
    StringBuilder out = new StringBuilder();
    for (char c : chosen.trim().toCharArray()) {
      boolean word = Character.isLetterOrDigit(c) && c < 128 || c == '_';
      boolean inner = c == '-' || c == '.';
      out.append(word || (inner && !out.isEmpty()) ? c : '_');
    }
    return out.isEmpty() ? "app" : out.toString();
  }
}
