package edu.njust.narrativestudio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AiDialogueService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AiDialogueService.class);
    public record Request(@NotNull @Positive Long nodeId,@NotNull @Positive Long characterId,
            @NotBlank @Size(max=1000) String direction,@NotNull @AssertTrue Boolean consent) {}
    public record Candidate(String text,boolean saved) {}
    private final ProjectAccessService access;
    private final FeatureScope scope;
    private final DialogueProvider provider;
    private final ObjectMapper json;
    public AiDialogueService(ProjectAccessService access,FeatureScope scope,DialogueProvider provider,ObjectMapper json) {
        this.access=access;this.scope=scope;this.provider=provider;this.json=json;
    }
    // No transaction is held while waiting on a model. Only explicitly selected content is sent.
    // 【解释】验证编辑权限和明确同意，只发送选中节点/角色的资料；生成后再次校验权限，返回未保存候选。
    // 【修改提示】候选不是已写入剧情；不能把 saved=false 改 true 来冒充保存。默认未启用 AI，配置代码不等于实际已接通。
    // 方向文字长度看本类 Request 的 @Size(max=1000)；不得打印完整用户资料或密钥到日志。
    public Candidate generate(Long user,Long project,Request request) {
        access.requireEditor(user,project);
        if(!Boolean.TRUE.equals(request.consent())) throw FeatureScope.invalid("请先确认允许向配置的 AI 服务发送所选剧情与角色资料");
        var node=scope.node(project,request.nodeId());var character=scope.character(project,request.characterId());
        Map<String,Object> context=new LinkedHashMap<>();
        context.put("nodeTitle",node.getTitle());context.put("nodeContent",node.getContent());
        context.put("characterName",character.getName());context.put("personality",character.getPersonality());
        context.put("goal",character.getGoal());context.put("direction",request.direction());
        try {
            long start=System.nanoTime();
            String text=provider.generate(json.writeValueAsString(context));
            access.requireEditor(user,project); // Recheck permissions if membership changed during generation.
            log.info("AI candidate generated; characters={}, elapsedMs={}",text.length(),(System.nanoTime()-start)/1_000_000);
            return new Candidate(text,false);
        } catch(JsonProcessingException ex) {throw FeatureScope.invalid("无法编码所选剧情资料");}
    }
}
