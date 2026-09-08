package edu.njust.narrativestudio.service;
import edu.njust.narrativestudio.dto.PlaytestDtos.*;
public interface PlaytestService {
    SessionView start(Long user,Long project);
    SessionView get(Long user,Long project,Long session);
    SessionView advance(Long user,Long project,Long session,Long choice,int expectedStepNo);
    SessionView restart(Long user,Long project,Long session);
    SessionView stop(Long user,Long project,Long session);
    Page<SessionView> list(Long user,Long project,int page,int size);
    Page<StepView> steps(Long user,Long project,Long session,int page,int size);
    record Page<T>(java.util.List<T> items,int page,int size,long total,long pages) {}
}
