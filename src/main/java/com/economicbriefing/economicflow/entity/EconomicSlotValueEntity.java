package com.economicbriefing.economicflow.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "economic_slot_values", uniqueConstraints =
        @UniqueConstraint(columnNames = {"slot_id", "value_key"}))
public class EconomicSlotValueEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "slot_id") private EconomicSlotEntity slot;
    @Column(name = "value_key", nullable = false, length = 128) private String valueKey;
    @Column(nullable = false, length = 128) private String name;
    @Column(nullable = false) private boolean active = true;
    public Long getId() { return id; }
    public EconomicSlotEntity getSlot() { return slot; }
    public void setSlot(EconomicSlotEntity value) { slot = value; }
    public String getValueKey() { return valueKey; }
    public void setValueKey(String value) { valueKey = value; }
    public void setName(String value) { name = value; }
    public boolean isActive() { return active; }
}
