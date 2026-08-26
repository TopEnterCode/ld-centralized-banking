package com.example.banking.fraud;

import com.example.banking.support.DtmClientConfiguration;
import com.example.banking.support.WebSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({DtmClientConfiguration.class, WebSupportConfiguration.class})
public class FraudApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudApplication.class, args);
    }
}
