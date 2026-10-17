package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.MemberDtos;
import java.util.List;

// 【业务契约】声明 MemberService 的可调用方法，具体逻辑在 impl/MemberServiceImpl.java；返回类型和参数是调用约定。
// 【修改提示】添加查询参数须同步 Controller、接口、实现类与测试调用；只改排序/提示/DTO 内部字段通常不用改接口签名。
public interface MemberService {
    List<MemberDtos.Summary> list(Long userId, Long projectId);
    MemberDtos.Summary add(Long userId, Long projectId, MemberDtos.AddRequest request);
    MemberDtos.Summary updateRole(Long userId, Long projectId, Long memberId, MemberDtos.RoleRequest request);
    void remove(Long userId, Long projectId, Long memberId);
}
