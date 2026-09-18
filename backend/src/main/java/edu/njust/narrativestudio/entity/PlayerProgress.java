package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("player_progress")
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
