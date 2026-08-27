package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.ProjectDtos;
import java.util.List;

public interface ProjectService {
    ProjectDtos.Summary create(Long currentUserId, ProjectDtos.CreateRequest request);
    List<ProjectDtos.Summary> listAccessible(Long currentUserId);
    ProjectDtos.Summary getAccessible(Long currentUserId, Long projectId);
    ProjectDtos.Summary update(Long currentUserId, Long projectId, ProjectDtos.UpdateRequest request);
    void archive(Long currentUserId, Long projectId);
}
