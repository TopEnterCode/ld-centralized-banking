package com.example.banking.gateway;

import com.example.banking.support.DtmClientConfiguration;
import com.example.banking.support.WebSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({DtmClientConfiguration.class, WebSupportConfiguration.class})
public class WebGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebGatewayApplication.class, args);
    }
}
