package io.prismio.settings;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import io.prismio.highlighter.PrismioSyntaxHighlighter;
import io.prismio.icons.PrismioIcons;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Comprehensive color settings page for Prismio
 * Allows users to customize all syntax highlighting colors
 */
public final class PrismioColorSettingsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {// Keywords
      new AttributesDescriptor("Keywords//Keyword", PrismioSyntaxHighlighter.KEYWORD),
      new AttributesDescriptor(
          "Keywords//Contextual keyword", PrismioSyntaxHighlighter.CONTEXTUAL_KEYWORD),
      new AttributesDescriptor("Keywords//Built-in type", PrismioSyntaxHighlighter.BUILTIN_TYPE),
      new AttributesDescriptor("Keywords//Standard library type",
          PrismioSyntaxHighlighter.STDLIB_TYPE),

      // Literals
      new AttributesDescriptor("Literals//String", PrismioSyntaxHighlighter.STRING),
      new AttributesDescriptor("Literals//Number", PrismioSyntaxHighlighter.NUMBER),
      new AttributesDescriptor("Literals//Boolean", PrismioSyntaxHighlighter.BOOLEAN),
      new AttributesDescriptor("Literals//Character", PrismioSyntaxHighlighter.CHARACTER),

      // Identifiers
      new AttributesDescriptor("Identifiers//Identifier", PrismioSyntaxHighlighter.IDENTIFIER),
      new AttributesDescriptor(
          "Identifiers//Function Declaration", PrismioSyntaxHighlighter.FUNCTION_DECLARATION),
      new AttributesDescriptor(
          "Identifiers//Function Call", PrismioSyntaxHighlighter.FUNCTION_CALL),
      new AttributesDescriptor("Identifiers//Method Call", PrismioSyntaxHighlighter.METHOD_CALL),
      new AttributesDescriptor("Identifiers//Parameter", PrismioSyntaxHighlighter.PARAMETER),
      new AttributesDescriptor(
          "Identifiers//Local Variable", PrismioSyntaxHighlighter.LOCAL_VARIABLE),
      new AttributesDescriptor("Identifiers//Constant", PrismioSyntaxHighlighter.CONSTANT),
      new AttributesDescriptor("Identifiers//Field", PrismioSyntaxHighlighter.FIELD),
      new AttributesDescriptor("Identifiers//Struct Name", PrismioSyntaxHighlighter.STRUCT_NAME),
      new AttributesDescriptor("Identifiers//Enum Name", PrismioSyntaxHighlighter.ENUM_NAME),
      new AttributesDescriptor("Identifiers//Trait Name", PrismioSyntaxHighlighter.TRAIT_NAME),
      new AttributesDescriptor("Identifiers//Enum Variant", PrismioSyntaxHighlighter.ENUM_VARIANT),
      new AttributesDescriptor(
          "Identifiers//Type Reference", PrismioSyntaxHighlighter.TYPE_REFERENCE),
      new AttributesDescriptor("Identifiers//Import Path", PrismioSyntaxHighlighter.IMPORT_PATH),

      // Operators and Separators
      new AttributesDescriptor(
          "Operators and Separators//Operator", PrismioSyntaxHighlighter.OPERATOR),
      new AttributesDescriptor("Operators and Separators//Separator (Braces, Brackets)",
          PrismioSyntaxHighlighter.SEPARATOR),
      new AttributesDescriptor("Operators and Separators//Comma", PrismioSyntaxHighlighter.COMMA),
      new AttributesDescriptor(
          "Operators and Separators//Semicolon", PrismioSyntaxHighlighter.SEMICOLON),
      new AttributesDescriptor("Operators and Separators//Dot", PrismioSyntaxHighlighter.DOT),

      // Comments
      new AttributesDescriptor("Comments//Line Comment", PrismioSyntaxHighlighter.LINE_COMMENT),
      new AttributesDescriptor("Comments//Block Comment", PrismioSyntaxHighlighter.BLOCK_COMMENT),
      new AttributesDescriptor(
          "Comments//Documentation comment", PrismioSyntaxHighlighter.DOC_COMMENT),

      // Special
      new AttributesDescriptor("Special//Bad Character", PrismioSyntaxHighlighter.BAD_CHARACTER)};

  @Nullable
  @Override
  public Icon getIcon() {
    return PrismioIcons.FILE;
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter() {
    return new PrismioSyntaxHighlighter();
  }

  @NotNull
  @Override
  public String getDemoText() {
    return """
            // Every construct below is accepted by the compiler in this repository.
            /* Block comments nest, /* like this one */ which is what makes it safe
               to comment out a region that already contains a comment. */

            import <importPath>std.io</importPath>
            import std.string
            import std.vec

            // The FFI contract names are contextual keywords, not reserved words.
            extern fn read_file(path: String borrow) -> String produce(free)

            struct <structName>Point</structName> {
                <field>x</field>: Int,
                y: Float
            }

            enum <enumName>Direction</enumName> {
                <enumVariant>North</enumVariant>,
                South
            }

            trait <traitName>Drawable</traitName> {
                let MAX: Int
                type Output
                fn draw(self) -> String
            }

            struct Box<T> {
                value: T
            }

            impl<T: Drawable> Box<T> where T: Copy {
                private fn render(self, index: Usize) -> String {
                    return self.value.draw()
                }
            }

            let <constant>MAX_SIZE</constant> = 1000

            public fn <functionDeclaration>classify</functionDeclaration>(<parameter>value</parameter>: Int) -> Bool {
                let <localVariable>doubled</localVariable> = value * 2
                let mut total = 0

                // `and` and `or` are the logical operators; `..` is a range.
                for index in 0..doubled {
                    if (index > 3 and index != 7) {
                        total += index
                    }
                }

                match (value) {
                    0 => { return false }
                    _ => { return total >= 0 }
                }
            }

            fn main() -> Int {
                let mut items: Vec<Int> = [3, 1, 2]
                let text = "escapes: \\n and \\t"
                let letter = 'p'

                region arena pin(4096) {
                    let point = Point { x: 1, y: 2.5 }
                    <functionCall>println</functionCall>(point.x)
                    items.<methodCall>push</methodCall>(4)
                }

                spawn classify(41)
                return 0
            }
            """;
  }

  @Nullable
  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return Map.ofEntries(
        Map.entry("functionDeclaration", PrismioSyntaxHighlighter.FUNCTION_DECLARATION),
        Map.entry("functionCall", PrismioSyntaxHighlighter.FUNCTION_CALL),
        Map.entry("methodCall", PrismioSyntaxHighlighter.METHOD_CALL),
        Map.entry("parameter", PrismioSyntaxHighlighter.PARAMETER),
        Map.entry("localVariable", PrismioSyntaxHighlighter.LOCAL_VARIABLE),
        Map.entry("constant", PrismioSyntaxHighlighter.CONSTANT),
        Map.entry("field", PrismioSyntaxHighlighter.FIELD),
        Map.entry("structName", PrismioSyntaxHighlighter.STRUCT_NAME),
        Map.entry("enumName", PrismioSyntaxHighlighter.ENUM_NAME),
        Map.entry("traitName", PrismioSyntaxHighlighter.TRAIT_NAME),
        Map.entry("enumVariant", PrismioSyntaxHighlighter.ENUM_VARIANT),
        Map.entry("typeReference", PrismioSyntaxHighlighter.TYPE_REFERENCE),
        Map.entry("importPath", PrismioSyntaxHighlighter.IMPORT_PATH));
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Prismio";
  }
}
