package com.medibook.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MedicalCheckApiServiceTest {

    private MedicalCheckApiService buildService(String apiUrl, String apiKey) {
        return buildService(apiUrl, apiKey, mock(RestTemplate.class));
    }

    private MedicalCheckApiService buildService(String apiUrl, String apiKey, RestTemplate restTemplate) {
        MedicalCheckApiService service = new MedicalCheckApiService(restTemplate);
        ReflectionTestUtils.setField(service, "apiBaseUrl", apiUrl);
        ReflectionTestUtils.setField(service, "apiKey", apiKey);
        return service;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> okResponse(Map<String, Object> body) {
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) ResponseEntity.status(HttpStatus.OK).body(body);
    }

    @SuppressWarnings("unchecked")
    private RestTemplate restTemplateReturning(Map<String, Object> body) {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.exchange(any(String.class), eq(HttpMethod.POST), any(), any(Class.class)))
                .thenReturn(okResponse(body));
        return rt;
    }

    @Test
    void isUser_returnsFalse_whenIsUserFieldAbsent() {
        // Malformed response: 200 OK but no "isUser" key -> fail-closed (false)
        Map<String, Object> body = new HashMap<>();
        body.put("somethingElse", "value");
        MedicalCheckApiService service = buildService("https://example.com", "key", restTemplateReturning(body));

        assertFalse(service.isUser("patient@example.com"));
    }

    @Test
    void isUser_returnsFalse_whenIsUserFieldNotBoolean() {
        // Malformed response: "isUser" present but not a boolean -> fail-closed (false)
        Map<String, Object> body = new HashMap<>();
        body.put("isUser", "yes");
        MedicalCheckApiService service = buildService("https://example.com", "key", restTemplateReturning(body));

        assertFalse(service.isUser("patient@example.com"));
    }

    @Test
    void isUser_returnsTrue_whenIsUserTrue() {
        Map<String, Object> body = new HashMap<>();
        body.put("isUser", Boolean.TRUE);
        MedicalCheckApiService service = buildService("https://example.com", "key", restTemplateReturning(body));

        assertTrue(service.isUser("patient@example.com"));
    }

    @Test
    void isUser_returnsFalse_whenIsUserFalse() {
        Map<String, Object> body = new HashMap<>();
        body.put("isUser", Boolean.FALSE);
        MedicalCheckApiService service = buildService("https://example.com", "key", restTemplateReturning(body));

        assertFalse(service.isUser("patient@example.com"));
    }

    @Test
    void constructor_usesInjectedRestTemplate() {
        RestTemplate injected = mock(RestTemplate.class);
        MedicalCheckApiService service = new MedicalCheckApiService(injected);

        assertSame(injected, ReflectionTestUtils.getField(service, "restTemplate"));
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
