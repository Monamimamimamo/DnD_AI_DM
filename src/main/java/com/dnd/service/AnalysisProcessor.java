package com.dnd.service;

import com.dnd.entity.*;
import com.dnd.repository.CampaignRepository;
import com.dnd.repository.GameEventRepository;
import com.dnd.repository.LocationRepository;
import com.dnd.repository.NPCRepository;
import com.dnd.repository.QuestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Обработчик анализа от LLM для сохранения информации о NPC, локациях, квестах
 */
@Service
public class AnalysisProcessor {
    
    @Autowired
    private CampaignRepository campaignRepository;
    
    @Autowired
    private NPCRepository npcRepository;
    
    @Autowired
    private LocationRepository locationRepository;
    
    @Autowired
    private QuestRepository questRepository;
    
    @Autowired
    private GameEventRepository gameEventRepository;
    
    /**
     * Обрабатывает анализ из сообщения и сохраняет информацию о NPC, локациях, квестах
     */
    @Transactional
    public void processAnalysis(String campaignId, Map<String, Object> analysis, Long lastEventId) {
        if (analysis == null || analysis.isEmpty()) {
            return;
        }
        
        Campaign campaign = campaignRepository.findBySessionId(campaignId).orElse(null);
        
        if (campaign == null) {
            System.err.println("⚠️ [AnalysisProcessor] Кампания не найдена: " + campaignId);
            return;
        }
        
        // Обрабатываем новые NPC
        if (analysis.containsKey("new_information")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> newInfo = (Map<String, Object>) analysis.get("new_information");
            // Обрабатываем NPC
            if (newInfo.containsKey("npcs")) processNPCs(campaign, newInfo.get("npcs"));
            // Обрабатываем локации
            if (newInfo.containsKey("locations")) processLocations(campaign, newInfo.get("locations"));
            // Обрабатываем квесты
            if (newInfo.containsKey("quests")) processQuests(campaign, newInfo.get("quests"));
        }
        
        // Обрабатываем упоминания для обновления связей с последним событием
        processMentions(campaign, analysis, lastEventId);
    }
    /**
     * Обрабатывает новых или обновленных NPC
     */
    private void processNPCs(Campaign campaign, Object npcsObj) {
        if (!(npcsObj instanceof List)) return;
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> npcs = (List<Map<String, Object>>) npcsObj;
        
        for (Map<String, Object> npcData : npcs) {
            String name = (String) npcData.get("name");
            if (name == null || name.trim().isEmpty()) continue;
            
            // Ищем существующего NPC
            Optional<NPC> existingNPCOpt = npcRepository.findByCampaignIdAndName(campaign.getId(), name);
            NPC npc;
            
            if (existingNPCOpt.isEmpty()) {
                // Создаем нового NPC
                npc = new NPC();
                npc.setCampaign(campaign);
                npc.setName(name);
                System.out.println("✅ [AnalysisProcessor] Создан новый NPC: " + name);
            } else {
                // Обновляем существующего NPC
                npc = existingNPCOpt.get();
                System.out.println("🔄 [AnalysisProcessor] Обновлен NPC: " + name);
            }
            
            // Обновляем описание умно: дополняем, если новое более информативное
            String newDescription = (String) npcData.get("description");
            if (newDescription != null && !newDescription.trim().isEmpty()) {
                String currentDescription = npc.getDescription();
                if (currentDescription == null || currentDescription.trim().isEmpty()) {
                    npc.setDescription(newDescription);
                } else {
                    npc.setDescription(currentDescription + "\n\n" + newDescription);
                }
                // Иначе оставляем существующее описание
            }
            
            // Обновляем текущую локацию, если указана
            String locationName = (String) npcData.get("location");
            if (locationName != null && !locationName.trim().isEmpty()) {
                List<Location> locations = locationRepository.findByCampaignIdAndName(campaign.getId(), locationName);
                if (!locations.isEmpty()) {
                    // Устанавливаем текущую локацию NPC
                    npc.setLocation(locations.get(0));
                }
            }
            
            // Обновляем домашнюю локацию отдельно, если указана
            String homeLocationName = (String) npcData.get("home_location");
            if (homeLocationName != null && !homeLocationName.trim().isEmpty()) {
                // Проверяем, существует ли локация (может быть просто строка)
                List<Location> homeLocations = locationRepository.findByCampaignIdAndName(campaign.getId(), homeLocationName);
                if (!homeLocations.isEmpty()) {
                    // Устанавливаем домашнюю локацию
                    npc.setHomeLocation(homeLocationName);
                } else {
                    // Если локация не найдена в БД, все равно сохраняем как строку
                    // (возможно, локация будет создана позже)
                    npc.setHomeLocation(homeLocationName);
                }
            }
            
            npcRepository.save(npc);
        }
    }
    
    /**
     * Обрабатывает новые или обновленные локации
     */
    private void processLocations(Campaign campaign, Object locationsObj) {
        if (!(locationsObj instanceof List)) return;
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> locations = (List<Map<String, Object>>) locationsObj;
        
        for (Map<String, Object> locationData : locations) {
            String name = (String) locationData.get("name");
            if (name == null || name.trim().isEmpty()) continue;
            
            // Ищем существующую локацию
            List<Location> existingLocations = locationRepository.findByCampaignIdAndName(campaign.getId(), name);
            Location location;
            
            if (existingLocations.isEmpty()) {
                // Создаем новую локацию
                location = new Location();
                location.setCampaign(campaign);
                location.setName(name);
                location.setDiscovered(true);
                System.out.println("✅ [AnalysisProcessor] Создана новая локация: " + name);
            } else {
                // Обновляем существующую локацию
                location = existingLocations.get(0);
                System.out.println("🔄 [AnalysisProcessor] Обновлена локация: " + name);
            }
            
            String newDescription = (String) locationData.get("description");
            if (newDescription != null && !newDescription.trim().isEmpty()) {
                String currentDescription = location.getDescription();
                if (currentDescription == null || currentDescription.trim().isEmpty()) {
                    // Если описания нет, просто устанавливаем новое
                    location.setDescription(newDescription);
                } else {
                    location.setDescription(currentDescription + "\n\n" + newDescription);
                }
            }
            
            locationRepository.save(location);
        }
    }
    
