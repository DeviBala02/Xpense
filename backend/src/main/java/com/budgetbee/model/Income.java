package com.budgetbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Money received. A null category means "waiting for the user to choose one". */
@Entity
@Table(name = "incomes")
public class Income {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Owner of this record. Hidden from the API; set from the login. */
    @JsonIgnore
    public Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    /** Who sent it, read from the alert (name or UPI id). May be null. */
    public String sender;

    @ManyToOne(fetch = FetchType.EAGER)
    public IncomeCategory category;

    public String note;

    /** SMS (auto), CHAT (bot) or MANUAL (form) */
    public String source;

    @Column(nullable = false)
    public LocalDateTime receivedAt;

    @Column(length = 1000)
    public String rawText;
}
