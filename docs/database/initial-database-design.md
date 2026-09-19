# OpsFlow 初始数据库结构与业务流程

## 文档定位

本文档记录 OpsFlow 初始版本已经确认的数据库边界、表关系和核心业务流程。它是项目实现阶段的设计基线，不替代 SQL 迁移脚本，也不替代学习资料中的概念讲解。

当前设计共包含 12 张表，覆盖用户权限、商品库存、订单、工单和审计五个领域。

## 设计图

| 图 | 用途 | 链接 |
| --- | --- | --- |
| 简化 ERD | 展示核心实体和关系，适合放在 README、项目介绍和面试材料中 | [Open Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) |
| 初始物理 ERD | 展示字段、主键、外键和实现关系，供建表与代码开发参考 | [Open Initial Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) |
| 业务流程图 | 展示订单、库存、审计和异常工单的英文业务流程 | [Open Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) |

## 表结构总览

### 用户与权限

| 表 | 职责 |
| --- | --- |
| `users` | 保存用户账号、身份和启用状态 |
| `roles` | 定义系统角色 |
| `user_roles` | 连接用户与角色，支持一个用户拥有多个角色 |

关系：`users` 与 `roles` 是多对多关系，通过 `user_roles` 建立关联。

### 商品与库存

| 表 | 职责 |
| --- | --- |
| `products` | 保存商品基础信息、价格和销售状态 |
| `inventory` | 保存商品当前可用库存及并发控制信息 |
| `inventory_transactions` | 保存每次入库、扣减、回滚或调整的库存流水 |

初始版本中，一个商品对应一条当前库存记录，即 `products` 与 `inventory` 为一对一关系。一个商品可以产生多条库存流水。

`inventory` 表示当前状态，适合快速查询；`inventory_transactions` 表示历史变化，负责追溯库存为何增加或减少。

### 订单

| 表 | 职责 |
| --- | --- |
| `orders` | 保存订单级信息，例如下单用户、订单号、总金额和状态 |
| `order_items` | 保存订单中的商品快照、购买数量和成交单价 |

一个用户可以创建多个订单，一个订单必须包含一个或多个订单明细。每条 `order_items` 只关联一种商品；购买多种商品时，为每种商品分别创建一条明细。因此：

- `users` 1:N `orders`
- `orders` 1:N `order_items`
- `products` 1:N `order_items`

订单明细应保留成交时的商品名称或 SKU、单价等快照。后续修改商品资料时，历史订单仍能保持原始业务含义。

### 工单

| 表 | 职责 |
| --- | --- |
| `tickets` | 保存异常工单、固定创建人、当前负责人、状态和优先级 |
| `ticket_assignment_history` | 保存工单每次指派或转派的前后负责人及操作时间 |
| `ticket_comments` | 保存处理过程中的沟通、备注和结论 |

工单创建人由 `created_by` 固定记录。`assigned_to` 只保存当前负责人，同一时间只有一个当前负责人；负责人发生变化时，在 `ticket_assignment_history` 中新增记录，不覆盖历史。

一张工单可以没有负责人，也可以在生命周期中被多次转派。每次转派至少应记录原负责人、新负责人、操作人、原因和时间。

### 审计

| 表 | 职责 |
| --- | --- |
| `operation_logs` | 保存重要业务操作、操作者、目标对象及必要的前后状态 |

初始版本采用通用目标设计，通过 `target_type` 和 `target_id` 指向订单、库存或工单等业务对象。该关系由应用层维护，不使用一组互斥的数据库外键。

## 核心关系摘要

| 来源 | 关系 | 目标 | 说明 |
| --- | --- | --- | --- |
| `users` | M:N | `roles` | 通过 `user_roles` 关联 |
| `users` | 1:N | `orders` | 用户创建订单 |
| `orders` | 1:N | `order_items` | 一个订单包含至少一条明细 |
| `products` | 1:N | `order_items` | 每条明细只引用一种商品 |
| `products` | 1:1 | `inventory` | 初始版本每个商品只有一个库存记录 |
| `products` | 1:N | `inventory_transactions` | 商品产生多条库存流水 |
| `orders` | 1:N | `tickets` | 一个订单可因不同异常产生多张工单 |
| `users` | 1:N | `tickets` | 分别作为创建人和当前负责人 |
| `tickets` | 1:N | `ticket_assignment_history` | 保存完整转派历史 |
| `tickets` | 1:N | `ticket_comments` | 保存处理记录和沟通内容 |
| `users` | 1:N | `operation_logs` | 用户执行多个被审计的操作 |

## 订单创建流程

1. 用户通过认证并提交包含一种或多种商品的订单请求。
2. 系统校验商品是否存在、是否可销售以及请求数量是否合法。
3. 系统校验库存，并使用条件更新或锁机制防止并发超卖。
4. 在同一个数据库事务中创建 `orders`、创建多条 `order_items`、扣减 `inventory`，并写入 `inventory_transactions`。
5. 系统写入必要的 `operation_logs`，记录订单和库存操作。
6. 所有关键步骤成功后提交事务；任一步骤失败则回滚，不能留下只有订单或只有库存扣减的半完成状态。
7. 对需要人工处理的异常创建 `tickets`，再进入指派、调查、转派和解决流程。

库存不足属于可预期的业务失败，应直接返回明确结果且不扣减库存。数据库错误或其他系统异常必须触发事务回滚；是否自动创建异常工单，可在实现阶段根据错误类型确定。

## 初始约束

- 主键统一使用数值型 `id`，业务单号另设唯一字段。
- 外键列和高频查询字段需要建立索引。
- 用户名、角色编码、商品 SKU 和订单号等业务唯一值应设置唯一约束。
- 数量必须大于零，金额不能为负数。
- `order_items` 的成交单价和商品快照创建后不应跟随商品表变化。
- 库存扣减、订单及明细创建必须处于同一个事务边界。
- 库存流水、工单指派历史和操作日志属于追溯数据，原则上只新增、不覆盖。
- 业务状态使用受控枚举，并在服务层限制不合法的状态跳转。
- 主要业务表保留 `created_at` 和 `updated_at`；历史流水表至少保留创建时间。

## 后续演进

当前模型是适合初始开发和项目展示的版本。出现以下需求时再扩展，避免提前增加复杂度：

- 支持多仓库时，引入 `warehouses`，并将库存调整为商品与仓库的组合关系。
- 需要预占库存时，将库存拆分为可用量、锁定量等字段，并设计释放流程。
- 需要支付与退款时，增加支付、退款及对应流水表。
- 需要发货履约时，增加地址、包裹和物流相关表。
- 通用审计日志查询压力增大时，按业务类型拆分或归档日志。

正式编码前，应以物理 ERD 为基础生成首版 SQL 迁移脚本；字段或关系发生变化时，需要同时更新迁移脚本、物理 ERD 和本文档。
