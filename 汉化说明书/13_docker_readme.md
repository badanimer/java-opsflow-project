# 13 Docker 部署与项目整理

## 今天要建立的理解

部署能力会让你的项目明显更像真实工程。面试官不一定真的运行你的项目，但 README 和 Docker Compose 会传达一个信号：你知道项目不是只在自己电脑上能跑。

## 你要重点考虑的问题

先考虑依赖。你的项目需要后端、MySQL、Redis、RabbitMQ、前端。别人如果想运行，最痛苦的是配置环境。所以你要尽量把启动方式写清楚。

再考虑配置。数据库账号、Redis 地址、MQ 地址、JWT secret 不应该写死在代码里。你要理解环境变量和 application 配置之间的关系。

还要考虑 README。README 不是作文，而是项目入口。它应该包含项目介绍、功能、技术栈、架构图或流程图、启动方式、测试账号、接口文档地址和项目亮点。

## 建议你查什么

推荐关键词：

- Spring Boot Dockerfile best practice
- Docker Compose MySQL Redis RabbitMQ Spring Boot
- Spring Boot environment variables application.yml
- project README backend portfolio
- Docker healthcheck MySQL depends_on

不要一开始追求生产级部署。你的目标是让项目可复现、可演示、可理解。

## 怎么问 AI 更有效

可以问：

“这是我的项目依赖：Spring Boot、MySQL、Redis、RabbitMQ、Vue。请你帮我设计适合简历项目的 docker-compose，并解释每个服务的作用。”

也可以问：

“请你从招聘者角度检查我的 README，判断是否能快速看懂项目价值、启动方式和技术亮点。”

## 你应该产出的东西

你需要完成：

- Dockerfile
- docker-compose.yml
- 初始化 SQL
- 环境变量说明
- README 完整版
- 测试账号
- Swagger 地址

最好保留一段“常见启动问题”说明，比如端口占用、数据库连接失败、Redis 未启动。

## 复盘问题

- 别人能不能按 README 跑起来？
- 我有没有把密码或 secret 上传到 GitHub？
- Docker Compose 里每个服务为什么存在？
- 我的 README 是否像真实项目，而不是课程作业？
