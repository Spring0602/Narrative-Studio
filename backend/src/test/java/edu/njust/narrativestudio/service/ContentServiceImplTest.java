package edu.njust.narrativestudio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.njust.narrativestudio.dto.CharacterDtos;
import edu.njust.narrativestudio.dto.WorldEntryDtos;
import edu.njust.narrativestudio.entity.CharacterProfile;
import edu.njust.narrativestudio.entity.WorldEntry;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.CharacterProfileMapper;
import edu.njust.narrativestudio.mapper.WorldEntryMapper;
import edu.njust.narrativestudio.service.impl.CharacterServiceImpl;
import edu.njust.narrativestudio.service.impl.WorldEntryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentServiceImplTest {
    @Mock WorldEntryMapper worldEntryMapper;
    @Mock CharacterProfileMapper characterMapper;
    @Mock ProjectAccessService accessService;
    private WorldEntryServiceImpl worldEntryService;
    private CharacterServiceImpl characterService;

    @BeforeEach
    void setUp() {
        worldEntryService = new WorldEntryServiceImpl(worldEntryMapper, accessService,org.mockito.Mockito.mock(ProjectMutationGuard.class));
        characterService = new CharacterServiceImpl(characterMapper, accessService,org.mockito.Mockito.mock(ProjectMutationGuard.class));
    }

    @Test
    void worldEntryFromAnotherProjectIsHidden() {
        WorldEntry entry = new WorldEntry();
        entry.setId(30L);
        entry.setProjectId(99L);
        when(worldEntryMapper.selectById(30L)).thenReturn(entry);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> worldEntryService.get(7L, 10L, 30L));

        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void createCharacterNormalizesOptionalText() {
        when(characterMapper.insert(any(CharacterProfile.class))).thenAnswer(invocation -> {
            CharacterProfile character = invocation.getArgument(0);
            character.setId(40L);
            return 1;
        });

        CharacterDtos.Summary result = characterService.create(7L, 10L,
                new CharacterDtos.SaveRequest(" 主角 ", "  ", " 冷静 ", null, " 真相优先 "));

        assertEquals("主角", result.name());
        assertNull(result.summary());
        assertEquals("冷静", result.personality());
        verify(accessService).requireEditor(7L, 10L);
    }

    @Test
    void updateWorldEntryUsesPathProjectBoundary() {
        WorldEntry entry = new WorldEntry();
        entry.setId(30L);
        entry.setProjectId(99L);
        when(worldEntryMapper.selectById(30L)).thenReturn(entry);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> worldEntryService.update(7L, 10L, 30L,
                        new WorldEntryDtos.SaveRequest("SETTING", "标题", "内容", 0)));

        assertEquals("NOT_FOUND", ex.getCode());
    }
}
