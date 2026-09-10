package edu.njust.narrativestudio.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.njust.narrativestudio.entity.AccountActionToken;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AccountActionTokenMapper extends BaseMapper<AccountActionToken> {
    @Select("SELECT * FROM account_action_token WHERE token_hash=#{hash}")
    AccountActionToken find(byte[] hash);
    @Select("SELECT * FROM account_action_token WHERE id=#{id} FOR UPDATE")
    AccountActionToken lock(Long id);
}
