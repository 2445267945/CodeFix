项目结构
CodeFix/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── audit/
│   │   │           ├── JavaAuditApplication.java      # Spring Boot 启动类
│   │   │           │
│   │   │           ├── controller/                    # 控制器层：接收HTTP请求
│   │   │           │   ├── AuditController.java       # /api/audit 代码分析接口
│   │   │           │   └── HealthController.java      # /health 健康检查
│   │   │           │
│   │   │           ├── service/                       # 业务服务层
│   │   │           │   ├── AuditService.java          # 核心分析服务（编排流程）
│   │   │           │   ├── CodeParserService.java     # JavaParser解析服务
│   │   │           │   └── CacheService.java          # Redis缓存管理
│   │   │           │
│   │   │           ├── client/                        # 外部调用客户端
│   │   │           │   └── PythonAgentClient.java     # 调用Python服务的Feign/WebClient
│   │   │           │
│   │   │           ├── model/                         # 数据模型（内部流转）
│   │   │           │   ├── dto/                       # 数据传输对象（API契约）
│   │   │           │   │   ├── AuditRequest.java      # 请求体
│   │   │           │   │   └── AuditResponse.java     # 响应体
│   │   │           │   ├── entity/                    # 领域实体（内部使用）
│   │   │           │   │   ├── CodeSmell.java         # 代码异味实体
│   │   │           │   │   └── AuditReport.java       # 审计报告实体
│   │   │           │   └── enums/                     # 枚举
│   │   │           │       └── SmellSeverity.java     # 严重等级（HIGH/MEDIUM/LOW）
│   │   │           │
│   │   │           ├── config/                        # 配置类
│   │   │           │   ├── WebClientConfig.java       # WebClient Bean配置
│   │   │           │   ├── RedisConfig.java           # Redis序列化配置
│   │   │           │   └── AsyncConfig.java           # 线程池配置
│   │   │           │
│   │   │           ├── validator/                     # 校验器（二次编译校验）
│   │   │           │   └── JavaSyntaxValidator.java   # 用JavaParser校验修复后的代码
│   │   │           │
│   │   │           ├── exception/                     # 异常处理
│   │   │           │   ├── GlobalExceptionHandler.java # 全局异常处理器
│   │   │           │   └── BusinessException.java     # 自定义业务异常
│   │   │           │
│   │   │           └── util/                          # 工具类
│   │   │               ├── Md5Utils.java              # 代码MD5生成
│   │   │               └── JsonUtils.java             # JSON序列化工具
│   │   │
│   │   └── resources/
│   │       ├── application.yml                        # 主配置文件
│   │       ├── application-dev.yml                    # 开发环境配置
│   │       └── application-prod.yml                   # 生产环境配置
│   │
│   └── test/                                          # 单元测试
│       └── java/com/audit/
│           ├── CodeParserServiceTest.java
│           └── AuditServiceTest.java
│
├── pom.xml                                            # Maven依赖管理
├── Dockerfile                                         # Java服务镜像
└── README.md                                          # 项目说明