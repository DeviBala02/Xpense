package com.budgetbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** One row (id 1) of app-wide settings. */
@Entity
@Table(name = "settings")
public class Settings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JsonIgnore
    @Column(unique = true)
    public Long userId;

    /** Income the user expects each month. Used before the real credit arrives. */
    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal expectedIncome = BigDecimal.ZERO;
}
