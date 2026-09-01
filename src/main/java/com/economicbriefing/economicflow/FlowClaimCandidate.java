package com.economicbriefing.economicflow;

public record FlowClaimCandidate(
        String from,
        String to,
        EventRelationType relationType) {}
