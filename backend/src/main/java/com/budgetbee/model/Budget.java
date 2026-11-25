package com.budgetbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "budgets")
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Owner of this record. Hidden from the API; set from the login. */
    @JsonIgnore
    public Long userId;

    public String name;

    /** null = overall budget across every category */
    @ManyToOne(fetch = FetchType.EAGER)
    public Category category;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false)
    public LocalDate startDate;

    @Column(nullable = false)
    public LocalDate endDate;
}
