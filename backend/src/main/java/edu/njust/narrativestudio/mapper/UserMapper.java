package edu.njust.narrativestudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.User;

public interface UserMapper extends BaseMapper<User> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
    User lockById(Long id);
}
