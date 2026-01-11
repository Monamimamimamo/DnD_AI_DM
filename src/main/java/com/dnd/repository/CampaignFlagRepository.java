package com.dnd.repository;

import com.dnd.entity.CampaignFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignFlagRepository extends JpaRepository<CampaignFlag, Long> {
    List<CampaignFlag> findByCampaignId(Long campaignId);
    Optional<CampaignFlag> findByCampaignIdAndFlagKey(Long campaignId, String flagKey);
}

