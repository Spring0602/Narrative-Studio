package edu.njust.narrativestudio.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.AccountActionToken;
import org.apache.ibatis.annotations.*;
@Mapper
// 【数据库访问】find 用摘要找凭据，lock 用 FOR UPDATE 锁凭据行；外层固定先锁用户再锁凭据。
// 【修改提示】SQL 参数使用 #{...} 绑定，不能拼接用户输入；Mapper 不替代业务权限检查。新增字段需保证实体与真实列一致。
public interface AccountActionTokenMapper extends BaseMapper<AccountActionToken> {
    @Select("SELECT * FROM account_action_token WHERE token_hash=#{hash}")
    AccountActionToken find(byte[] hash);
    @Select("SELECT * FROM account_action_token WHERE id=#{id} FOR UPDATE")
    AccountActionToken lock(Long id);
}
