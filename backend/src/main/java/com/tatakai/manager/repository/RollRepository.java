package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Roll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RollRepository extends JpaRepository<Roll, UUID> {
}
