package com.sentinel.aml;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SentinelAmlApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context wires up correctly with H2 + all entities
    }
}
