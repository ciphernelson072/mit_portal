package com.sis.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthControllerTest {

    @Test
    void healthEndpointHasCorrectApiRouteAndPayload() {
        RequestMapping requestMapping = HealthController.class.getAnnotation(RequestMapping.class);
        assertNotNull(requestMapping);
        assertEquals("/api/${app.api.version:v1}", requestMapping.value()[0]);

        HealthController controller = new HealthController();
        ReflectionTestUtils.setField(controller, "activeProfile", "dev");
        ReflectionTestUtils.setField(controller, "apiVersion", "v1");

        Map<String, Object> response = controller.health();

        assertEquals("ok", response.get("status"));
        assertEquals("sis", response.get("service"));
        assertEquals("v1", response.get("apiVersion"));
    }
}
