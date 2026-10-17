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
    // 【解释】校验角色在项目内且 ACTIVE；资源归属检查不替代外层用户权限检查。
    public CharacterProfile character(Long project,Long id) {
        CharacterProfile c=characters.selectById(id);
        if(c==null || !project.equals(c.getProjectId()) || !"ACTIVE".equals(c.getStatus()))
            throw BusinessException.notFound("角色不存在");
        return c;
    }
    // 【解释】校验节点所属项目。
    public StoryNode node(Long project,Long id) {
        StoryNode n=nodes.selectById(id);
        if(n==null || !project.equals(n.getProjectId())) throw BusinessException.notFound("节点不存在");
        return n;
    }
    // 【解释】先校验 page/size，再生成受限整数分页 SQL，long 乘法防溢出。
    // 【常见修改】最大每页 50：size>100 改为 size>50 并改提示；它影响多个模块，PlaytestServiceImpl 有独立分页检查需另看。
    public static String limit(int page,int size) {
        if(page<1 || size<1 || size>100) throw invalid("page至少为1，size范围为1至100");
        return "LIMIT "+size+" OFFSET "+((long)(page-1)*size);
    }
    // 【解释】公共参数错误工厂，返回 VALIDATION_ERROR/400。
    public static BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR",message,HttpStatus.BAD_REQUEST);
    }
}
