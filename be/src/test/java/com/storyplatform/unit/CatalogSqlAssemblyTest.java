package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the text-block SQL in PublicCatalogService against the concatenation
 * bug that produced "IS NOT NULLAND (...)" in production: a text block strips
 * its trailing newline, so appending one straight onto a filter fuses the last
 * word of the filter with the first word of the block.
 */
class CatalogSqlAssemblyTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/storyplatform/catalog/application/PublicCatalogService.java");

    /**
     * Matches `SOMETHING + """` whose very next line already carries SQL. The
     * opening delimiter's own line break is not part of the value, so that
     * first line lands flush against whatever preceded the block.
     */
    private static final Pattern FUSED_CONCAT = Pattern.compile(
            "\\+\\s*\"\"\"[ \\t]*\\r?\\n[ \\t]*(AND|OR|WHERE|ORDER|LIMIT|JOIN|GROUP|HAVING)\\b",
            Pattern.CASE_INSENSITIVE);

    private String source() throws IOException {
        return Files.readString(SOURCE, StandardCharsets.UTF_8);
    }

    @Test
    void noTextBlockIsAppendedDirectlyOntoASqlKeyword() throws IOException {
        assertThat(FUSED_CONCAT.matcher(source()).find())
                .as("A text block starting with a SQL keyword must be preceded by a blank "
                        + "line, otherwise it fuses onto the previous token (e.g. NULLAND)")
                .isFalse();
    }

    @Test
    void assembledFilterKeepsWordsSeparated() {
        // Reproduces the exact assembly the service performs. The blank first
        // line is what turns into the separating newline.
        String filter = "s.status = 'PUBLISHED' AND s.published_at IS NOT NULL";
        String appended = filter + """

                AND (s.title LIKE :query)
                """;

        assertThat(appended).doesNotContain("NULLAND");
        assertThat(appended).contains("NULL\nAND (");
    }

    @Test
    void demonstratesTheBugTheBlankLinePrevents() {
        String filter = "s.published_at IS NOT NULL";
        // No blank line: this is the shape that broke /search in production.
        String fused = filter + """
                AND (s.title LIKE :query)
                """;

        assertThat(fused).contains("NULLAND");
    }
}
