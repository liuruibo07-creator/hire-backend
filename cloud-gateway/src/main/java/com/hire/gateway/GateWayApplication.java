package com.hire.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GateWayApplication {
    public static void main(String[] args) {
        com.hire.common.constant.JwtConstant.secretKey();
        SpringApplication.run(GateWayApplication.class, args);
    }
}
