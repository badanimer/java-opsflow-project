# 02 数据库设计

## 今天要建立的理解

数据库不是简单建几张表，而是把业务规则固定下来。订单、库存、工单、日志这些模块能不能可靠运行，很大程度取决于表设计是否合理。

你今天要理解：为什么订单要分主表和明细表，为什么库存不能直接写在商品表里，为什么日志表最好不要随便修改，为什么状态字段会影响后续代码复杂度。

## 你要重点考虑的问题

先考虑实体关系。用户可以创建订单，订单包含多个商品，商品有库存，订单异常会产生工单，用户处理工单时会留下操作日志。你要能把这些关系画出来。

再考虑字段含义。每个表不只是有 id 和 name。你需要思考：

- 订单是否需要订单号？
- 库存是否要区分 available、locked、sold？
- 工单是否要优先级？
- 操作日志要不要记录操作前和操作后的值？
- 删除数据是物理删除还是软删除？

还要考虑状态设计。订单状态不要太多，但也不能太少。比如 CREATED、PAID、CANCELLED、FAILED 已经够支撑主要流程。工单状态可以是 OPEN、IN_PROGRESS、RESOLVED、CLOSED。你要想清楚每个状态之间能不能互相跳转。

## 建议你查什么

查数据库设计时，不要只搜“Spring Boot CRUD 表设计”。你要搜业务系统建模。

推荐关键词：

- order table order item table design
- inventory table design available locked quantity
- audit log table design
- ticket system database schema
- soft delete created_at updated_at best practice

查完后，把别人的设计和你的设计对比：哪些字段是业务必须的，哪些字段只是复杂系统才需要的。

## 怎么问 AI 更有效

你可以把自己的表结构发给 AI，然后问：

“这是我为订单库存工单系统设计的 MySQL 表。请你从真实业务系统角度检查：表之间关系是否合理，有没有缺少关键字段，有没有过度设计，哪些字段会影响后续订单一致性？”

也可以问：

“我不确定库存表应该只放 stock_quantity，还是拆成 available_quantity、locked_quantity、sold_quantity。请你用初级 Java 项目的复杂度帮我分析取舍。”

## 你应该产出的东西

你需要有一份 SQL 建表草稿，至少包含：

- users
- roles
- user_roles
- products
- inventory
- orders
- order_items
- tickets
- operation_logs

每张表都应该有 created_at 和 updated_at。对会被停用的数据，例如商品、用户，可以考虑 status 或 deleted 字段。

## 复盘问题

- 我能不能解释订单主表和订单明细表的区别？
- 库存为什么最好单独成表？
- 哪些字段是为了查询，哪些字段是为了业务安全？
- 如果订单创建失败，我的表设计能不能支持排查问题？
