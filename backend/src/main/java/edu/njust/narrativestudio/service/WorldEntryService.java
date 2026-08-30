package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.WorldEntryDtos;
import java.util.List;

public interface WorldEntryService {
    List<WorldEntryDtos.Summary> list(Long userId, Long projectId, String entryType);
    WorldEntryDtos.Summary get(Long userId, Long projectId, Long entryId);
    WorldEntryDtos.Summary create(Long userId, Long projectId, WorldEntryDtos.SaveRequest request);
    WorldEntryDtos.Summary update(Long userId, Long projectId, Long entryId, WorldEntryDtos.SaveRequest request);
    void delete(Long userId, Long projectId, Long entryId);
}
