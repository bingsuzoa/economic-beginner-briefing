package com.economicbriefing.economicflow;

import java.util.LinkedHashSet;
import java.util.List;

public record EconomicFlowExtraction(List<FlowClaimCandidate> flowClaims) {
    public EconomicFlowExtraction {
        flowClaims = flowClaims == null ? List.of() : List.copyOf(flowClaims);
    }

    public List<FlowNodeCandidate> nodes() {
        var texts = new LinkedHashSet<String>();
        flowClaims.forEach(claim -> { texts.add(claim.from()); texts.add(claim.to()); });
        return texts.stream().map(FlowNodeCandidate::new).toList();
    }
}
