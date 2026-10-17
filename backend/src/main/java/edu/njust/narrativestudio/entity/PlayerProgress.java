package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("player_progress")
// 【持久化模型】对应 player_progress 表；按项目+用户+版本保存跨局 JSON 与修订号，编辑稿 versionKey=0。
// 【修改提示】getter/setter 只读写 Java 对象，新增持久化字段需同步 SQL、DTO/Service 和相关快照/测试；仅展示计算值优先放响应 DTO。
public class PlayerProgress {
    @TableId(type=IdType.AUTO) private Long id;
    private Long projectId,testerId,versionKey,revision;
    private String progressJson;
    public Long getId(){return id;}public void setId(Long v){id=v;}
    public Long getProjectId(){return projectId;}public void setProjectId(Long v){projectId=v;}
    public Long getTesterId(){return testerId;}public void setTesterId(Long v){testerId=v;}
    public Long getVersionKey(){return versionKey;}public void setVersionKey(Long v){versionKey=v;}
    public Long getRevision(){return revision;}public void setRevision(Long v){revision=v;}
    public String getProgressJson(){return progressJson;}public void setProgressJson(String v){progressJson=v;}
}
