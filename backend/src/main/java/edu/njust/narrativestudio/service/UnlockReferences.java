package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.dto.ProgressSnapshot;
import edu.njust.narrativestudio.engine.UnlockRuleEngine;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.exception.BusinessException;
import org.springframework.stereotype.Component;
@Component
public class UnlockReferences {
    private final StoryChoiceMapper choices;private final PlayerProgressMapper progress;private final ObjectMapper json;private final UnlockRuleEngine engine;
    public UnlockReferences(StoryChoiceMapper choices,PlayerProgressMapper progress,ObjectMapper json,UnlockRuleEngine engine) {
        this.choices=choices;this.progress=progress;this.json=json;this.engine=engine;
    }
    public void requireUnused(Long project,String key,boolean variable) {
        for(var choice:choices.selectList(new LambdaQueryWrapper<StoryChoice>().eq(StoryChoice::getProjectId,project))) {
            if(engine.references(engine.decode(choice.getUnlockRule()),key,variable))
                throw BusinessException.conflict("标识被高级解锁规则引用，请先修改相关选择");
        }
        for(var row:progress.selectList(new LambdaQueryWrapper<PlayerProgress>().eq(PlayerProgress::getProjectId,project).eq(PlayerProgress::getVersionKey,0L))) {
            ProgressSnapshot p;
            try {p=json.readValue(row.getProgressJson(),ProgressSnapshot.class);}
            catch(Exception ex) {throw BusinessException.conflict("进度数据损坏，无法安全修改标识");}
            if(variable?p.values().containsKey(key):(p.completedEndings().contains(key)||p.visitedNodes().contains(key)))
                throw BusinessException.conflict("标识被编辑稿玩家进度使用，请先重置相关测试进度；建议保留稳定标识");
        }
    }
}
