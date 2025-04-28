package com.example.simplethumb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.example.simplethumb.mapper")
@SpringBootApplication
@EnableScheduling
public class SimpleThumbApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleThumbApplication.class, args);
    }

}
