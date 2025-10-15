package com.ismile.core.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "grpc.server.port=0")
@SpringBootTest
class AuthApplicationTests {

	@Test
	void contextLoads() {
	}

}
