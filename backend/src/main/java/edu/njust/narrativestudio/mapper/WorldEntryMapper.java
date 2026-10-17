package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.WorldEntry;

// 答辩修改点：BaseMapper 已提供 selectList、selectById、insert、updateById、deleteById 等方法。
// 调整排序、按标题搜索通常只改 ServiceImpl 的查询条件，不必在这里重新写 SQL。
public interface WorldEntryMapper extends BaseMapper<WorldEntry> {
    // 【需要自定义 SQL 时的写法】例如统计某项目的世界观条目数，可以在此加入：
    // @org.apache.ibatis.annotations.Select(
    //         "SELECT COUNT(*) FROM world_entry WHERE project_id = #{projectId}")
    // long countByProjectId(@org.apache.ibatis.annotations.Param("projectId") Long projectId);
    // 然后由 Service 调用 entryMapper.countByProjectId(projectId)。
    // 这只是语法示例，简单统计也能用已有的 selectCount，无须重复实现。
    // #{projectId} 是绑定参数，不要换成字符串拼接或 ${projectId}。
    // SQL 按项目过滤不等于完成权限校验：Service 仍需先检查当前用户是否有访问权限。
}
