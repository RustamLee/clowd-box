package com.example.cloud_box;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@SpringBootApplication
@EnableRedisHttpSession
public class CloudBoxApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudBoxApplication.class, args);
	}

	@Bean
	public CommandLineRunner checkSessionRepo(SessionRepository<?> repo) {
		return args -> System.out.println("SessionRepository class: " + repo.getClass().getName());
	}

	@Bean
	public CommandLineRunner checkRedis(RedisConnectionFactory factory) {
		return args -> {
			System.out.println("RedisConnectionFactory class: " + factory.getClass().getName());
		};
	}

}
