package com.dnd.repository;

import com.dnd.entity.PlayerMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerMessageRepository extends JpaRepository<PlayerMessage, Long> {
    List<PlayerMessage> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
    
    List<PlayerMessage> findByCampaignIdAndCharacterNameOrderByCreatedAtDesc(Long campaignId, String characterName);
}
