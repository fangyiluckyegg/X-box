package com.prj;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * [P1-UPGRADE] 最小冒烟测试基线：验证 Spring 上下文在升级到 Boot 3.2 后能够成功启动。
 * <p>
 * 使用 dev profile 激活，与开发环境一致（关键凭证弱默认值仅打印 WARN，不阻止启动）。
 * 在 Docker 生产构建中该测试被 {@code -Dmaven.test.skip=true} 跳过；
 * 本地运行需在 MySQL / Redis 可用环境下执行（DataSource / Redis 连接为懒加载，
 * 上下文刷新阶段不会主动建连，因此即使依赖中间件暂不可达也可能通过）。
 */
@SpringBootTest
@ActiveProfiles("dev")
public class PrjBackendApplicationTests
{
    @Test
    void contextLoads()
    {
        // 上下文成功加载即通过
    }
}
