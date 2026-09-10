package edu.njust.narrativestudio.service;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
@Component
public class FeatureScope {
    private final CharacterProfileMapper characters;
    private final StoryNodeMapper nodes;
    public FeatureScope(CharacterProfileMapper characters,StoryNodeMapper nodes) { this.characters=characters;this.nodes=nodes; }
    public CharacterProfile character(Long project,Long id) {
        CharacterProfile c=characters.selectById(id);
        if(c==null || !project.equals(c.getProjectId()) || !"ACTIVE".equals(c.getStatus()))
            throw BusinessException.notFound("角色不存在");
        return c;
    }
    public StoryNode node(Long project,Long id) {
        StoryNode n=nodes.selectById(id);
        if(n==null || !project.equals(n.getProjectId())) throw BusinessException.notFound("节点不存在");
        return n;
    }
    public static String limit(int page,int size) {
        if(page<1 || size<1 || size>100) throw invalid("page至少为1，size范围为1至100");
        return "LIMIT "+size+" OFFSET "+((long)(page-1)*size);
    }
    public static BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR",message,HttpStatus.BAD_REQUEST);
    }
}
