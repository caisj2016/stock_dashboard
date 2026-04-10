package com.caisj.stockdashboard.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class StockDashboardBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockDashboardBackendApplication.class, args);
    }
}
