package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.CharacterProfile;

public interface CharacterProfileMapper extends BaseMapper<CharacterProfile> {
    @org.apache.ibatis.annotations.Select("""
        SELECT (SELECT COUNT(*) FROM character_relation WHERE source_character_id=#{id} OR target_character_id=#{id})
             + (SELECT COUNT(*) FROM character_knowledge WHERE character_id=#{id})
             + (SELECT COUNT(*) FROM node_character WHERE character_id=#{id})
        """)
    long countReferences(@org.apache.ibatis.annotations.Param("id") Long id);
}
