package com.example.simplethumb;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@MapperScan("com.example.simplethumb.mapper")
@SpringBootApplication
public class SimpleThumbApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleThumbApplication.class, args);
    }

}
