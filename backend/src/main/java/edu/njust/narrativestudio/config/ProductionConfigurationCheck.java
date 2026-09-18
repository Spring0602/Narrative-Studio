package edu.njust.narrativestudio.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fail closed instead of deploying with the development JWT/database defaults. */
@Component
@Profile("prod")
public class ProductionConfigurationCheck {
    public ProductionConfigurationCheck(Environment env) {
        String secret=required(env,"JWT_SECRET");
        if(secret.getBytes(StandardCharsets.UTF_8).length<32 || secret.startsWith("dev-only-"))
            throw new IllegalStateException("prod requires a non-development JWT_SECRET of at least 32 bytes");
        required(env,"DB_URL");required(env,"DB_USERNAME");
        if("narrative_dev".equals(required(env,"DB_PASSWORD")))
            throw new IllegalStateException("prod must not use the development database password");
        required(env,"CORS_ALLOWED_ORIGIN");
    }
    private static String required(Environment env,String name) {
        String value=env.getProperty(name);
        if(value==null || value.isBlank()) throw new IllegalStateException("prod requires "+name);
        return value;
    }
}
