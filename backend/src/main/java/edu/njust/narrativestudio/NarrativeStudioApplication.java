package edu.njust.narrativestudio;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("edu.njust.narrativestudio.mapper")
// 【启动链路】main 调用 SpringApplication.run，创建容器并启动 Web 服务；@SpringBootApplication 扫描本包下组件，@MapperScan 注册数据库 Mapper。
// 【修改提示】端口、数据库地址在配置中改，不在 main 写连接代码；移动启动类/包名需同步扫描范围，避免 Controller/Mapper 找不到。
public class NarrativeStudioApplication {
    public static void main(String[] args) {
        SpringApplication.run(NarrativeStudioApplication.class, args);
    }
}