    /**
     * Обрабатывает новые или обновленные квесты
     */
    private void processQuests(Campaign campaign, Object questsObj) {
        if (!(questsObj instanceof List)) {
            return;
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> quests = (List<Map<String, Object>>) questsObj;
        
        for (Map<String, Object> questData : quests) {
            String title = (String) questData.get("title");
            if (title == null || title.trim().isEmpty()) {
                continue;
            }
            
            // Ищем существующий квест
            Optional<Quest> existingQuestOpt = campaign.getQuests().stream()
                .filter(q -> title.equals(q.getTitle()))
                .findFirst();
            
            Quest quest;
            
            if (existingQuestOpt.isEmpty()) {
                // Создаем новый квест
                quest = new Quest();
                quest.setCampaign(campaign);
                quest.setTitle(title);
                quest.setQuestType((String) questData.getOrDefault("type", "side"));
                quest.setCurrentStageIndex(0);
                quest.setCompleted(false);
                System.out.println("✅ [AnalysisProcessor] Создан новый квест: " + title);
            } else {
                // Обновляем существующий квест
                quest = existingQuestOpt.get();
                System.out.println("🔄 [AnalysisProcessor] Обновлен квест: " + title);
            }
            
            // Обновляем описание умно: дополняем, если новое более информативное
            String newDescription = (String) questData.get("description");
            if (newDescription != null && !newDescription.trim().isEmpty()) {
                String currentDescription = quest.getDescription();
                if (currentDescription == null || currentDescription.trim().isEmpty()) {
                    quest.setDescription(newDescription);
                } else {
                    quest.setDescription(currentDescription + "\n\n" + newDescription);
                }
            }
            
            // Обновляем цель, если есть
            String goal = (String) questData.get("goal");
            if (goal != null && !goal.trim().isEmpty()) {
                quest.setGoal(goal);
            }
            
            questRepository.save(quest);
        }
    }
    
    /**
     * Обрабатывает упоминания для обновления связей с последним GameEvent
     * Связывает упомянутые NPC, локации и квесты с последним событием в истории
     */
    private void processMentions(Campaign campaign, Map<String, Object> analysis, Long lastEventId) {
        Optional<GameEvent> eventOpt = gameEventRepository.findById(lastEventId);
        if (eventOpt.isEmpty()) {
            System.out.println("⚠️ [AnalysisProcessor] Событие не найдено: " + lastEventId);
            return;
        }
        
        GameEvent event = eventOpt.get();
        boolean updated = false;
        
        // Обрабатываем упоминания NPC
        if (analysis.containsKey("npcs_mentioned")) {
            @SuppressWarnings("unchecked")
            List<String> npcNames = (List<String>) analysis.get("npcs_mentioned");
            if (npcNames != null && !npcNames.isEmpty()) {
                for (String npcName : npcNames) {
                    Optional<NPC> npcOpt = npcRepository.findByCampaignIdAndName(campaign.getId(), npcName);
                    if (npcOpt.isPresent() && !event.getNpcs().contains(npcOpt.get())) {
                        event.addNpc(npcOpt.get());
                        updated = true;
                        System.out.println("📝 [AnalysisProcessor] Привязан NPC к событию: " + npcName);
                    }
                }
            }
        }
        
        // Обрабатываем упоминания локаций
        if (analysis.containsKey("locations_mentioned")) {
            @SuppressWarnings("unchecked")
            List<String> locationNames = (List<String>) analysis.get("locations_mentioned");
            if (locationNames != null && !locationNames.isEmpty()) {
                for (String locationName : locationNames) {
                    List<Location> locations = locationRepository.findByCampaignIdAndName(campaign.getId(), locationName);
                    if (!locations.isEmpty()) {
                        Location location = locations.get(0);
                        if (!event.getLocations().contains(location)) {
                            event.addLocation(location);
                            updated = true;
                            System.out.println("📝 [AnalysisProcessor] Привязана локация к событию: " + locationName);
                        }
                    }
                }
            }
        }
        
        // Обрабатываем упоминания квестов
        if (analysis.containsKey("quests_mentioned")) {
            @SuppressWarnings("unchecked")
            List<String> questTitles = (List<String>) analysis.get("quests_mentioned");
            if (questTitles != null && !questTitles.isEmpty()) {
                for (String questTitle : questTitles) {
                    Optional<Quest> questOpt = campaign.getQuests().stream()
                        .filter(q -> questTitle.equals(q.getTitle()))
                        .findFirst();
                    if (questOpt.isPresent() && !event.getQuests().contains(questOpt.get())) {
                        event.addQuest(questOpt.get());
                        updated = true;
                        System.out.println("📝 [AnalysisProcessor] Привязан квест к событию: " + questTitle);
                    }
                }
            }
        }
        
        if (updated) {
            gameEventRepository.save(event);
            System.out.println("✅ [AnalysisProcessor] Событие обновлено с новыми связями");
        }
    }
}
