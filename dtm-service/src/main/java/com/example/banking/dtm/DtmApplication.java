package com.example.banking.dtm;

import com.example.banking.support.WebSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WebSupportConfiguration.class)
public class DtmApplication {
    public static void main(String[] args) {
        SpringApplication.run(DtmApplication.class, args);
    }
}
