package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("account_action_token")
public class AccountActionToken {
    @TableId(type=IdType.AUTO)
    private Long id;private Long userId;private String purpose;private byte[] tokenHash;private String destinationEmail;
    private LocalDateTime expiresAt;private LocalDateTime usedAt;private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getPurpose(){return purpose;} public void setPurpose(String v){purpose=v;}
    public byte[] getTokenHash(){return tokenHash;} public void setTokenHash(byte[] v){tokenHash=v;}
    public String getDestinationEmail(){return destinationEmail;} public void setDestinationEmail(String v){destinationEmail=v;}
    public LocalDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(LocalDateTime v){expiresAt=v;}
    public LocalDateTime getUsedAt(){return usedAt;} public void setUsedAt(LocalDateTime v){usedAt=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
