package com.budgetbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/** "Always put money from this sender in this category." Created when the user ticks Remember. */
@Entity
@Table(name = "sender_rules")
public class SenderRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Owner of this record. Hidden from the API; set from the login. */
    @JsonIgnore
    public Long userId;

    @Column(nullable = false)
    public String sender;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public IncomeCategory category;
}
