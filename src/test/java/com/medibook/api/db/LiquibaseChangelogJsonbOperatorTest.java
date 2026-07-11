package com.medibook.api.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard against the class of bug that broke 0013 on real Postgres:
 * the jsonb existence operators ({@code ?}, {@code ?|}, {@code ?&}) inside a
 * Liquibase {@code <sql>} block are misparsed by the JDBC driver as bind
 * parameter placeholders ("$1"), which throws a syntax error on Postgres.
 *
 * H2 tests skip Liquibase ({@code spring.liquibase.enabled=false}), so this
 * can NOT be caught by booting the context under test. This static check reads
 * the changelog XML files and fails if any bare jsonb {@code ?}-family operator
 * is present. Use the function form instead:
 *   {@code col ? 'key'}   -> {@code jsonb_exists(col, 'key')}
 *   {@code col ?| array}  -> {@code jsonb_exists_any(col, array[...])}
 *   {@code col ?& array}  -> {@code jsonb_exists_all(col, array[...])}
 */
class LiquibaseChangelogJsonbOperatorTest {

    private static final Path CHANGELOG_DIR =
            Paths.get("src", "main", "resources", "db", "changelog");

    // A whitespace-delimited '?' optionally followed by '|' or '&' — i.e. the
    // jsonb existence operator family used as a standalone SQL operator.
    private static final Pattern BARE_JSONB_QUESTION_OP =
            Pattern.compile("\\s\\?[|&]?\\s");

    @Test
    @DisplayName("No changelog uses a bare jsonb '?'-family operator (breaks JDBC on Postgres)")
    void changelogsHaveNoBareJsonbQuestionOperator() throws IOException {
        try (Stream<Path> paths = Files.walk(CHANGELOG_DIR)) {
            List<Path> offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .filter(this::containsBareJsonbQuestionOperator)
                    .collect(Collectors.toList());

            assertTrue(offenders.isEmpty(),
                    "Liquibase changelog(s) use a bare jsonb '?' operator that the JDBC "
                            + "driver misparses as a bind parameter on Postgres. Replace with "
                            + "jsonb_exists / jsonb_exists_any / jsonb_exists_all. Offending files: "
                            + offenders);
        }
    }

    private boolean containsBareJsonbQuestionOperator(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return BARE_JSONB_QUESTION_OP.matcher(content).find();
        } catch (IOException e) {
            throw new RuntimeException("Unable to read changelog file: " + file, e);
        }
    }
}
