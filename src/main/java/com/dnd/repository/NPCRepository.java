package com.dnd.repository;

import com.dnd.entity.NPC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NPCRepository extends JpaRepository<NPC, Long> {
    List<NPC> findByCampaignId(Long campaignId);
    List<NPC> findByCampaignIdAndLocationId(Long campaignId, Long locationId);
    Optional<NPC> findByCampaignIdAndName(Long campaignId, String name);
}

