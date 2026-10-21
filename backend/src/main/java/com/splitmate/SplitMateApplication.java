package com.splitmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SplitMateApplication {

	public static void main(String[] args) {
		SpringApplication.run(SplitMateApplication.class, args);
	}

}
