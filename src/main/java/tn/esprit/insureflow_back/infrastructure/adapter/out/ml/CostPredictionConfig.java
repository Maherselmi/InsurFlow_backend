package tn.esprit.insureflow_back.infrastructure.adapter.out.ml;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(CostPredictionProperties.class)
public class CostPredictionConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}