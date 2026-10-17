package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.CharacterDtos;
import java.util.List;

// 【业务契约】声明 CharacterService 的可调用方法，具体逻辑在 impl/CharacterServiceImpl.java；返回类型和参数是调用约定。
// 【修改提示】添加查询参数须同步 Controller、接口、实现类与测试调用；只改排序/提示/DTO 内部字段通常不用改接口签名。
public interface CharacterService {
    List<CharacterDtos.Summary> list(Long userId, Long projectId);
    CharacterDtos.Summary get(Long userId, Long projectId, Long characterId);
    CharacterDtos.Summary create(Long userId, Long projectId, CharacterDtos.SaveRequest request);
    CharacterDtos.Summary update(Long userId, Long projectId, Long characterId, CharacterDtos.SaveRequest request);
    void delete(Long userId, Long projectId, Long characterId);
}
