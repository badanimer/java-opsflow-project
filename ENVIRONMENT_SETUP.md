# OpsFlow 本地开发环境配置文档

> 记录时间：2026-09-12
> 操作系统：Windows 11 Home (10.0.26200)
> 说明：本机是一台**虚拟机**，不支持嵌套虚拟化（VT-x/AMD-V 未透传），因此 Docker Desktop 虽已安装但**无法正常运行**，所有中间件均改为 **Windows 原生服务** 方式安装。

---

## 1. 环境总览

| 组件 | 用途 | 版本 | 安装方式 | 状态 |
|---|---|---|---|---|
| JDK | Java 运行/编译环境 | Microsoft Build of OpenJDK 17 (17.0.20.1) | winget | ✅ 已装 |
| Maven | Spring Boot 项目构建工具 | 3.9.16 | 手动下载解压 | ✅ 已装 |
| MySQL | 主数据库 | 8.4.9 | winget（MSI，手动初始化） | ✅ Windows 服务运行中，开机自启 |
| Redis | 缓存 / 幂等 / 限流 | 8.10.1（redis-windows-fork，MSYS2 构建） | winget + NSSM 注册为服务 | ✅ Windows 服务运行中，开机自启 |
| Erlang/OTP | RabbitMQ 运行依赖 | 27.3.4.17 | 手动下载安装 | ✅ 已装 |
| RabbitMQ | 消息队列（异步任务） | 4.3.5 | 手动下载安装 | ✅ Windows 服务运行中，开机自启，管理插件已启用 |
| Node.js | 前端构建/运行 | v21.7.0 | 系统自带 | ✅ 已装 |
| npm | 前端包管理 | 10.5.0 | 系统自带 | ✅ 已装 |
| Git | 版本控制 | 2.44.0.windows.1 | 系统自带 | ✅ 已装 |
| Docker Desktop | 容器化部署 | 4.90.0 | winget | ⚠️ 已装但不可用（宿主机不支持嵌套虚拟化） |

---

## 2. 详细配置信息

### 2.1 JDK 17

- 安装路径：`C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`
- 环境变量：`JAVA_HOME` = 上述路径（用户级），已加入 `PATH`
- 验证命令：
  ```powershell
  java -version
  ```

### 2.2 Maven 3.9.16

- 安装路径：`C:\Tools\maven`
- 环境变量：`M2_HOME` = `C:\Tools\maven`，`bin` 目录已加入 `PATH`
- 本地仓库（默认）：`C:\Users\Admin\.m2\repository`
- 验证命令：
  ```powershell
  mvn -version
  ```
- 建议：如果后续下载依赖慢，可以在 `C:\Users\Admin\.m2\settings.xml` 里配置阿里云等国内镜像仓库。

### 2.3 MySQL 8.4.9

- 安装路径：`C:\Program Files\MySQL\MySQL Server 8.4`
- 数据目录：`C:\ProgramData\MySQL\MySQL Server 8.4\Data`
- 配置文件：`C:\ProgramData\MySQL\MySQL Server 8.4\my.ini`
- Windows 服务名：`MySQL84`（启动类型 Automatic）
- 端口：`3306`
- 账号：`root`
- 密码：`OpsFlow@2026`  ⚠️ 仅供本地开发使用，**不要提交到代码仓库或写死在配置文件里**，正式项目里请用环境变量 / `.env` 注入
- 字符集：`utf8mb4`（server 级默认已设置）
- `mysql.exe` 已加入 `PATH`

常用命令：
```powershell
# 启动/停止/重启服务
Start-Service MySQL84
Stop-Service MySQL84
Restart-Service MySQL84

# 连接数据库
mysql -u root -p
```

Spring Boot `application.yml` 参考配置：
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/opsflow?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root
    password: OpsFlow@2026
    driver-class-name: com.mysql.cj.jdbc.Driver
```
> 记得先手动创建数据库：`CREATE DATABASE opsflow DEFAULT CHARACTER SET utf8mb4;`

### 2.4 Redis 8.10.1

- 安装路径（可执行文件）：
  `C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork_Microsoft.Winget.Source_8wekyb3d8bbwe\Redis-8.10.1-Windows-x64-msys2`
- 数据/配置目录：`C:\ProgramData\Redis`（`redis.conf` 在此）
- Windows 服务名：`Redis`（通过 **NSSM** 注册，启动类型 Automatic）
  - NSSM 工具路径：`...\NSSM.NSSM_...\nssm-2.24-101-g897c7ad\win64\nssm.exe`
- 端口：`6379`，监听 `127.0.0.1`
- 未设置密码（本地开发默认无密码，`bind 127.0.0.1` 已限制只能本机访问）
- `redis-cli` 未加入全局 PATH（如需使用，进入上述安装目录，或自行加到 PATH）

常用命令：
```powershell
Start-Service Redis
Stop-Service Redis
Restart-Service Redis

