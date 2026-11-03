# Redis 运维平台：人工部署手册

本文档只说明 **Redis 运维平台后端** 的人工部署。执行器如何部署，请查看执行器项目自己的 `README.md`。

下面以 Linux、systemd、Java 8、PostgreSQL 为例，推荐安装目录为：

```text
/opt/redis-platform
```

如果生产环境使用其他目录，请把本文命令和 `application.yml` 中的路径一起修改。路径必须是干净的绝对路径，不能带首尾空格，也不能包含 `.` 或 `..` 路径段。

## 1. 部署前准备

平台服务器需要：

- JDK 8；
- Maven 3.6 或更高版本（仅构建机器需要）；
- PostgreSQL；
- 可以从执行器服务器访问的平台端口，默认 `8100`；
- 一个专门运行平台的 Linux 账户，本文示例为 `redisplatform`。

检查 Java：

```bash
java -version
```

输出中应包含 Java 8。若 Java 不在 `/usr/bin/java`，后面的 systemd 文件需要填写 `command -v java` 返回的真实路径。

## 2. 构建平台 JAR

在构建机器进入平台项目：

```bash
cd redis-monitor-platform
mvn clean package
```

构建成功后得到：

```text
target/redis-monitor-platform.jar
```

正式投产前建议执行一次全量测试：

```bash
mvn test
```

## 3. 创建运行账户和目录

以下命令在平台服务器执行。若 `redisplatform` 已存在，跳过 `useradd`：

```bash
sudo useradd --system --home-dir /opt/redis-platform --shell /sbin/nologin redisplatform

sudo install -d -o redisplatform -g redisplatform -m 0750 /opt/redis-platform
sudo install -d -o redisplatform -g redisplatform -m 0750 /opt/redis-platform/data/versionPackages
sudo install -d -o redisplatform -g redisplatform -m 0750 /opt/redis-platform/data/nodeConfigs
sudo install -d -o redisplatform -g redisplatform -m 0750 /opt/redis-platform/data/nodeConfigs/current
sudo install -d -o redisplatform -g redisplatform -m 0750 /opt/redis-platform/data/nodeConfigs/staging
sudo install -d -o redisplatform -g redisplatform -m 0750 /opt/redis-platform/exports
```

平台启动时会严格检查以下目录，缺失或权限不正确会直接拒绝启动：

- `/opt/redis-platform/data/versionPackages`；
- `/opt/redis-platform/data/nodeConfigs`；
- `/opt/redis-platform/data/nodeConfigs/current`；
- `/opt/redis-platform/data/nodeConfigs/staging`。

## 4. 上传程序和数据库脚本

从构建机器上传文件，下面的 `platform-server` 替换为平台服务器地址：

```bash
scp target/redis-monitor-platform.jar platform-server:/tmp/
scp src/main/resources/db/postgresql/schema.sql platform-server:/tmp/
```

在平台服务器安装文件：

```bash
sudo install -o redisplatform -g redisplatform -m 0640 /tmp/redis-monitor-platform.jar /opt/redis-platform/redis-monitor-platform.jar
sudo install -o redisplatform -g redisplatform -m 0640 /tmp/schema.sql /opt/redis-platform/schema.sql
```

## 5. 初始化 PostgreSQL

先由 DBA 创建生产数据库和专用账号。以下名称仅为示例：

```text
数据库：redis_monitor
账号：redis_monitor
```

执行正式建表脚本，命令会提示输入数据库密码：

```bash
psql -h DB_HOST -p 5432 -U redis_monitor -d redis_monitor -W \
  -f /opt/redis-platform/schema.sql
```

将 `DB_HOST` 替换为真实 PostgreSQL 地址。执行结束后确认没有 `ERROR`。

生产库只使用：

```text
src/main/resources/db/postgresql/schema.sql
```

不要在生产库运行测试重建脚本或测试数据脚本。

## 6. 创建外置 application.yml

```bash
sudo touch /opt/redis-platform/application.yml
sudo chown redisplatform:redisplatform /opt/redis-platform/application.yml
sudo chmod 600 /opt/redis-platform/application.yml
sudo vi /opt/redis-platform/application.yml
```

写入以下内容，并替换所有尖括号占位符：

```yaml
server:
  port: 8100

platform:
  register-code: <平台与全部执行器共同使用的注册码>

  version-package:
    storage-dir: /opt/redis-platform/data/versionPackages
    max-bytes: 52428800

  node-config:
    config-dir: /opt/redis-platform/data/nodeConfigs

  report:
    export-dir: /opt/redis-platform/exports
    download-prefix: /report/download

  alarm:
    notice:
      enabled: false
      receivers:
        - ops@example.com

spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://<数据库IP或域名>:5432/redis_monitor
    username: redis_monitor
    password: <数据库密码>
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8

logging:
  level:
    com.danuvius.platform: INFO
```

注意：

- `platform.register-code` 必须与每台执行器完全一致；
- 数据库密码和注册码不要提交到 Git；
- 未配置真实 SMTP 服务器前，保持邮件通知关闭；
- `storage-dir` 和 `config-dir` 不能写相对路径；
- 不要把 Windows 开发路径复制到 Linux 生产配置中。

## 7. 配置 systemd

```bash
sudo vi /etc/systemd/system/redis-monitor-platform.service
```

写入：

```ini
[Unit]
Description=Redis Monitor Platform
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=redisplatform
Group=redisplatform
WorkingDirectory=/opt/redis-platform
ExecStart=/usr/bin/java -jar /opt/redis-platform/redis-monitor-platform.jar --spring.config.additional-location=file:/opt/redis-platform/application.yml
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

加载并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now redis-monitor-platform
sudo systemctl status redis-monitor-platform --no-pager
```

