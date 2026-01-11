package com.dnd.repository;

import com.dnd.entity.CharacterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterRepository extends JpaRepository<CharacterEntity, Long> {
    List<CharacterEntity> findByCampaignId(Long campaignId);
    Optional<CharacterEntity> findByCampaignIdAndName(Long campaignId, String name);
}

