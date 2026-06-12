package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.util.HtmlEncoder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HtmlEncoder Unit Tests")
class HtmlEncoderUnitTest {

    // ==================== encode(String) Tests ====================

    @Nested
    @DisplayName("encode(String) - Null and Empty Input")
    class NullAndEmptyInputTests {

        @Test
        @DisplayName("Should return null when input is null")
        void encode_WhenInputIsNull_ReturnsNull() {
            // When
            String result = HtmlEncoder.encode((String) null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return empty string when input is empty")
        void encode_WhenInputIsEmpty_ReturnsEmpty() {
            // When
            String result = HtmlEncoder.encode("");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return whitespace unchanged")
        void encode_WhenInputIsWhitespace_ReturnsWhitespace() {
            // When
            String result = HtmlEncoder.encode("   ");

            // Then
            assertThat(result).isEqualTo("   ");
        }
    }

    @Nested
    @DisplayName("encode(String) - Individual Special Characters")
    class IndividualSpecialCharacterTests {

        @Test
        @DisplayName("Should encode ampersand to &amp;")
        void encode_WhenInputContainsAmpersand_EncodesCorrectly() {
            // When
            String result = HtmlEncoder.encode("Tom & Jerry");

            // Then
            assertThat(result).isEqualTo("Tom &amp; Jerry");
        }

        @Test
        @DisplayName("Should encode less than to &lt;")
        void encode_WhenInputContainsLessThan_EncodesCorrectly() {
            // When
            String result = HtmlEncoder.encode("a < b");

            // Then
            assertThat(result).isEqualTo("a &lt; b");
        }

        @Test
        @DisplayName("Should encode greater than to &gt;")
        void encode_WhenInputContainsGreaterThan_EncodesCorrectly() {
            // When
            String result = HtmlEncoder.encode("a > b");

            // Then
            assertThat(result).isEqualTo("a &gt; b");
        }

        @Test
        @DisplayName("Should encode double quote to &quot;")
        void encode_WhenInputContainsDoubleQuote_EncodesCorrectly() {
            // When
            String result = HtmlEncoder.encode("She said \"hello\"");

            // Then
            assertThat(result).isEqualTo("She said &quot;hello&quot;");
        }

        @Test
        @DisplayName("Should encode single quote to &#x27;")
        void encode_WhenInputContainsSingleQuote_EncodesCorrectly() {
            // When
            String result = HtmlEncoder.encode("It's working");

            // Then
            assertThat(result).isEqualTo("It&#x27;s working");
        }

        @Test
        @DisplayName("Should encode forward slash to &#x2F;")
        void encode_WhenInputContainsForwardSlash_EncodesCorrectly() {
            // When
            String result = HtmlEncoder.encode("path/to/file");

            // Then
            assertThat(result).isEqualTo("path&#x2F;to&#x2F;file");
        }
    }

    @Nested
    @DisplayName("encode(String) - XSS Attack Patterns")
    class XssAttackPatternTests {

        @Test
        @DisplayName("Should encode basic script tag XSS attack")
        void encode_WhenInputContainsScriptTag_EncodesCorrectly() {
            // Given
            String xssAttack = "<script>alert('XSS')</script>";

            // When
            String result = HtmlEncoder.encode(xssAttack);

            // Then
            assertThat(result).isEqualTo("&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;&#x2F;script&gt;");
            assertThat(result).doesNotContain("<script>");
            assertThat(result).doesNotContain("</script>");
        }

        @Test
        @DisplayName("Should encode img onerror XSS attack")
        void encode_WhenInputContainsImgOnerror_EncodesCorrectly() {
            // Given
            String xssAttack = "<img src=\"x\" onerror=\"alert('XSS')\">";

            // When
            String result = HtmlEncoder.encode(xssAttack);

            // Then
            assertThat(result).doesNotContain("<img");
            assertThat(result).contains("&lt;img");
            assertThat(result).contains("&quot;");
            // Note: HtmlEncoder encodes HTML special characters, not attribute names
            // The encoding prevents browser from parsing this as an actual HTML tag
        }

        @Test
        @DisplayName("Should encode event handler XSS attack")
        void encode_WhenInputContainsEventHandler_EncodesCorrectly() {
            // Given
            String xssAttack = "<div onmouseover=\"alert('XSS')\">hover me</div>";

            // When
            String result = HtmlEncoder.encode(xssAttack);

            // Then
            assertThat(result).doesNotContain("<div");
            assertThat(result).contains("&lt;div");
        }

        @Test
        @DisplayName("Should encode JavaScript protocol XSS attack")
        void encode_WhenInputContainsJavascriptProtocol_EncodesCorrectly() {
            // Given
            String xssAttack = "<a href=\"javascript:alert('XSS')\">click</a>";

            // When
            String result = HtmlEncoder.encode(xssAttack);

            // Then
            assertThat(result).doesNotContain("<a href");
            assertThat(result).contains("&lt;a href");
        }
    }

    @Nested
    @DisplayName("encode(String) - Combined Special Characters")
    class CombinedSpecialCharacterTests {

        @Test
        @DisplayName("Should encode all special characters in one string")
        void encode_WhenInputContainsAllSpecialChars_EncodesAllCorrectly() {
            // Given
            String input = "&<>\"'/";

            // When
            String result = HtmlEncoder.encode(input);

            // Then
            assertThat(result).isEqualTo("&amp;&lt;&gt;&quot;&#x27;&#x2F;");
        }

        @Test
        @DisplayName("Should encode HTML comment syntax")
        void encode_WhenInputContainsHtmlComment_EncodesCorrectly() {
            // Given
            String input = "<!-- comment -->";

            // When
            String result = HtmlEncoder.encode(input);

            // Then
            assertThat(result).isEqualTo("&lt;!-- comment --&gt;");
        }
    }

    @Nested
    @DisplayName("encode(String) - Unicode and Safe Characters")
    class UnicodeAndSafeCharacterTests {

        @Test
        @DisplayName("Should preserve Unicode characters unchanged")
        void encode_WhenInputContainsUnicode_PreservesUnicode() {
            // Given
            String unicodeInput = "Hello, Welt, Villag, Swiat";

            // When
            String result = HtmlEncoder.encode(unicodeInput);

            // Then
            assertThat(result).isEqualTo(unicodeInput);
        }

        @Test
        @DisplayName("Should preserve emojis unchanged")
        void encode_WhenInputContainsEmojis_PreservesEmojis() {
            // Given
            String emojiInput = "Hello World!";

            // When
            String result = HtmlEncoder.encode(emojiInput);

            // Then
            assertThat(result).isEqualTo(emojiInput);
        }

        @Test
        @DisplayName("Should preserve regular alphanumeric text unchanged")
        void encode_WhenInputIsAlphanumeric_ReturnsUnchanged() {
            // Given
            String input = "Hello World 123";

            // When
            String result = HtmlEncoder.encode(input);

            // Then
            assertThat(result).isEqualTo(input);
        }
    }

    @Nested
    @DisplayName("encode(String) - Double Encoding Behavior")
    class DoubleEncodingTests {

        @Test
        @DisplayName("Should double encode already encoded ampersand")
        void encode_WhenInputAlreadyContainsEncodedAmpersand_DoubleEncodes() {
            // Given - already encoded string
            String alreadyEncoded = "&amp;";

            // When
            String result = HtmlEncoder.encode(alreadyEncoded);

            // Then - ampersand in &amp; gets encoded again
            assertThat(result).isEqualTo("&amp;amp;");
        }

        @Test
        @DisplayName("Should double encode already encoded less than")
        void encode_WhenInputAlreadyContainsEncodedLessThan_DoubleEncodes() {
            // Given
            String alreadyEncoded = "&lt;script&gt;";

            // When
            String result = HtmlEncoder.encode(alreadyEncoded);

            // Then
            assertThat(result).isEqualTo("&amp;lt;script&amp;gt;");
        }
    }

    // ==================== encode(Object) Tests ====================

    @Nested
    @DisplayName("encode(Object) - Object Encoding")
    class ObjectEncodingTests {

        @Test
        @DisplayName("Should return null when object is null")
        void encode_WhenObjectIsNull_ReturnsNull() {
            // When
            String result = HtmlEncoder.encode((Object) null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should encode object toString() containing special characters")
        void encode_WhenObjectToStringContainsSpecialChars_EncodesCorrectly() {
            // Given
            Object objectWithSpecialChars = new SpecialCharObject();

            // When
            String result = HtmlEncoder.encode(objectWithSpecialChars);

            // Then
            assertThat(result).isEqualTo("&lt;malicious&gt;");
        }

        // Named inner class to avoid anonymous class serialization issues
        static class SpecialCharObject {
            @Override
            public String toString() {
                return "<malicious>";
            }
        }

        @Test
        @DisplayName("Should encode Integer object correctly")
        void encode_WhenObjectIsInteger_ReturnsNumberAsString() {
            // Given
            Integer number = 42;

            // When
            String result = HtmlEncoder.encode(number);

            // Then
            assertThat(result).isEqualTo("42");
        }

        @Test
        @DisplayName("Should encode StringBuilder with special characters")
        void encode_WhenObjectIsStringBuilder_EncodesContent() {
            // Given
            StringBuilder sb = new StringBuilder("Hello <World>");

            // When
            String result = HtmlEncoder.encode(sb);

            // Then
            assertThat(result).isEqualTo("Hello &lt;World&gt;");
        }
    }

    // ==================== Parameterized Tests ====================

    @Nested
    @DisplayName("Parameterized Encoding Tests")
    class ParameterizedEncodingTests {

        @ParameterizedTest(name = "Character ''{0}'' should be encoded to ''{1}''")
        @MethodSource("specialCharacterProvider")
        @DisplayName("Should encode individual special characters correctly")
        void encode_SpecialCharacters_EncodedCorrectly(String input, String expected) {
            // When
            String result = HtmlEncoder.encode(input);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> specialCharacterProvider() {
            return Stream.of(
                Arguments.of("&", "&amp;"),
                Arguments.of("<", "&lt;"),
                Arguments.of(">", "&gt;"),
                Arguments.of("\"", "&quot;"),
                Arguments.of("'", "&#x27;"),
                Arguments.of("/", "&#x2F;")
            );
        }

        @ParameterizedTest(name = "Input ''{0}'' should be encoded to ''{1}''")
        @MethodSource("xssPatternProvider")
        @DisplayName("Should properly encode various XSS attack patterns")
        void encode_XssPatterns_EncodedCorrectly(String input, String expected) {
            // When
            String result = HtmlEncoder.encode(input);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> xssPatternProvider() {
            return Stream.of(
                Arguments.of("<script>", "&lt;script&gt;"),
                Arguments.of("</script>", "&lt;&#x2F;script&gt;"),
                Arguments.of("<img/>", "&lt;img&#x2F;&gt;"),
                Arguments.of("onclick=\"\"", "onclick=&quot;&quot;"),
                Arguments.of("'onclick'", "&#x27;onclick&#x27;")
            );
        }
    }
}
