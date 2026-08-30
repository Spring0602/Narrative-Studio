package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.MemberDtos;
import java.util.List;

public interface MemberService {
    List<MemberDtos.Summary> list(Long userId, Long projectId);
    MemberDtos.Summary add(Long userId, Long projectId, MemberDtos.AddRequest request);
    MemberDtos.Summary updateRole(Long userId, Long projectId, Long memberId, MemberDtos.RoleRequest request);
    void remove(Long userId, Long projectId, Long memberId);
}
