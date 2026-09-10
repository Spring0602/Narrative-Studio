package edu.njust.narrativestudio.mapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface NodeCharacterMapper {
    @Select("SELECT character_id FROM node_character WHERE node_id=#{node} ORDER BY character_id")
    List<Long> characters(Long node);
    @Delete("DELETE FROM node_character WHERE node_id=#{node}")
    int clear(Long node);
    @Insert("INSERT INTO node_character(node_id,character_id) VALUES(#{node},#{character})")
    int add(@Param("node") Long node,@Param("character") Long character);
}
