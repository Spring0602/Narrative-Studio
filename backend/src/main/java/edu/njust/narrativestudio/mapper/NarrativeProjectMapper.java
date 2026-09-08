package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.NarrativeProject;

public interface NarrativeProjectMapper extends BaseMapper<NarrativeProject> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM narrative_project WHERE id = #{id} FOR UPDATE")
    NarrativeProject lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
