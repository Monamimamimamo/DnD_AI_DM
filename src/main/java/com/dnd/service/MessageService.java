package com.dnd.service;

import com.dnd.entity.*;
import com.dnd.repository.CampaignRepository;
import com.dnd.repository.GameEventRepository;
import com.dnd.repository.PlayerMessageRepository;
import com.dnd.repository.LocationRepository;
import com.dnd.repository.NPCRepository;
import com.dnd.repository.QuestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для сохранения сообщений в БД
 */
@Service
public class MessageService {
    
    @Autowired
    private CampaignRepository campaignRepository;
    
    @Autowired
    private GameEventRepository gameEventRepository;
    
    @Autowired
    private PlayerMessageRepository playerMessageRepository;
    
    @Autowired
    private LocationRepository locationRepository;
    
    @Autowired
    private NPCRepository npcRepository;
    
    @Autowired
    private QuestRepository questRepository;
    
    @Autowired(required = false)
    private com.dnd.service.EventIndexingService eventIndexingService;
    
    /**
     * Сохраняет сообщение от игрока
     */
    @Transactional
    public PlayerMessage savePlayerMessage(String campaignId, String characterName, String messageText) {
        Campaign campaign = campaignRepository.findBySessionId(campaignId)
            .orElseThrow(() -> new IllegalArgumentException("Кампания не найдена: " + campaignId));
        
        PlayerMessage message = new PlayerMessage(characterName, messageText, campaign);
        return playerMessageRepository.save(message);
    }
    
