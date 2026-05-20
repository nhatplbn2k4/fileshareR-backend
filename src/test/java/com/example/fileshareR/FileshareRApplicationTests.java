package com.example.fileshareR;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring context smoke test — requires running PostgreSQL + Firebase + full
 * external infra. Disabled by default for fast unit-test CI; enable manually
 * when smoke-checking deployment.
 */
@SpringBootTest
@Disabled("Requires full Spring context with PostgreSQL/Firebase — out of scope for unit-test JaCoCo run")
class FileshareRApplicationTests {

	@Test
	void contextLoads() {
	}

}
