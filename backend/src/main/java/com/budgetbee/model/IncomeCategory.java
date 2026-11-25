package com.budgetbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "income_categories")
public class IncomeCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Owner of this record. Hidden from the API; set from the login. */
    @JsonIgnore
    public Long userId;

    @Column(nullable = false)
    public String name;

    public String icon;

    public IncomeCategory() {}

    public IncomeCategory(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }
}
