package com.budgetbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "savings_goals")
public class SavingsGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Owner of this record. Hidden from the API; set from the login. */
    @JsonIgnore
    public Long userId;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal targetAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal savedAmount = BigDecimal.ZERO;

    public LocalDate deadline;
}
