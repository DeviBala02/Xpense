package com.budgetbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Owner of this record. Hidden from the API; set from the login. */
    @JsonIgnore
    public Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public Category category;

    public String merchant;
    public String note;

    /** GPAY (auto), CHAT (AI bot) or MANUAL (form) */
    public String source;

    @Column(nullable = false)
    public LocalDateTime spentAt;

    @Column(length = 1000)
    public String rawText;
}
