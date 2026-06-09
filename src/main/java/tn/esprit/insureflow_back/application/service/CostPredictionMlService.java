package tn.esprit.insureflow_back.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.esprit.insureflow_back.infrastructure.adapter.out.ml.CostPredictionProperties;
import tn.esprit.insureflow_back.infrastructure.adapter.out.ml.CostPredictionRequest;
import tn.esprit.insureflow_back.infrastructure.adapter.out.ml.CostPredictionResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostPredictionMlService {

    private final RestTemplate restTemplate;
    private final CostPredictionProperties properties;

    public double predictCost(CostPredictionRequest request) {

        if (!properties.isEnabled()) {
            return 0.0;
        }

        try {

            CostPredictionResponse response =
                    restTemplate.postForObject(
                            properties.getUrl(),
                            request,
                            CostPredictionResponse.class
                    );

            if (response == null) {
                return 0.0;
            }

            log.info("Prédiction XGBoost reçue : {} DT",
                    response.predictedCost());

            return response.predictedCost();

        } catch (Exception e) {

            log.error("Erreur appel API ML : {}", e.getMessage());

            return 0.0;
        }
    }
}