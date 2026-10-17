package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.WorldEntryDtos;
import java.util.List;

public interface WorldEntryService {
    // 答辩修改点：接口只约定“能调用什么”，具体查询和权限判断写在 impl 中。
    // 【增加标题搜索】把下一行签名替换为：
    // List<WorldEntryDtos.Summary> list(Long userId, Long projectId, String entryType, String keyword);
    // 同步改 Controller 的调用、ServiceImpl 的方法签名和所有测试调用，不能只改这里。
    // ServiceImpl 中增加 query.like(...) 的位置和空白处理，见实现类的搜索示例注释。
    List<WorldEntryDtos.Summary> list(Long userId, Long projectId, String entryType);
    WorldEntryDtos.Summary get(Long userId, Long projectId, Long entryId);
    // 【新增请求字段】如果只改 SaveRequest 内部字段，这里的参数类型不用改；
    // 但实现类必须读取新字段，需要保存到数据库时还要同步 Entity 和 SQL。
    WorldEntryDtos.Summary create(Long userId, Long projectId, WorldEntryDtos.SaveRequest request);
    WorldEntryDtos.Summary update(Long userId, Long projectId, Long entryId, WorldEntryDtos.SaveRequest request);
    // 【改变删除权限】改实现类中的权限检查，不是在接口中写判断。
    // void 表示业务方法不返回数据；HTTP 成功响应由 Controller 包装。
    void delete(Long userId, Long projectId, Long entryId);
}
