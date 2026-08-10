项目结构
CodeFix_Java/
├── .idea/                              # IDEA 项目配置
│
├── src/
│   └── main/
│       └── java/
│           └── com.xd/
│               │
│               ├── CodeFixApplication.java        # Spring Boot 启动类
│               │
│               ├── client/                        # 外部服务客户端
│               │   └── (外部API调用封装)
│               │
│               ├── config/                        # 配置类
│               │   ├── AppConfig.java             # 应用通用配置
│               │   ├── RedisConfig.java           # Redis 配置
│               │   ├── RocketMQConsumerConfig.java # RocketMQ 消费者配置
│               │   ├── RocketMQProducerConfig.java # RocketMQ 生产者配置
│               │   └── WebConfig.java             # Web 配置（CORS、拦截器等）
│               │
│               ├── controller/                    # 控制器层（HTTP 接口）
│               │   ├── AuditController.java       # 审计/审查接口
│               │   ├── JavaParserController.java  # Java 解析接口
│               │   └── ValidateController.java    # 校验接口
│               │
│               ├── convert/                       # 对象转换器
│               │   └── AIContentConvert.java      # AI 内容转换
│               │
│               ├── exception/                     # 异常处理
│               │   ├── BusinessException.java     # 业务异常
│               │   ├── GlobalExceptionHandler.java # 全局异常处理器
│               │   └── MqSendException.java       # MQ 发送异常
│               │
│               ├── interceptors/                  # 拦截器
│               │   └── RateLimitInterceptor.java  # 限流拦截器
│               │
│               ├── mapper/                        # MyBatis Mapper 层
│               │   └── (XxxMapper.java)
│               │
│               ├── model/                         # 数据模型层
│               │   ├── do/                        # 数据对象（持久层）
│               │   │   └── (XxxDO.java)
│               │   ├── dto/                       # 数据传输对象（业务层）
│               │   │   ├── AgentMessageDTO.java
│               │   │   ├── AuditReportDTO.java
│               │   │   ├── CodeIssueDTO.java
│               │   │   ├── CodeSmellDTO.java
│               │   │   ├── IssueStatisticsDTO.java
│               │   │   └── ReportMetadataDTO.java
│               │   ├── enums/                     # 枚举类
│               │   │   ├── AuditTaskStatusEnum.java
│               │   │   └── SmellSeverityEnum.java
│               │   └── vo/                        # 视图对象（展现层）
│               │       └── (XxxVO.java)
│               │
│               ├── mq/                            # 消息队列
│               │   └── message/                   # 消息定义
│               │       ├── BaseMessage.java       # 消息基类
│               │       ├── MessageHandler.java    # 消息处理器
│               │       ├── MQListener.java        # MQ 监听器
│               │       └── MQProducer.java        # MQ 生产者
│               │
│               ├── service/                       # 业务服务层
│               │   ├── impl/                      # 服务实现
│               │   │   ├── AuditHandlerService.java
│               │   │   └── TaskDispatcher.java
│               │   └── (接口定义)
│               │
│               ├── util/                          # 工具类
│               │   └── (XxxUtil.java)
│               │
│               └── validator/                     # 校验器
│                   └── JavaSyntaxValidator.java   # Java 语法校验
│
├── resources/                      # 资源文件
│   ├── application.yml
│   ├── application-dev.yml
│   └── mapper/
│
├── pom.xml                         # Maven 依赖配置
└── target/                         # 编译输出