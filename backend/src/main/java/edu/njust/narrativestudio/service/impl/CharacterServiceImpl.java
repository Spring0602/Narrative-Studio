package edu.njust.narrativestudio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.dto.CharacterDtos;
import edu.njust.narrativestudio.entity.CharacterProfile;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.CharacterProfileMapper;
import edu.njust.narrativestudio.service.CharacterService;
import edu.njust.narrativestudio.service.ProjectAccessService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CharacterServiceImpl implements CharacterService {
    private final CharacterProfileMapper characterMapper;
    private final ProjectAccessService accessService;

    public CharacterServiceImpl(CharacterProfileMapper characterMapper, ProjectAccessService accessService) {
        this.characterMapper = characterMapper;
        this.accessService = accessService;
    }

    @Override
    public List<CharacterDtos.Summary> list(Long userId, Long projectId) {
        accessService.requireMember(userId, projectId);
        return characterMapper.selectList(new LambdaQueryWrapper<CharacterProfile>()
                        .eq(CharacterProfile::getProjectId, projectId)
                        .ne(CharacterProfile::getStatus, "DELETED")
                        .orderByAsc(CharacterProfile::getId))
                .stream().map(this::toSummary).toList();
    }

    @Override
    public CharacterDtos.Summary get(Long userId, Long projectId, Long characterId) {
        accessService.requireMember(userId, projectId);
        return toSummary(requireCharacter(projectId, characterId));
    }

    @Override
    @Transactional
    public CharacterDtos.Summary create(Long userId, Long projectId, CharacterDtos.SaveRequest request) {
        accessService.requireEditor(userId, projectId);
        LocalDateTime now = LocalDateTime.now();
        CharacterProfile character = new CharacterProfile();
        character.setProjectId(projectId);
        character.setStatus("ACTIVE");
        apply(character, request);
        character.setCreatedAt(now);
        character.setUpdatedAt(now);
        characterMapper.insert(character);
        return toSummary(character);
    }

    @Override
    @Transactional
    public CharacterDtos.Summary update(Long userId, Long projectId, Long characterId,
                                        CharacterDtos.SaveRequest request) {
        accessService.requireEditor(userId, projectId);
        CharacterProfile character = requireCharacter(projectId, characterId);
        apply(character, request);
        character.setUpdatedAt(LocalDateTime.now());
        characterMapper.updateById(character);
        return toSummary(character);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long projectId, Long characterId) {
        accessService.requireEditor(userId, projectId);
        CharacterProfile character = requireCharacter(projectId, characterId);
        character.setStatus("DELETED");
        character.setUpdatedAt(LocalDateTime.now());
        characterMapper.updateById(character);
    }

    private CharacterProfile requireCharacter(Long projectId, Long characterId) {
        CharacterProfile character = characterMapper.selectById(characterId);
        if (character == null || !projectId.equals(character.getProjectId())
                || "DELETED".equals(character.getStatus())) {
            throw BusinessException.notFound("角色不存在");
        }
        return character;
    }

    private void apply(CharacterProfile character, CharacterDtos.SaveRequest request) {
        character.setName(request.name().trim());
        character.setSummary(trimToNull(request.summary()));
        character.setPersonality(trimToNull(request.personality()));
        character.setGoal(trimToNull(request.goal()));
        character.setValueOrder(trimToNull(request.valueOrder()));
    }

    private CharacterDtos.Summary toSummary(CharacterProfile character) {
        return new CharacterDtos.Summary(character.getId(), character.getProjectId(), character.getName(),
                character.getSummary(), character.getPersonality(), character.getGoal(),
                character.getValueOrder(), character.getStatus(), character.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
