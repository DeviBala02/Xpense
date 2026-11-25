package com.budgetbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    /** Stored in lower case. */
    @Column(nullable = false, unique = true)
    public String email;

    @JsonIgnore
    @Column(nullable = false)
    public String passwordHash;

    public LocalDateTime createdAt;
}
