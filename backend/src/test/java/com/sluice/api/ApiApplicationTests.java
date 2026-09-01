package com.sluice.api;

import com.sluice.api.support.SluiceIntegrationTest;
import com.sluice.api.worker.JobWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SluiceIntegrationTest
@TestPropertySource(properties = "sluice.runtime.mode=api")
class ApiApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoadsWithDisposableDatabase() throws Exception {
		try (var connection = dataSource.getConnection()) {
			assertTrue(connection.getMetaData().getURL().contains("/sluice_test"));
		}
	}

	@Test
	void apiRuntimeDoesNotCreateQueueWorker() {
		assertFalse(applicationContext.getBeansOfType(JobWorker.class).size() > 0);
	}

}
