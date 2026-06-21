package com.anushika.typeahead.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI typeaheadOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Search Typeahead System")
                        .description("Production-style autocomplete system built using:\n\n" +
                                "- Spring Boot\n" +
                                "- PostgreSQL\n" +
                                "- Redis Sorted Sets\n" +
                                "- Redis Streams\n" +
                                "- Consistent Hashing\n" +
                                "- Cache Aside Pattern\n" +
                                "- Batch Processing\n" +
                                "- Trend Scoring")
                        .version("1.0.0")
                        .contact(new Contact().name("Anushika Chauhan")));
    }
}
