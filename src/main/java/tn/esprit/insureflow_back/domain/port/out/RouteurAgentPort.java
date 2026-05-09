package tn.esprit.insureflow_back.domain.port.out;

import tn.esprit.insureflow_back.domain.model.AgentResult;
import tn.esprit.insureflow_back.domain.model.Claim;

public interface RouteurAgentPort {
    AgentResult analyserTypeSinistre(Claim claim);
}