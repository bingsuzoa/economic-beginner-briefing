package com.economicbriefing.economicflow.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "economic_slots")
public class EconomicSlotEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "slot_key", nullable = false, unique = true, length = 128) private String slotKey;
    @Column(nullable = false, length = 128) private String name;
    @Column(nullable = false) private boolean active = true;
    public Long getId() { return id; }
    public String getSlotKey() { return slotKey; }
    public void setSlotKey(String value) { slotKey = value; }
    public void setName(String value) { name = value; }
    public boolean isActive() { return active; }
}
