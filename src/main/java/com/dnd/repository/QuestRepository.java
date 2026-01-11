package com.dnd.repository;

import com.dnd.entity.Quest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestRepository extends JpaRepository<Quest, Long> {
    List<Quest> findByCampaignId(Long campaignId);
    List<Quest> findByCampaignIdAndQuestType(Long campaignId, String questType);
    Optional<Quest> findByCampaignIdAndQuestTypeAndCompletedFalse(Long campaignId, String questType);
}

