package io.prismio;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.prismio.ums.UmsLexer;
import io.prismio.ums.UmsTypes;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;

/** The UMS lexer against {@code ums/parser/lexer.psm}. */
public class UmsLexerTest extends TestCase {

  private static List<IElementType> types(String source) {
    UmsLexer lexer = new UmsLexer();
    lexer.start(source, 0, source.length(), 0);
    List<IElementType> out = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() != TokenType.WHITE_SPACE) {
        out.add(lexer.getTokenType());
      }
      lexer.advance();
    }
    return out;
  }

  public void testBothCommentSpellings() {
    assertEquals(List.of(UmsTypes.COMMENT), types("// a comment"));
    assertEquals(List.of(UmsTypes.COMMENT), types("# also a comment"));
  }

  public void testManifestNamesAreOrdinaryIdentifiers() {
    // UMS has no keywords: `project` is an identifier the semantic layer
    // interprets, which is what lets a new block parse before the model knows it.
    assertEquals(List.of(UmsTypes.IDENTIFIER, UmsTypes.LEFT_BRACE, UmsTypes.RIGHT_BRACE),
        types("project { }"));
  }

  public void testADotJoinsAnEnumVariantIntoOneIdentifier() {
    // `lexIdentifier` continues over a `.` that a letter follows: `Channel.BETA` is the name of a variant.
    assertEquals(List.of(UmsTypes.IDENTIFIER), types("Channel.BETA"));
    assertEquals(List.of(UmsTypes.IDENTIFIER, UmsTypes.EQUAL, UmsTypes.IDENTIFIER),
        types("CHANNEL = Channel.BETA"));
    // A dot with no letter after it is not part of the name.
    assertEquals(List.of(UmsTypes.IDENTIFIER, TokenType.BAD_CHARACTER), types("Channel."));
    assertEquals(List.of(UmsTypes.IDENTIFIER, TokenType.BAD_CHARACTER, UmsTypes.INTEGER), types("a.1"));
  }

  public void testConstAndEnumAreIdentifiersToTheLexer() {
    assertEquals(List.of(UmsTypes.IDENTIFIER, UmsTypes.IDENTIFIER, UmsTypes.EQUAL, UmsTypes.INTEGER),
        types("const MAJOR = 3"));
    assertEquals(List.of(UmsTypes.IDENTIFIER, UmsTypes.IDENTIFIER, UmsTypes.LEFT_BRACE, UmsTypes.IDENTIFIER,
        UmsTypes.RIGHT_BRACE), types("enum Channel { STABLE }"));
  }

  public void testAnInterpolatedStringIsOneStringToken() {
    assertEquals(List.of(UmsTypes.STRING), types("\"${MAJOR}.2.0 costs $$5\""));
  }

  public void testBooleansAreValuesRatherThanNames() {
    assertEquals(List.of(UmsTypes.BOOLEAN), types("true"));
    assertEquals(List.of(UmsTypes.BOOLEAN), types("false"));
    assertEquals(List.of(UmsTypes.IDENTIFIER), types("truthy"));
  }

  public void testHyphenContinuesAnIdentifierButDoesNotStartOne() {
    // `lexIdentifier` accepts alnum and `-`; the dispatch that reaches it tests
    // for an alphabetic first character.
    assertEquals(List.of(UmsTypes.IDENTIFIER), types("testImplementation"));
    assertEquals(List.of(UmsTypes.IDENTIFIER), types("some-name"));
    assertEquals(List.of(TokenType.BAD_CHARACTER, UmsTypes.INTEGER), types("-1"));
  }

  public void testStringsCarryEscapesAndStopAtTheLineEnd() {
    assertEquals(List.of(UmsTypes.STRING), types("\"a \\\" quote\""));
    assertEquals(List.of(UmsTypes.STRING, UmsTypes.IDENTIFIER), types("\"unterminated\nname"));
  }

  public void testAWholeManifest() {
    assertEquals(
        List.of(UmsTypes.COMMENT,
            UmsTypes.IDENTIFIER, UmsTypes.LEFT_BRACE,
            UmsTypes.IDENTIFIER, UmsTypes.EQUAL, UmsTypes.STRING,
            UmsTypes.IDENTIFIER, UmsTypes.EQUAL, UmsTypes.LEFT_BRACKET, UmsTypes.STRING,
            UmsTypes.COMMA, UmsTypes.RIGHT_BRACKET,
            UmsTypes.RIGHT_BRACE,
            UmsTypes.IDENTIFIER, UmsTypes.LEFT_BRACE,
            UmsTypes.IDENTIFIER, UmsTypes.LEFT_PAREN, UmsTypes.STRING, UmsTypes.RIGHT_PAREN,
            UmsTypes.LEFT_BRACE,
            UmsTypes.IDENTIFIER, UmsTypes.EQUAL, UmsTypes.STRING,
            UmsTypes.RIGHT_BRACE,
            UmsTypes.RIGHT_BRACE),
        types("""
            # a manifest
            project {
                name = "app"
                authors = ["Ada",]
            }
            targets {
                executable("app") {
                    entry = "src/main.psm"
                }
            }
            """));
  }
}