查看实时日志：

```bash
sudo journalctl -u redis-monitor-platform -f
```

## 8. 启动验收

检查端口：

```bash
ss -lntp | grep 8100
```

检查平台接口：

```bash
curl -i http://127.0.0.1:8100/agent/ping
```

检查关键目录权限：

```bash
sudo -u redisplatform test -r /opt/redis-platform/data/versionPackages
sudo -u redisplatform test -w /opt/redis-platform/data/versionPackages
sudo -u redisplatform test -x /opt/redis-platform/data/versionPackages
sudo -u redisplatform test -r /opt/redis-platform/data/nodeConfigs/current
sudo -u redisplatform test -w /opt/redis-platform/data/nodeConfigs/current
sudo -u redisplatform test -r /opt/redis-platform/data/nodeConfigs/staging
sudo -u redisplatform test -w /opt/redis-platform/data/nodeConfigs/staging
```

权限检查没有输出且退出码为 `0` 即为通过。

## 9. 首次纳管前的平台准备

假设现有生产服务器为：

```text
10.0.0.11  Master
10.0.0.12  Replica
10.0.0.13  Replica
```

创建对应目录：

```bash
sudo -u redisplatform mkdir -p /opt/redis-platform/data/nodeConfigs/current/10.0.0.11
sudo -u redisplatform mkdir -p /opt/redis-platform/data/nodeConfigs/current/10.0.0.12
sudo -u redisplatform mkdir -p /opt/redis-platform/data/nodeConfigs/current/10.0.0.13
```

分别放入每台服务器对应的可信配置：

```text
current/10.0.0.11/redis.conf
current/10.0.0.11/sentinel.conf
current/10.0.0.12/redis.conf
current/10.0.0.12/sentinel.conf
current/10.0.0.13/redis.conf
current/10.0.0.13/sentinel.conf
```

要求：

- 文件必须是对应服务器实际使用配置的可信副本；
- 文件必须非空、可读；
- 平台副本必须与对应执行器的备份配置一致；
- 首次纳管不要求先上传 Redis 安装包。

三台执行器注册并正常采集后，再在前端执行首次纳管。

## 10. 服务器切换前的平台准备

服务器切换前，在 `staging/{目标服务器IP}` 下准备三台目标服务器的配置：

```text
staging/10.0.1.11/redis.conf
staging/10.0.1.11/sentinel.conf
staging/10.0.1.12/redis.conf
staging/10.0.1.12/sentinel.conf
staging/10.0.1.13/redis.conf
staging/10.0.1.13/sentinel.conf
```

必须确认：

- 目标主节点的 `redis.conf` 没有生效的 `replicaof` 或 `slaveof`；
- 两个目标从节点都通过 `replicaof` 指向目标主节点；
- 三份 `sentinel.conf` 使用相同的主节点名称并监控本次目标主节点；
- 配置中的 `dir`、`logfile`、`pidfile` 与执行器允许的目录一致。

Redis 源码包必须通过前端上传并启用，不要手工把文件放进 `versionPackages` 后直接修改数据库。

## 11. 防火墙要求

平台服务器至少需要：

- 允许前端或运维人员访问平台端口 `8100`；
- 允许所有执行器访问平台端口 `8100`；
- 允许平台主动访问各执行器端口，默认 `9101`；
- 允许平台访问 PostgreSQL，默认 `5432`。

只向必要网段开放，不建议直接对公网开放。

## 12. 更新平台版本

先备份旧 JAR：

```bash
sudo cp /opt/redis-platform/redis-monitor-platform.jar \
  /opt/redis-platform/redis-monitor-platform.jar.bak
```

停止、替换并启动：

```bash
sudo systemctl stop redis-monitor-platform
sudo install -o redisplatform -g redisplatform -m 0640 \
  /tmp/redis-monitor-platform.jar \
  /opt/redis-platform/redis-monitor-platform.jar
sudo systemctl start redis-monitor-platform
sudo systemctl status redis-monitor-platform --no-pager
```

不要删除 `data`、`exports`、`application.yml` 或数据库数据。

## 13. 常见启动失败

### 报错“必须使用绝对路径”

检查 `platform.version-package.storage-dir` 和 `platform.node-config.config-dir`，不能使用 `./data`。

### 报错“不能包含首尾空格”或“不能包含 . 或 .. 路径段”

按报错原样修正配置。平台不会自动修正错误路径。

### 报错目录不存在、不可读、不可写或不可访问

重新执行第 3 节的 `install -d`，并检查：

```bash
namei -l /opt/redis-platform/data/nodeConfigs/current
```

### 数据库连接失败

确认数据库地址、端口、库名、账号、密码、SSL 要求和防火墙配置。

### 执行器注册失败

确认平台与执行器使用相同注册码、执行器可以访问平台 `8100` 端口，并检查平台日志是否存在数据库错误。

## 14. 平台投产检查清单

- [ ] Java 8 已安装；
- [ ] PostgreSQL 正式库和专用账号已创建；
- [ ] `schema.sql` 已执行且没有错误；
- [ ] 平台 JAR 和外置 `application.yml` 已放置；
- [ ] 数据库密码和注册码已替换；
- [ ] `versionPackages`、`nodeConfigs/current`、`nodeConfigs/staging` 已创建；
- [ ] 所有目录属于平台运行账户且具备读写访问权限；
- [ ] systemd 服务已启用并正常运行；
- [ ] `8100` 端口已监听；
- [ ] `/agent/ping` 能收到响应；
- [ ] 平台与执行器之间的网络已经放通；
- [ ] 首次纳管前，`current/{IP}` 中的六份配置已经准备；
- [ ] 服务器切换前，`staging/{IP}` 配置和目标版本安装包已经准备。
