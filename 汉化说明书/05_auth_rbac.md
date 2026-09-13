# 05 用户登录与权限

## 今天要建立的理解

权限不是为了让项目看起来高级，而是企业后台系统的基本安全要求。订单、库存、工单、日志都不是任何人都能随便改的。你要理解认证和授权的区别：认证回答“你是谁”，授权回答“你能做什么”。

## 你要重点考虑的问题

先考虑登录流程。用户提交用户名和密码，后端验证成功后返回 JWT。前端后续请求带上 token，后端解析 token 得到用户身份和角色。

再考虑角色权限。Admin、Operator、Viewer 应该能做不同的事。你要为每个模块设计权限边界：谁能改库存？谁能取消订单？谁能查看操作日志？谁能分配工单？

还要考虑安全细节。密码不能明文存储。token 过期怎么办？没有 token 返回 401，权限不足返回 403。你不用一开始做得非常复杂，但这些概念要清楚。

## 建议你查什么

推荐关键词：

- Spring Security JWT authentication flow
- RBAC role based access control
- password hashing BCrypt Spring Security
- 401 vs 403 difference
- JWT expiration refresh token simple design

查资料时重点理解流程图，不要只抄配置类。Spring Security 配置比较绕，你可以让 AI 用你的项目结构解释。

## 怎么问 AI 更有效

可以问：

“请你用我的项目场景解释 Spring Security + JWT 的完整请求流程，从登录到访问订单接口，每一步后端做什么。”

也可以问：

“这是我的角色设计：Admin、Operator、Viewer。请你帮我按商品、库存、订单、工单、日志模块设计权限矩阵，并指出哪些权限给错会有业务风险。”

## 你应该产出的东西

你需要完成：

- 登录接口
- JWT 生成和校验
- 密码加密
- 受保护接口
- 角色权限控制
- 401 和 403 错误处理

你还应该有一张权限矩阵，哪怕只是 Markdown 表格。

## 复盘问题

- 认证和授权有什么区别？
- token 里应该放哪些信息，不应该放哪些信息？
- Viewer 为什么不能改库存？
- 如果 token 被盗，会有什么风险？
