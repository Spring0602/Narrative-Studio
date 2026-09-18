package edu.njust.narrativestudio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;

public interface EndingCoverageMapper {
    record Count(String status,Long currentNodeId,long amount) {}
    @Select("""
        <script>
        SELECT status,current_node_id,COUNT(*) AS amount
        FROM playtest_session
        WHERE project_id=#{project} AND tester_id=#{user}
        <choose>
          <when test="release != null">AND release_id=#{release}</when>
          <otherwise>AND release_id IS NULL</otherwise>
        </choose>
        GROUP BY status,current_node_id
        </script>
        """)
    List<Count> counts(@Param("user") Long user,@Param("project") Long project,@Param("release") Long release);
}
