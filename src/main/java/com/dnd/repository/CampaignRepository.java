package com.dnd.repository;

import com.dnd.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    Optional<Campaign> findBySessionId(String sessionId);
    boolean existsBySessionId(String sessionId);
}

