# 03 Spring Boot 项目搭建

## 今天要建立的理解

项目骨架决定后面代码会不会越写越乱。你今天不是为了“把 Spring Boot 跑起来”这么简单，而是要理解一个后端项目为什么要分层，为什么 DTO、Entity、VO 不应该混着用，为什么异常处理和统一返回格式最好一开始就设计。

## 你要重点考虑的问题

先考虑包结构。controller 负责接收请求，service 负责业务逻辑，repository 或 mapper 负责数据库，entity 映射表结构，dto 接收请求参数，vo 返回给前端。你要避免把所有逻辑塞进 controller。

再考虑技术选择。JPA 和 MyBatis Plus 都可以。JPA 更偏对象关系映射，MyBatis Plus 更接近 SQL 控制。你可以选一个自己更愿意学的，但要能解释为什么选它。

还要考虑配置管理。数据库地址、Redis 地址、JWT secret 这些不应该硬编码。你要理解 application.yml、环境变量和不同环境配置的概念。

## 建议你查什么

推荐关键词：

- Spring Boot layered architecture
- Spring Boot DTO VO Entity difference
- Spring Boot global exception handler
- Spring Boot response wrapper best practice
- Spring Boot application.yml profile

查资料时重点看“为什么这么分”，不要只复制目录结构。

## 怎么问 AI 更有效

可以这样问：

“我正在搭一个 Spring Boot 订单库存系统。请你帮我设计适合初级项目但不幼稚的包结构，并解释每一层应该放什么、不应该放什么。”

也可以把你的目录贴给 AI：

“这是我的 Spring Boot 项目目录。请你检查有没有分层混乱、命名不清楚、后续不好扩展的问题。”

## 你应该产出的东西

你需要完成一个能启动的后端项目，并有：

- 健康检查接口
- 数据库连接配置
- 统一响应对象
- 全局异常处理雏形
- Swagger 或 Knife4j 页面
- 基础包结构

这时候还不需要功能很多，但骨架要稳。

## 复盘问题

- controller 里应该写多少逻辑？
- DTO 和 Entity 混用会有什么问题？
- 为什么一开始就做统一返回和异常处理？
- 如果别人打开我的项目，能不能快速理解结构？
