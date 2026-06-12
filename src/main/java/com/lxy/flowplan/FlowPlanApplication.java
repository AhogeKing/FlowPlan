package com.lxy.flowplan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.lxy.flowplan.mapper")
public class FlowPlanApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowPlanApplication.class, args);
    }
}
