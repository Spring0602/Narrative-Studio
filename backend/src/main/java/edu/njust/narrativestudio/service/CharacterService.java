package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.CharacterDtos;
import java.util.List;

public interface CharacterService {
    List<CharacterDtos.Summary> list(Long userId, Long projectId);
    CharacterDtos.Summary get(Long userId, Long projectId, Long characterId);
    CharacterDtos.Summary create(Long userId, Long projectId, CharacterDtos.SaveRequest request);
    CharacterDtos.Summary update(Long userId, Long projectId, Long characterId, CharacterDtos.SaveRequest request);
    void delete(Long userId, Long projectId, Long characterId);
}
