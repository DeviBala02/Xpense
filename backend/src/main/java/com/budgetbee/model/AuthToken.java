package com.budgetbee.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One logged-in device. Only the hash of the token is stored. Deleting the row logs that device out. */
@Entity
@Table(name = "auth_tokens")
public class AuthToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String tokenHash;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public LocalDateTime expiresAt;
}
