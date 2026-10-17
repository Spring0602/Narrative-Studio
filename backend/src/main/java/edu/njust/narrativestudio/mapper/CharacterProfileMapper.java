package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.CharacterProfile;

// 【数据库访问】countReferences 汇总关系、知识、出场引用，用于禁止删除被引用角色；新增引用表时要补统计。
// 【修改提示】SQL 参数使用 #{...} 绑定，不能拼接用户输入；Mapper 不替代业务权限检查。新增字段需保证实体与真实列一致。
public interface CharacterProfileMapper extends BaseMapper<CharacterProfile> {
    @org.apache.ibatis.annotations.Select("""
        SELECT (SELECT COUNT(*) FROM character_relation WHERE source_character_id=#{id} OR target_character_id=#{id})
             + (SELECT COUNT(*) FROM character_knowledge WHERE character_id=#{id})
             + (SELECT COUNT(*) FROM node_character WHERE character_id=#{id})
        """)
    long countReferences(@org.apache.ibatis.annotations.Param("id") Long id);
}
