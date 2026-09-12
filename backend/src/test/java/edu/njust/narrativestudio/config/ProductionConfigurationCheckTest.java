package edu.njust.narrativestudio.config;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
class ProductionConfigurationCheckTest {
    @Test void missingAndDevelopmentSecretsFailClosed() {
        assertThrows(IllegalStateException.class,()->new ProductionConfigurationCheck(new MockEnvironment()));
        assertThrows(IllegalStateException.class,()->new ProductionConfigurationCheck(new MockEnvironment()
                .withProperty("JWT_SECRET","dev-only-change-this-secret-32-bytes-minimum")));
    }
    @Test void explicitProductionConfigurationIsAccepted() {
        var env=new MockEnvironment().withProperty("JWT_SECRET","a-test-only-non-development-key-32-bytes")
                .withProperty("DB_URL","jdbc:mysql://localhost/test").withProperty("DB_USERNAME","test")
                .withProperty("DB_PASSWORD","test-only-password").withProperty("CORS_ALLOWED_ORIGIN","https://studio.example.com");
        assertDoesNotThrow(()->new ProductionConfigurationCheck(env));
    }
}
