package edu.njust.narrativestudio.mapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
// 【数据库访问】characters 查询角色 ID；clear 清除指定节点关联；add 插入关联；先由 Service 验证项目归属并用事务整体替换。
// 【修改提示】SQL 参数使用 #{...} 绑定，不能拼接用户输入；Mapper 不替代业务权限检查。新增字段需保证实体与真实列一致。
public interface NodeCharacterMapper {
    @Select("SELECT character_id FROM node_character WHERE node_id=#{node} ORDER BY character_id")
    List<Long> characters(Long node);
    @Delete("DELETE FROM node_character WHERE node_id=#{node}")
    int clear(Long node);
    @Insert("INSERT INTO node_character(node_id,character_id) VALUES(#{node},#{character})")
    int add(@Param("node") Long node,@Param("character") Long character);
}
