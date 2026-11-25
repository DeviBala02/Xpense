package com.budgetbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Owner of this record. Hidden from the API; set from the login. */
    @JsonIgnore
    public Long userId;

    @Column(nullable = false)
    public String name;

    /** Comma separated words that map a payment note to this category, e.g. "snacks,samosa,chips" */
    @Column(length = 1000)
    public String keywords;

    public String icon;

    public Category() {}

    public Category(String name, String icon, String keywords) {
        this.name = name;
        this.icon = icon;
        this.keywords = keywords;
    }
}
