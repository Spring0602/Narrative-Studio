package edu.njust.narrativestudio;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("edu.njust.narrativestudio.mapper")
public class NarrativeStudioApplication {
    public static void main(String[] args) {
        SpringApplication.run(NarrativeStudioApplication.class, args);
    }
}
