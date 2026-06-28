package com.medibook.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class MedicalCheckApiServiceTest {

    private MedicalCheckApiService buildService(String apiUrl, String apiKey) {
        MedicalCheckApiService service = new MedicalCheckApiService();
        ReflectionTestUtils.setField(service, "apiBaseUrl", apiUrl);
        ReflectionTestUtils.setField(service, "apiKey", apiKey);
        return service;
    }

    @Test
    void init_throwsWhenApiKeyMissing() {
        MedicalCheckApiService service = buildService("https://example.com", null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateConfiguration);
        assertTrue(ex.getMessage().toLowerCase().contains("medical.check.api.key"));
    }

    @Test
    void init_throwsWhenApiKeyBlank() {
        MedicalCheckApiService service = buildService("https://example.com", "   ");

        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }

    @Test
    void init_succeedsWhenApiKeyPresent() {
        MedicalCheckApiService service = buildService("https://example.com", "some-key");

        assertDoesNotThrow(service::validateConfiguration);
    }

    @Test
    void applicationProperties_hasNoHardcodedApiKeyDefault() throws Exception {
        Path props = locate("src/main/resources/application.properties");
        String content = Files.readString(props);

        // No committed secret value
        assertFalse(content.contains("mk_live_"),
                "application.properties must not contain a hardcoded medical.check.api.key value");
        // The property must reference an env placeholder WITHOUT an inline default value
        // i.e. ${MEDICAL_CHECK_API_KEY} not ${MEDICAL_CHECK_API_KEY:...}
        assertFalse(content.matches("(?s).*medical\\.check\\.api\\.key=\\$\\{[A-Z_]+:[^}]+}.*"),
                "medical.check.api.key must not declare an inline default value");
    }

    @Test
    void value_annotation_hasNoEmptyDefault() throws Exception {
        Path source = locate("src/main/java/com/medibook/api/service/MedicalCheckApiService.java");
        String content = Files.readString(source);

        assertFalse(content.contains("${medical.check.api.key:}"),
                "@Value for the api key must not provide an empty default");
    }

    private Path locate(String relative) {
        Path direct = Paths.get(relative);
        if (Files.exists(direct)) {
            return direct;
        }
        return Paths.get("2025-aguinaldo-back").resolve(relative);
    }
}
