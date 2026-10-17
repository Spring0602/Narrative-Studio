package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.PlaytestSession;

// 【数据库访问】completedNodes 仅取本人此项目此版本 COMPLETED 的不同结局节点，用于跨局进度初始化。
// 【修改提示】SQL 参数使用 #{...} 绑定，不能拼接用户输入；Mapper 不替代业务权限检查。新增字段需保证实体与真实列一致。
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
