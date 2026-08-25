package com.sluice.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class ApiApplication {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	tools.jackson.databind.JacksonModule legacyJsonNodeHttpModule() {
		tools.jackson.databind.module.SimpleModule module =
				new tools.jackson.databind.module.SimpleModule("sluice-jackson2-json-node-bridge");
		module.addSerializer(com.fasterxml.jackson.databind.JsonNode.class,
				new tools.jackson.databind.ValueSerializer<com.fasterxml.jackson.databind.JsonNode>() {
					@Override
					public void serialize(com.fasterxml.jackson.databind.JsonNode value,
							tools.jackson.core.JsonGenerator generator,
							tools.jackson.databind.SerializationContext context)
							throws tools.jackson.core.JacksonException {
						generator.writeRawValue(value.toString());
					}
				});
		return module;
	}

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
