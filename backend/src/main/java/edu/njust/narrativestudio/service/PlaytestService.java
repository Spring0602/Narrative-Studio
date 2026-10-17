package edu.njust.narrativestudio.service;
import edu.njust.narrativestudio.dto.PlaytestDtos.*;
public interface PlaytestService {
    // 答辩修改导航：只改分页默认值、排序、错误提示，通常不用修改接口签名。
    // 增加查询参数时，Controller -> 此接口 -> ServiceImpl -> 测试调用要同步修改。
    // 修改 DTO 内部返回字段不必改这里的 SessionView 类型名，但要更新 DTO 的构造调用。
    SessionView startRelease(Long user,Long project,Long release);
    SessionView start(Long user,Long project);
    SessionView get(Long user,Long project,Long session);
    SessionView advance(Long user,Long project,Long session,Long choice,int expectedStepNo);
    SessionView restart(Long user,Long project,Long session);
    SessionView stop(Long user,Long project,Long session);
    Page<SessionView> list(Long user,Long project,int page,int size);
    Page<StepView> steps(Long user,Long project,Long session,int page,int size);
    record Page<T>(java.util.List<T> items,int page,int size,long total,long pages) {}
}
