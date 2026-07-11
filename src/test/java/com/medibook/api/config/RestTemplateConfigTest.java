package com.medibook.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class RestTemplateConfigTest {

    @Test
    void restTemplate_hasConnectAndReadTimeouts() {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate();

        assertNotNull(restTemplate);

        ClientHttpRequestFactory factory = (ClientHttpRequestFactory)
                ReflectionTestUtils.getField(restTemplate, "requestFactory");
        assertNotNull(factory, "RestTemplate must have a request factory configured");
        assertInstanceOf(SimpleClientHttpRequestFactory.class, factory,
                "Expected a SimpleClientHttpRequestFactory with explicit timeouts");

        int connectTimeout = (int) ReflectionTestUtils.getField(factory, "connectTimeout");
        int readTimeout = (int) ReflectionTestUtils.getField(factory, "readTimeout");

        assertTrue(connectTimeout > 0, "Connect timeout must be set to a positive value");
        assertTrue(readTimeout > 0, "Read timeout must be set to a positive value");
    }
}
