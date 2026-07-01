package com.tatakai.manager.repository;

import com.tatakai.manager.entity.NpcImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NpcImageRepository extends JpaRepository<NpcImage, UUID> {
}
