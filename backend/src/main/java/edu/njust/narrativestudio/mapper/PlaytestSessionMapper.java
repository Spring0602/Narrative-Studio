package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.PlaytestSession;

public interface PlaytestSessionMapper extends BaseMapper<PlaytestSession> {
    @org.apache.ibatis.annotations.Select("""
      <script>SELECT DISTINCT current_node_id FROM playtest_session
      WHERE project_id=#{project} AND tester_id=#{user} AND status='COMPLETED'
      <choose><when test="release != null">AND release_id=#{release}</when>
      <otherwise>AND release_id IS NULL</otherwise></choose></script>
      """)
    java.util.List<Long> completedNodes(@org.apache.ibatis.annotations.Param("user") Long user,
            @org.apache.ibatis.annotations.Param("project") Long project,@org.apache.ibatis.annotations.Param("release") Long release);
}
