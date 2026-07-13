package com.medibook.api.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that the committed application.properties contains NO
 * weak/literal secret defaults. Secrets must be referenced via env/placeholder
 * only (no {@code :default}) so a missing value fails fast instead of silently
 * falling back to a committed credential. Also asserts the inert in-memory
 * Spring Security user block has been removed entirely.
 */
class ApplicationSecretsHardeningTest {

    private Properties loadRawProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            assertNotNull(in, "application.properties must be on the classpath");
            props.load(in);
        }
        return props;
    }

    @Test
    void springSecurityInMemoryUserBlockIsRemoved() throws IOException {
        Properties props = loadRawProperties();
        assertFalse(props.containsKey("spring.security.user.name"),
                "spring.security.user.name must be removed (inert in-memory user)");
        assertFalse(props.containsKey("spring.security.user.password"),
                "spring.security.user.password must be removed");
        assertFalse(props.containsKey("spring.security.user.roles"),
                "spring.security.user.roles must be removed");
    }

    @Test
    void dbPasswordHasNoCommittedDefault() throws IOException {
        Properties props = loadRawProperties();
        String value = props.getProperty("spring.datasource.password");
        assertNotNull(value, "spring.datasource.password must be declared");
        assertNoLiteralDefault("spring.datasource.password", value);
        assertFalse(value.contains("MediBook"),
                "spring.datasource.password must not contain the committed 'MediBook' default");
    }

    @Test
    void supabaseS3KeysHaveNoCommittedDefault() throws IOException {
        Properties props = loadRawProperties();
        String accessKey = props.getProperty("supabase.s3.access-key");
        String secretKey = props.getProperty("supabase.s3.secret-key");
        assertNoLiteralDefault("supabase.s3.access-key", accessKey);
        assertNoLiteralDefault("supabase.s3.secret-key", secretKey);
        assertFalse(accessKey.contains("test-access-key"),
                "supabase.s3.access-key must not contain a committed 'test-access-key' default");
        assertFalse(secretKey.contains("test-secret-key"),
                "supabase.s3.secret-key must not contain a committed 'test-secret-key' default");
    }

    @Test
    void googleAppsScriptTokenHasNoCommittedDefault() throws IOException {
        Properties props = loadRawProperties();
        String token = props.getProperty("google.apps.script.token");
        assertNoLiteralDefault("google.apps.script.token", token);
    }

    @Test
    void gymcloudApiKeysHaveNoCommittedDefault() throws IOException {
        Properties props = loadRawProperties();
        String keys = props.getProperty("gymcloud.api.keys");
        assertNoLiteralDefault("gymcloud.api.keys", keys);
    }

    /**
     * A hardened property must be a pure placeholder reference {@code ${VAR}} with
     * no {@code :default} fallback. Pure pass-through defaults to other
     * placeholders (none here) are not allowed for secrets.
     */
    private void assertNoLiteralDefault(String key, String value) {
        assertNotNull(value, key + " must be declared in application.properties");
        assertTrue(value.startsWith("${") && value.endsWith("}"),
                key + " must be a placeholder reference (got: " + value + ")");
        String inside = value.substring(2, value.length() - 1);
        assertFalse(inside.contains(":"),
                key + " must not provide a literal default value (got: " + value + ")");
    }
}
