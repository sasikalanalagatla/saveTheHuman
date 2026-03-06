package com.sasikala.SaveTheHuman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class SaveTheHumanApplication {

	public static void main(String[] args) {
		SpringApplication.run(SaveTheHumanApplication.class, args);
	}

}