# 测试连接
& "C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork_Microsoft.Winget.Source_8wekyb3d8bbwe\Redis-8.10.1-Windows-x64-msys2\redis-cli.exe" -h 127.0.0.1 -p 6379 PING
```

Spring Boot 配置参考：
```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
```

### 2.5 RabbitMQ 4.3.5

- 安装路径：`C:\Program Files\RabbitMQ Server\rabbitmq_server-4.3.5`
- 依赖的 Erlang：`C:\Program Files\Erlang OTP`（27.3.4.17，环境变量 `ERLANG_HOME` 已设置为机器级）
- Windows 服务名：`RabbitMQ`（启动类型 Automatic）
- 端口：
  - `5672`：AMQP 协议端口（应用连接用这个）
  - `15672`：Web 管理界面
- 管理界面：http://localhost:15672
- 默认账号：`guest` / `guest`（**RabbitMQ 默认只允许 guest 账号从本机 localhost 登录**，如需远程访问需新建账号）
- 管理插件 `rabbitmq_management` 已手动启用
- `sbin` 目录已加入 `PATH`，可直接用 `rabbitmqctl`、`rabbitmq-plugins` 等命令

常用命令：
```powershell
Start-Service RabbitMQ
Stop-Service RabbitMQ
Restart-Service RabbitMQ

# 查看状态 / 队列列表
rabbitmqctl status
rabbitmqctl list_queues
```

Spring Boot 配置参考：
```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
    virtual-host: /
```

⚠️ **安装过程中的一个坑**：最初通过 winget 装的 Erlang 是 29.0.6，与 RabbitMQ 4.3.x 不兼容（RabbitMQ 4.3.x 官方只支持 Erlang 27.x，28.x 部分支持，29.x 不支持），启动时报 `incompatible_feature_flags` / `horus extraction_denied` 错误。后来卸载 Erlang 29，改装 Erlang 27.3.4.17 后正常。**以后升级 Erlang 前，先查 [RabbitMQ 官方兼容表](https://www.rabbitmq.com/docs/which-erlang)。**

### 2.6 Docker Desktop（未启用）

- 已通过 winget 安装（4.90.0），但本机检测到：
  - Windows 的 `VirtualMachinePlatform`、`Microsoft-Windows-Subsystem-Linux` 功能均已启用
  - 但硬件虚拟化未透传给本机（`wsl --install` 报错提示未启用虚拟化，`systeminfo` 显示"已检测到一个虚拟机监控程序"）
  - 结论：本机自身就是一台虚拟机，Docker/WSL2 需要宿主机开启**嵌套虚拟化**才能用，这个无法在虚拟机内部解决
- 如果之后换到物理机，或宿主机开启了嵌套虚拟化，可以重新尝试 `wsl --install`，成功后 Docker Desktop 即可正常使用，届时可以把 MySQL/Redis/RabbitMQ 迁移为 `docker-compose` 管理（对应文档第 13 章）。

---

## 3. PATH / 环境变量汇总

以下均为**用户级（User）**环境变量，写入后需要**打开新终端窗口**才会生效：

| 变量名 | 值 |
|---|---|
| `JAVA_HOME` | `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` |
| `M2_HOME` | `C:\Tools\maven` |
| `ERLANG_HOME`（机器级） | `C:\Program Files\Erlang OTP` |

`PATH` 新增条目：
- `C:\Tools\maven\bin`
- `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin`
- `C:\Program Files\MySQL\MySQL Server 8.4\bin`
- `C:\Program Files\RabbitMQ Server\rabbitmq_server-4.3.5\sbin`

---

## 4. 一键自检

新开一个终端，依次执行：

```powershell
java -version
mvn -version
mysql --version
node -v
npm -v
git --version
rabbitmqctl status
```

服务状态检查：
```powershell
Get-Service MySQL84, Redis, RabbitMQ | Select-Object Name, Status, StartType
```

三个服务都应显示 `Running` / `Automatic`（意味着开机会自动启动，不需要每次手动开）。

---

## 5. 后续待办 / 建议

- [ ] 手动创建业务数据库：`CREATE DATABASE opsflow ...`（见 2.3）
- [ ] 用 [Spring Initializr](https://start.spring.io) 或 IDE 生成 Spring Boot 项目骨架（对应文档第 3 章）
- [ ] 把 MySQL root 密码、RabbitMQ 账号等敏感信息改为通过环境变量 / `application-local.yml`（不提交 git）注入，而不是硬编码
- [ ] 前端部分：确认用 Vue 还是 React（文档中两者都提到），再决定是否需要额外装脚手架 CLI（如 `npm create vite@latest`）
- [ ] 如果以后机器换成支持虚拟化的环境，可以考虑迁移到 Docker Compose 统一管理这几个中间件，参考文档第 13 章