    /**
     * Сохраняет сообщение от DM (LLM) с множественными связями
     */
    @Transactional
    public GameEvent saveDMMessage(String campaignId, String eventType, String description, String fullText,
                                   String characterName, String locationName, 
                                   List<Long> npcIds, List<Long> questIds, List<Long> locationIds) {
        Campaign campaign = campaignRepository.findBySessionId(campaignId)
            .orElseThrow(() -> new IllegalArgumentException("Кампания не найдена: " + campaignId));
        
        GameEvent event = new GameEvent();
        event.setCampaign(campaign);
        event.setEventType(eventType);
        event.setDescription(description);
        event.setFullText(fullText != null ? fullText : description);
        event.setCharacterName(characterName);
        event.setLocationName(locationName);
        event.setTimestamp(LocalDateTime.now());
        
        // Устанавливаем множественные связи с NPC
        if (npcIds != null && !npcIds.isEmpty()) {
            List<NPC> npcs = npcIds.stream()
                .map(id -> npcRepository.findById(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
            event.setNpcs(npcs);
        }
        
        // Устанавливаем множественные связи с квестами
        if (questIds != null && !questIds.isEmpty()) {
            List<Quest> quests = questIds.stream()
                .map(id -> questRepository.findById(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
            event.setQuests(quests);
        }
        
        // Устанавливаем множественные связи с локациями
        List<Location> locationsToAdd = new ArrayList<>();
        
        // Если указаны ID локаций
        if (locationIds != null && !locationIds.isEmpty()) {
            List<Location> locationsById = locationIds.stream()
                .map(id -> locationRepository.findById(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
            locationsToAdd.addAll(locationsById);
        }
        
        // Если локация указана по имени, находим её
        if (locationName != null) {
            List<Location> locationsByName = locationRepository.findByCampaignIdAndName(campaign.getId(), locationName);
            for (Location loc : locationsByName) {
                if (!locationsToAdd.contains(loc)) {
                    locationsToAdd.add(loc);
                }
            }
        }
        
        if (!locationsToAdd.isEmpty()) {
            event.setLocations(locationsToAdd);
        }
        
        GameEvent savedEvent = gameEventRepository.save(event);
        
        // Индексируем событие в векторную БД для RAG
        if (eventIndexingService != null) {
            eventIndexingService.indexEvent(savedEvent);
        }
        
        return savedEvent;
    }
    
    /**
     * Сохраняет сообщение от DM с автоматическим определением связей
     */
    @Transactional
    public GameEvent saveDMMessage(String campaignId, String eventType, String description, String fullText,
                                   String characterName, String locationName) {
        return saveDMMessage(campaignId, eventType, description, fullText, characterName, locationName, 
                            null, null, null);
    }
    
    /**
     * Определяет ID основного квеста для кампании
     */
    @Transactional(readOnly = true)
    public Long getMainQuestId(String campaignId) {
        Campaign campaign = campaignRepository.findBySessionId(campaignId)
            .orElse(null);
        
        if (campaign == null) {
            return null;
        }
        
        Optional<Quest> mainQuest = campaign.getQuests().stream()
            .filter(q -> "main".equals(q.getQuestType()))
            .findFirst();
        
        return mainQuest.map(Quest::getId).orElse(null);
    }
    
    /**
     * Определяет список ID всех активных квестов для кампании
     */
    @Transactional(readOnly = true)
    public List<Long> getActiveQuestIds(String campaignId) {
        Campaign campaign = campaignRepository.findBySessionId(campaignId)
            .orElse(null);
        
        if (campaign == null) {
            return new ArrayList<>();
        }
        
        return campaign.getQuests().stream()
            .filter(q -> !q.getCompleted())
            .map(Quest::getId)
            .collect(Collectors.toList());
    }
    
    /**
     * Находит ID квестов по их названиям (работает внутри транзакции)
     */
    @Transactional(readOnly = true)
    public List<Long> findQuestIdsByTitles(String campaignId, List<String> questTitles) {
        Campaign campaign = campaignRepository.findBySessionId(campaignId)
            .orElse(null);
        
        if (campaign == null || questTitles == null || questTitles.isEmpty()) {
            return new ArrayList<>();
        }
        
        return campaign.getQuests().stream()
            .filter(q -> questTitles.contains(q.getTitle()))
            .map(Quest::getId)
            .collect(Collectors.toList());
    }
    
    /**
     * Находит NPC по имени в кампании
     */
    @Transactional(readOnly = true)
    public List<Long> findNpcIdsByName(String campaignId, List<String> npcNames) {
        if (npcNames == null || npcNames.isEmpty()) {
            return new ArrayList<>();
        }
        
        Campaign campaign = campaignRepository.findBySessionId(campaignId)
            .orElse(null);
        
        if (campaign == null) {
            return new ArrayList<>();
        }
        
        return campaign.getNpcs().stream()
            .filter(npc -> npcNames.contains(npc.getName()))
            .map(NPC::getId)
            .collect(Collectors.toList());
    }
    
    /**
     * Находит локации по имени в кампании
     */
    @Transactional(readOnly = true)
    public List<Long> findLocationIdsByName(String campaignId, List<String> locationNames) {
        if (locationNames == null || locationNames.isEmpty()) {
            return new ArrayList<>();
        }
        
        Campaign campaign = campaignRepository.findBySessionId(campaignId)
            .orElse(null);
        
        if (campaign == null) {
            return new ArrayList<>();
        }
        
        return campaign.getLocations().stream()
            .filter(loc -> locationNames.contains(loc.getName()))
            .map(Location::getId)
            .collect(Collectors.toList());
    }
    
    /**
     * Получает историю сообщений для кампании (объединение сообщений игроков и событий DM)
     * Возвращает список сообщений, отсортированный по времени создания
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessageHistory(String campaignId) {
        Campaign campaign = campaignRepository.findBySessionId(campaignId)
            .orElse(null);
        
        if (campaign == null) {
            return new ArrayList<>();
        }
        
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // Загружаем сообщения игроков
        List<PlayerMessage> playerMessages = playerMessageRepository.findByCampaignIdOrderByCreatedAtDesc(campaign.getId());
        for (PlayerMessage msg : playerMessages) {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("type", "player_message");
            messageMap.put("id", msg.getId());
            messageMap.put("character_name", msg.getCharacterName());
            messageMap.put("text", msg.getMessageText());
            messageMap.put("timestamp", msg.getCreatedAt());
            messageMap.put("created_at", msg.getCreatedAt());
            messages.add(messageMap);
        }
        
        // Загружаем события от DM
        List<GameEvent> dmEvents = gameEventRepository.findByCampaignIdOrderByTimestampDesc(campaign.getId());
        for (GameEvent event : dmEvents) {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("type", "dm_message");
            messageMap.put("id", event.getId());
            messageMap.put("event_type", event.getEventType());
            messageMap.put("text", event.getFullText() != null ? event.getFullText() : event.getDescription());
            messageMap.put("description", event.getDescription());
            messageMap.put("character_name", event.getCharacterName());
            messageMap.put("location_name", event.getLocationName());
            messageMap.put("timestamp", event.getTimestamp());
            messageMap.put("created_at", event.getCreatedAt());
            
            // Добавляем связанные сущности
            List<Long> npcIds = event.getNpcs().stream()
                .map(NPC::getId)
                .collect(Collectors.toList());
            messageMap.put("npc_ids", npcIds);
            
            List<Long> questIds = event.getQuests().stream()
                .map(Quest::getId)
                .collect(Collectors.toList());
            messageMap.put("quest_ids", questIds);
            
            List<Long> locationIds = event.getLocations().stream()
                .map(Location::getId)
                .collect(Collectors.toList());
            messageMap.put("location_ids", locationIds);
            
            messages.add(messageMap);
        }
        
        // Сортируем все сообщения по времени создания (от старых к новым)
        messages.sort((a, b) -> {
            LocalDateTime timeA = (LocalDateTime) a.get("created_at");
            LocalDateTime timeB = (LocalDateTime) b.get("created_at");
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return -1;
            if (timeB == null) return 1;
            return timeA.compareTo(timeB);
        });
        
        return messages;
    }
}
