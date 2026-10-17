package edu.njust.narrativestudio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;

// 【数据库访问】counts 按本人、项目、版本聚合会话状态及当前节点；release 为 null 用 IS NULL，不能写 = NULL。
// 【修改提示】SQL 参数使用 #{...} 绑定，不能拼接用户输入；Mapper 不替代业务权限检查。新增字段需保证实体与真实列一致。
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
