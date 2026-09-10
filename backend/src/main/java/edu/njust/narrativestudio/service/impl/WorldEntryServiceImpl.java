package edu.njust.narrativestudio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.dto.WorldEntryDtos;
import edu.njust.narrativestudio.entity.WorldEntry;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.WorldEntryMapper;
import edu.njust.narrativestudio.service.ProjectAccessService;
import edu.njust.narrativestudio.service.WorldEntryService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorldEntryServiceImpl implements WorldEntryService {
    private final WorldEntryMapper entryMapper;
    private final ProjectAccessService accessService;
    private final edu.njust.narrativestudio.service.ProjectMutationGuard guard;

    public WorldEntryServiceImpl(WorldEntryMapper entryMapper, ProjectAccessService accessService,edu.njust.narrativestudio.service.ProjectMutationGuard guard) {
        this.entryMapper = entryMapper;
        this.accessService = accessService;
        this.guard=guard;
    }

    @Override
    public List<WorldEntryDtos.Summary> list(Long userId, Long projectId, String entryType) {
        accessService.requireMember(userId, projectId);
        LambdaQueryWrapper<WorldEntry> query = new LambdaQueryWrapper<WorldEntry>()
                .eq(WorldEntry::getProjectId, projectId);
        if (entryType != null && !entryType.isBlank()) query.eq(WorldEntry::getEntryType, entryType);
        query.orderByAsc(WorldEntry::getSortOrder).orderByAsc(WorldEntry::getId);
        return entryMapper.selectList(query).stream().map(this::toSummary).toList();
    }

    @Override
    public WorldEntryDtos.Summary get(Long userId, Long projectId, Long entryId) {
        accessService.requireMember(userId, projectId);
        return toSummary(requireEntry(projectId, entryId));
    }

    @Override
    @Transactional
    public WorldEntryDtos.Summary create(Long userId, Long projectId, WorldEntryDtos.SaveRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        LocalDateTime now = LocalDateTime.now();
        WorldEntry entry = new WorldEntry();
        entry.setProjectId(projectId);
        apply(entry, request);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        entryMapper.insert(entry);
        return toSummary(entry);
    }

    @Override
    @Transactional
    public WorldEntryDtos.Summary update(Long userId, Long projectId, Long entryId,
                                         WorldEntryDtos.SaveRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        WorldEntry entry = requireEntry(projectId, entryId);
        apply(entry, request);
        entry.setUpdatedAt(LocalDateTime.now());
        entryMapper.updateById(entry);
        return toSummary(entry);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long projectId, Long entryId) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        requireEntry(projectId, entryId);
        entryMapper.deleteById(entryId);
    }

    private WorldEntry requireEntry(Long projectId, Long entryId) {
        WorldEntry entry = entryMapper.selectById(entryId);
        if (entry == null || !projectId.equals(entry.getProjectId())) {
            throw BusinessException.notFound("世界观条目不存在");
        }
        return entry;
    }

    private void apply(WorldEntry entry, WorldEntryDtos.SaveRequest request) {
        entry.setEntryType(request.entryType());
        entry.setTitle(request.title().trim());
        entry.setContent(request.content().trim());
        entry.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private WorldEntryDtos.Summary toSummary(WorldEntry entry) {
        return new WorldEntryDtos.Summary(entry.getId(), entry.getProjectId(), entry.getEntryType(),
                entry.getTitle(), entry.getContent(), entry.getSortOrder(), entry.getUpdatedAt());
    }
}
