package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.NarrativeProject;

// 【数据库访问】lockById 使用 FOR UPDATE，必须在事务中才有跨后续操作的锁保护；不能改成普通 selectById 来省事。
// 【修改提示】SQL 参数使用 #{...} 绑定，不能拼接用户输入；Mapper 不替代业务权限检查。新增字段需保证实体与真实列一致。
public interface NarrativeProjectMapper extends BaseMapper<NarrativeProject> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM narrative_project WHERE id = #{id} FOR UPDATE")
    NarrativeProject lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
