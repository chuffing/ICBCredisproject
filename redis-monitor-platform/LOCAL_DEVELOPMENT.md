# 本地运行说明

## 1. 本地PostgreSQL实例

本地开发数据库使用已经安装的PostgreSQL 14程序，数据目录为：

```text
C:\Users\86135\AppData\Local\redis-monitor-platform-postgres\data
```

该实例与原来的PostgreSQL服务相互独立，只监听`127.0.0.1:5433`，不需要Docker，也不需要原来PostgreSQL服务的管理员密码。

启动本地开发数据库：

```powershell
& "D:\postgresql\bin\pg_ctl.exe" `
  -D "C:\Users\86135\AppData\Local\redis-monitor-platform-postgres\data" `
  -l "C:\Users\86135\AppData\Local\redis-monitor-platform-postgres\postgres.log" `
  -o "-p 5433 -h 127.0.0.1" `
  start
```

## 2. 使用local配置启动平台

在IDEA的Spring Boot启动配置中增加：

```text
--spring.profiles.active=local
```

也可以通过Maven启动：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

平台第一次连接本地数据库时，会自动执行：

```text
src/main/resources/db/postgresql/local_schema.sql
```

初始化脚本使用`CREATE TABLE IF NOT EXISTS`，后续重新启动平台不会删除已有数据。

## 3. 本地数据库连接信息

```text
地址：127.0.0.1:5433
数据库：redis_monitor_platform
用户名：redis_platform
认证：仅限本机的开发实例使用trust认证
```

这些值只用于本地开发，也可以通过以下环境变量覆盖：

```text
LOCAL_POSTGRES_URL
LOCAL_POSTGRES_USERNAME
LOCAL_POSTGRES_PASSWORD
```

## 4. 停止本地数据库

停止实例但保留全部本地数据：

```powershell
& "D:\postgresql\bin\pg_ctl.exe" `
  -D "C:\Users\86135\AppData\Local\redis-monitor-platform-postgres\data" `
  stop
```

