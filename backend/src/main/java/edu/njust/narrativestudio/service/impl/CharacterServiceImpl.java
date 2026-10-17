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
    private final edu.njust.narrativestudio.service.ProjectMutationGuard guard;

    public CharacterServiceImpl(CharacterProfileMapper characterMapper, ProjectAccessService accessService,edu.njust.narrativestudio.service.ProjectMutationGuard guard) {
        this.characterMapper = characterMapper;
        this.accessService = accessService;
        this.guard=guard;
    }

    @Override
    // 【解释】仅查询当前项目中非 DELETED 的角色，转换 DTO 返回。
    // 【常见修改】最新修改优先：把 orderByAsc(CharacterProfile::getId) 替换为 orderByDesc(CharacterProfile::getUpdatedAt).orderByDesc(CharacterProfile::getId)。
    public List<CharacterDtos.Summary> list(Long userId, Long projectId) {
        accessService.requireMember(userId, projectId);
        return characterMapper.selectList(new LambdaQueryWrapper<CharacterProfile>()
                        .eq(CharacterProfile::getProjectId, projectId)
                        .ne(CharacterProfile::getStatus, "DELETED")
                        .orderByAsc(CharacterProfile::getId))
                .stream().map(this::toSummary).toList();
    }

    @Override
    // 【解释】检查成员身份和角色归属，返回详情。
    public CharacterDtos.Summary get(Long userId, Long projectId, Long characterId) {
        accessService.requireMember(userId, projectId);
        return toSummary(requireCharacter(projectId, characterId));
    }

    @Override
    @Transactional
    // 【解释】编辑权限+项目锁，创建 ACTIVE 角色，填入字段与时间后插入。
    public CharacterDtos.Summary create(Long userId, Long projectId, CharacterDtos.SaveRequest request) {
        guard.editor(userId,projectId);
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
    // 【解释】查询本项目有效角色，复用 apply 覆盖表单字段，更新修改时间。
    public CharacterDtos.Summary update(Long userId, Long projectId, Long characterId,
                                        CharacterDtos.SaveRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        CharacterProfile character = requireCharacter(projectId, characterId);
        apply(character, request);
        character.setUpdatedAt(LocalDateTime.now());
        characterMapper.updateById(character);
        return toSummary(character);
    }

    @Override
    @Transactional
    // 【解释】有关系、知识或出场引用时拒绝删除；无引用才将状态写成 DELETED，是逻辑删除。
    // 【常见修改】若只允许 OWNER 删除，在本方法把 requireEditor 改为 requireOwner，保留 guard 与引用检查。
    public void delete(Long userId, Long projectId, Long characterId) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        CharacterProfile character = requireCharacter(projectId, characterId);
        character.setStatus("DELETED");
        if(characterMapper.countReferences(characterId)>0)
            throw new BusinessException("RESOURCE_IN_USE", "角色仍有关系、知识或出场引用，请先解除关联", org.springframework.http.HttpStatus.CONFLICT);
        character.setUpdatedAt(LocalDateTime.now());
        characterMapper.updateById(character);
    }

    // 【解释】同时检查存在、所属项目及未删除，不能只用 selectById 就返回。
    private CharacterProfile requireCharacter(Long projectId, Long characterId) {
        CharacterProfile character = characterMapper.selectById(characterId);
        if (character == null || !projectId.equals(character.getProjectId())
                || "DELETED".equals(character.getStatus())) {
            throw BusinessException.notFound("角色不存在");
        }
        return character;
    }

    // 【解释】新增和修改共用的字段赋值；姓名去首尾空白，可选文本用 trimToNull。
    // 【常见修改】姓名最多 30 字符，在 CharacterDtos.SaveRequest.name 的 @Size 中改 max=30，保留 @NotBlank 并同步前端。
    // 新增持久化字段还需 Entity/SQL/DTO/快照 TABLES/导出与测试，setter 本身不写数据库。
    private void apply(CharacterProfile character, CharacterDtos.SaveRequest request) {
        character.setName(request.name().trim());
        character.setSummary(trimToNull(request.summary()));
        character.setPersonality(trimToNull(request.personality()));
        character.setGoal(trimToNull(request.goal()));
        character.setValueOrder(trimToNull(request.valueOrder()));
    }

    // 【解释】实体转接口返回对象，想多返回字段要同步 CharacterDtos.Summary 和所有构造调用。
    private CharacterDtos.Summary toSummary(CharacterProfile character) {
        return new CharacterDtos.Summary(character.getId(), character.getProjectId(), character.getName(),
                character.getSummary(), character.getPersonality(), character.getGoal(),
                character.getValueOrder(), character.getStatus(), character.getUpdatedAt());
    }

    // 【解释】空白可选字段统一转 null；若修改清空行为，要验证 Mapper 是否实际将 null 写入列。
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
