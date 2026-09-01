package com.economicbriefing.economicflow;

import com.economicbriefing.economicflow.entity.EconomicSlotEntity;
import com.economicbriefing.economicflow.entity.EconomicSlotValueEntity;
import com.economicbriefing.economicflow.repository.EconomicSlotRepository;
import com.economicbriefing.economicflow.repository.EconomicSlotValueRepository;
import org.springframework.stereotype.Component;

@Component
public class EconomicKeyResolver {
    private final EconomicSlotRepository slots;
    private final EconomicSlotValueRepository values;

    public EconomicKeyResolver(EconomicSlotRepository slots, EconomicSlotValueRepository values) {
        this.slots = slots;
        this.values = values;
    }

    public ResolvedKeys resolve(EventCandidate candidate) {
        if (candidate.nodeKind() == null) return null;
        if (blank(candidate.scopeKey()) || blank(candidate.subjectKey())) {
            throw new IllegalArgumentException("Normalized node requires scopeKey and subjectKey");
        }
        if (blank(candidate.slotKey())) {
            if (candidate.nodeKind() == NodeKind.EVENT && blank(candidate.valueKey())) return null;
            throw new IllegalArgumentException("STATE requires slotKey and valueKey");
        }
        EconomicSlotEntity slot = slots.findBySlotKeyAndActiveTrue(candidate.slotKey())
                .orElseThrow(() -> new IllegalArgumentException("Unknown slot key: " + candidate.slotKey()));
        EconomicSlotValueEntity value = candidate.valueKey() == null ? null
                : values.findBySlot_IdAndValueKeyAndActiveTrue(slot.getId(), candidate.valueKey())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown value key for slot " + candidate.slotKey() + ": " + candidate.valueKey()));
        if (candidate.nodeKind() == NodeKind.STATE && value == null) {
            throw new IllegalArgumentException("STATE requires valueKey");
        }
        return new ResolvedKeys(slot, value);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public record ResolvedKeys(EconomicSlotEntity slot, EconomicSlotValueEntity value) {}
}
