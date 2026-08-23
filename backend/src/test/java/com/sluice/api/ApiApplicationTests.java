package com.sluice.api;

import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SluiceIntegrationTest
class ApiApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoadsWithDisposableDatabase() throws Exception {
		try (var connection = dataSource.getConnection()) {
			assertTrue(connection.getMetaData().getURL().contains("/sluice_test"));
		}
	}

}
