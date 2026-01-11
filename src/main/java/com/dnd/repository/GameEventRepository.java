package com.dnd.repository;

import com.dnd.entity.GameEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameEventRepository extends JpaRepository<GameEvent, Long> {
    List<GameEvent> findByCampaignIdOrderByTimestampDesc(Long campaignId);
    
    @Query("SELECT e FROM GameEvent e WHERE e.campaign.id = :campaignId ORDER BY e.timestamp DESC")
    List<GameEvent> findRecentByCampaignId(Long campaignId, org.springframework.data.domain.Pageable pageable);
    
    List<GameEvent> findByCampaignIdAndEventTypeOrderByTimestampDesc(Long campaignId, String eventType);
}

