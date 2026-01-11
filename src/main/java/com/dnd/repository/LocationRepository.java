package com.dnd.repository;

import com.dnd.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByCampaignId(Long campaignId);
    List<Location> findByCampaignIdAndName(Long campaignId, String name);
}

