package com.bank.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Banking API")
                .version("v1")
                .description("Double-entry ledger banking service. Money-moving endpoints "
                        + "(deposit, withdraw, transfer) require an 'Idempotency-Key' header."));
    }
}
