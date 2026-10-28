package io.prismio.ums;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import io.prismio.icons.PrismioIcons;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UmsColorSettingsPage implements ColorSettingsPage {

  private static final AttributesDescriptor[] DESCRIPTORS = {
      new AttributesDescriptor("Block name", UmsSyntaxHighlighter.BLOCK),
      new AttributesDescriptor("Property key", UmsSyntaxHighlighter.KEY),
      new AttributesDescriptor("Declaration call", UmsSyntaxHighlighter.CALL),
      new AttributesDescriptor("Identifier", UmsSyntaxHighlighter.IDENTIFIER),
      new AttributesDescriptor("Constant or enum name", UmsSyntaxHighlighter.CONSTANT),
      new AttributesDescriptor("String", UmsSyntaxHighlighter.STRING),
      new AttributesDescriptor("String interpolation", UmsSyntaxHighlighter.INTERPOLATION),
      new AttributesDescriptor("String dollar escape", UmsSyntaxHighlighter.ESCAPE),
      new AttributesDescriptor("Number", UmsSyntaxHighlighter.NUMBER),
      new AttributesDescriptor("Boolean", UmsSyntaxHighlighter.BOOLEAN),
      new AttributesDescriptor("Comment", UmsSyntaxHighlighter.COMMENT),
      new AttributesDescriptor("Braces", UmsSyntaxHighlighter.BRACES),
      new AttributesDescriptor("Parentheses", UmsSyntaxHighlighter.PARENTHESES),
      new AttributesDescriptor("Brackets", UmsSyntaxHighlighter.BRACKETS),
      new AttributesDescriptor("Comma", UmsSyntaxHighlighter.COMMA),
      new AttributesDescriptor("Assignment", UmsSyntaxHighlighter.EQUAL),
      new AttributesDescriptor("Semicolon", UmsSyntaxHighlighter.SEMICOLON),
      new AttributesDescriptor("Bad character", UmsSyntaxHighlighter.BAD_CHARACTER),
  };

  @Override
  public @Nullable Icon getIcon() {
    return PrismioIcons.UMS;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new UmsSyntaxHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    return """
        // A manifest, with every block this plugin knows.
        # `#` starts a comment too.

        toolchain {
            <key>host</key> = ".prismio/build/debug/prismio"
        }

        <keyword>const</keyword> <constant>MAJOR</constant> = 0
        <keyword>const</keyword> <constant>VERSION</constant> = "<interp>${MAJOR}</interp>.1.0"
        <keyword>enum</keyword> <constant>Channel</constant> { STABLE = "stable", BETA }

        project {
            <key>name</key> = "app-<interp>${Channel.BETA}</interp>"
            <key>version</key> = <constant>VERSION</constant>
            <key>prismio</key> = "0.1.0"
            <key>description</key> = "costs <escape>$$</escape>5"
            <key>authors</key> = ["Ada Lovelace"]
        }

        targets {
            <call>executable</call>("app") {
                <key>entry</key> = "src/main.psm"
                native {
                    <call>source</call>("c/codec.c")
                    <call>include</call>("c/include")
                }
                link {
                    <call>library</call>("sqlite3")
                }
            }
        }

        commands {
            <call>command</call>("dist") {
                <key>description</key> = "Package a release archive"
                <call>build</call>("app")
                <call>run</call>("tools/package.py", "--out", "dist", <boolean>args</boolean>)
            }
        }

        dependencies {
            <call>implementation</call>("json", "1.2.0", "../json")
        }
        """;
  }

  @Override
  public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return Map.of(
        "key", UmsSyntaxHighlighter.KEY,
        "call", UmsSyntaxHighlighter.CALL,
        "boolean", UmsSyntaxHighlighter.BOOLEAN,
        "keyword", UmsSyntaxHighlighter.BLOCK,
        "constant", UmsSyntaxHighlighter.CONSTANT,
        "interp", UmsSyntaxHighlighter.INTERPOLATION,
        "escape", UmsSyntaxHighlighter.ESCAPE);
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  public @NotNull String getDisplayName() {
    return "UMS Manifest";
  }
}
