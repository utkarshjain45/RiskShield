package com.riskshield.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI riskShieldOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RiskShield AI — Enterprise Risk Manager API")
                        .description("Event-driven AI risk management platform for merchants, policy engine, and audit trail.")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("RiskShield AI Engineering Team")
                                .email("engineering@riskshield.ai"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
