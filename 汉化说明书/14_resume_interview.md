# 14 简历表达与面试准备

## 今天要建立的理解

项目完成后，真正影响求职的是你怎么讲它。你不能只说“我实现了增删改查”。你要说清楚业务背景、技术选择、你解决的问题、遇到的风险、如何验证结果。

## 你要重点考虑的问题

先考虑简历表达。每条 bullet 都应该体现一个能力：业务系统设计、事务一致性、Redis 幂等、RabbitMQ 异步、权限控制、日志审计、Docker 部署。

再考虑面试故事。面试官可能不关心你有多少接口，而会问：库存超卖怎么办？Redis 用在哪里？为什么用 MQ？如果订单失败变多你怎么排查？权限怎么设计？

还要考虑项目演示。演示顺序要短而完整：登录，查看 Dashboard，创建商品，调整库存，创建订单，模拟异常，查看工单，查看操作日志。这样能在几分钟内展示项目闭环。

## 建议你查什么

推荐关键词：

- Java backend project resume bullet points
- Spring Boot project interview questions
- order inventory system interview questions
- Redis idempotency interview explanation
- RabbitMQ project interview explanation

你可以看岗位描述，把 JD 里的关键词映射到你的项目：Java、Spring Boot、SQL、API、AWS/Docker、support、troubleshooting、business systems。

## 怎么问 AI 更有效

可以问：

“这是我的项目功能和技术栈。请你根据澳洲初级 Java 后端岗位，帮我写 4 条简历 bullet，要求突出业务系统、事务、Redis、MQ、部署，不要写得像培训项目。”

也可以问：

“请你扮演 Java 实习面试官，围绕我的订单库存工单项目连续追问 10 个问题，并在我回答后指出哪里不够清楚。”

## 你应该产出的东西

你需要完成：

- 简历项目描述
- 3-5 条简历 bullet
- 3 分钟项目介绍稿
- 10 个常见面试问题和答案
- GitHub README 最终版
- 演示流程清单

## 推荐简历表达方向

基于 Java Spring Boot 设计并实现企业级订单库存与运营工单管理系统，支持用户认证、角色权限、商品库存管理、订单创建、异常工单处理和操作日志审计。使用 MySQL 事务保证订单创建与库存扣减一致性，使用 Redis 实现防重复提交和接口限流，并通过 RabbitMQ 解耦订单后续处理流程。项目支持 Swagger 接口文档和 Docker Compose 一键部署，模拟真实企业后台系统的开发、运维和异常排查场景。

## 复盘问题

- 我的简历是否突出了解决问题，而不是罗列技术？
- 我能不能讲清楚每个技术为什么用？
- 项目里最容易被追问的风险点是什么？
- 如果面试官让我现场画架构图，我能不能画出来？
