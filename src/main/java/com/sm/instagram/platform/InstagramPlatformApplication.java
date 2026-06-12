package com.sm.instagram.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableAspectJAutoProxy
public class InstagramPlatformApplication {

	public static void main(String[] args) {
		log.info("=========================================");
		log.info("🚀 Starting CheckItOut Backend Application");
		log.info("=========================================");
		
		SpringApplication.run(InstagramPlatformApplication.class, args);
		
		log.info("=========================================");
		log.info("✅ CheckItOut Backend Started Successfully");
		log.info("=========================================");
	}
}
