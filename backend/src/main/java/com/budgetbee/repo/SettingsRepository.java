package com.budgetbee.repo;

import com.budgetbee.model.Settings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettingsRepository extends JpaRepository<Settings, Long> {
    Optional<Settings> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
