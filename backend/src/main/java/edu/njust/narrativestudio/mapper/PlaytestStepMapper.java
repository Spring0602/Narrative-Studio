package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.PlaytestStep;

// 【数据库访问】继承 BaseMapper<PlaytestStep>，复用增删改查；排序/筛选通常写在 Service 的查询包装器中。
// 【修改提示】SQL 参数使用 #{...} 绑定，不能拼接用户输入；Mapper 不替代业务权限检查。新增字段需保证实体与真实列一致。
public interface PlaytestStepMapper extends BaseMapper<PlaytestStep> {}
