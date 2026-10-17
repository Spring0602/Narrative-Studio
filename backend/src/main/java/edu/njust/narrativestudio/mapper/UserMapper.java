package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.User;

// 【数据库访问】lockById 锁用户行，用于改密、发凭据和状态更新串行化，不能去掉 FOR UPDATE。
// 【修改提示】SQL 参数使用 #{...} 绑定，不能拼接用户输入；Mapper 不替代业务权限检查。新增字段需保证实体与真实列一致。
public interface UserMapper extends BaseMapper<User> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
    User lockById(Long id);
}
