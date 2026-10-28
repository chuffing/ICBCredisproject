# Redis 运维平台部署与目录准备说明

本文档只说明平台服务器和执行器服务器的目录结构，以及正式使用前必须人工完成的准备工作。内容以当前平台和执行器代码为准。

## 一、总体约定

系统由一台管理平台和多台执行器组成。

- 平台保存 Redis 源码安装包、每台服务器对应的 Redis/Sentinel 配置文件，并负责创建和调度运维任务。
- 每台 Redis 服务器部署一个执行器，执行器只操作本机 Redis 和 Sentinel。
- 平台保存的配置按照服务器 IP 区分，每台服务器可以使用不同配置。
- Redis 正式运行配置和故障恢复备份必须位于不同目录。
- 新服务器切换前只部署执行器，不提前安装 Redis；执行器探测不到 Redis 时只注册自身。
- 已经运行 Redis 的现有生产服务器启动执行器后，由执行器探测并上报真实 Redis 信息。

本文使用以下生产目录作为推荐示例：

    平台服务器：/opt/redis-platform
    执行器服务器：/opt/redis

如果实际服务器使用其他路径，必须同步修改平台和执行器配置，不能只移动文件。

## 二、平台服务器目录结构

建议平台服务从 /opt/redis-platform 目录启动：

    /opt/redis-platform/
    ├── redis-monitor-platform.jar
    ├── application.yml
    ├── data/
    │   ├── versionPackages/
    │   │   └── redis-{version}.tar.gz
    │   └── nodeConfigs/
    │       ├── current/
    │       │   ├── 10.0.0.11/
    │       │   │   ├── redis.conf
    │       │   │   └── sentinel.conf
    │       │   ├── 10.0.0.12/
    │       │   │   ├── redis.conf
    │       │   │   └── sentinel.conf
    │       │   └── 10.0.0.13/
    │       │       ├── redis.conf
    │       │       └── sentinel.conf
    │       └── staging/
    │           ├── 10.0.0.21/
    │           │   ├── redis.conf
    │           │   └── sentinel.conf
    │           ├── 10.0.0.22/
    │           │   ├── redis.conf
    │           │   └── sentinel.conf
    │           └── 10.0.0.23/
    │               ├── redis.conf
    │               └── sentinel.conf
    └── exports/

### 1. versionPackages

该目录保存从前端上传并通过校验的 Redis 官方源码包。

- 文件名必须为 redis-{version}.tar.gz。
- 安装包通过上传接口保存，不需要人工复制。
- 平台首次部署时可以没有任何安装包。
- 上传时平台自动创建目录、计算 SHA-256、检查压缩包结构，并校验文件名版本与 src/version.h 中的真实版本一致。
- 目录必须对平台进程用户可读、可写。

对应配置：

    platform:
      version-package:
        storage-dir: /opt/redis-platform/data/versionPackages

实际路径通过平台外置 `application.yml` 中的 `platform.version-package.storage-dir` 修改。

### 2. nodeConfigs/current

该目录保存当前正式配置。故障恢复和首次纳管配置摘要计算从这里读取文件。

目录必须按照服务器 IP 创建：

    current/{服务器IP}/redis.conf
    current/{服务器IP}/sentinel.conf

首次纳管三台已经运行的生产 Redis 前，必须人工准备三台服务器对应的六份配置。文件内容必须与各服务器当前实际使用并保存到可信备份目录的配置一致。

首次纳管不依赖 Redis 安装包，但依赖这六份配置计算并保存 SHA-256。

### 3. nodeConfigs/staging

该目录保存下一次服务器切换要使用的新配置。

服务器切换前，必须按照三台目标服务器 IP 人工准备六份文件：

    staging/{目标主节点IP}/redis.conf
    staging/{目标主节点IP}/sentinel.conf
    staging/{目标从节点1 IP}/redis.conf
    staging/{目标从节点1 IP}/sentinel.conf
    staging/{目标从节点2 IP}/redis.conf
    staging/{目标从节点2 IP}/sentinel.conf

平台会检查：

1. 目标主节点的 redis.conf 不包含生效的 replicaof 或 slaveof。
2. 两个目标从节点的 redis.conf 都通过 replicaof {目标主节点IP} 6379 指向本次主节点。
3. 三份 sentinel.conf 都监控本次目标主节点的 6379 端口。
4. 三份 Sentinel 配置使用相同的主节点名称。
5. 每份文件存在、可读、非空且不超过 1 MiB。

切换成功后，平台把本次三台目标服务器的配置从 staging/{IP} 复制到 current/{IP}，并重新计算正式配置摘要。

平台不会创建 tasks/{taskNo} 配置快照目录，也不会自动删除 staging 中的源文件。下一次切换前应人工替换为对应目标服务器的新配置。

对应配置：

    platform:
      node-config:
        config-dir: /opt/redis-platform/data/nodeConfigs

实际路径通过平台外置 `application.yml` 中的 `platform.node-config.config-dir` 修改。

### 4. exports

该目录用于保存平台生成的报表：

    platform:
      report:
        export-dir: /opt/redis-platform/exports

实际路径通过平台外置 `application.yml` 中的 `platform.report.export-dir` 修改。

## 三、平台服务器需要人工准备的内容

### 1. 创建平台工作目录

由服务器管理员创建根目录，并把所有权交给平台运行账户：

    mkdir -p /opt/redis-platform/data/versionPackages
    mkdir -p /opt/redis-platform/data/nodeConfigs/current
    mkdir -p /opt/redis-platform/data/nodeConfigs/staging
    mkdir -p /opt/redis-platform/exports

实际用户名和用户组由部署环境决定。

### 2. 放置程序和外置配置

人工放置：

    /opt/redis-platform/redis-monitor-platform.jar
    /opt/redis-platform/application.yml

application.yml 至少需要确认：

- PostgreSQL 地址、用户名和密码。
- 平台注册码 platform.register-code。
- 安装包目录、节点配置目录和报表导出目录。
- 邮件告警是否启用；未配置真实邮件服务器时保持关闭。
- 平台端口默认是 8100，防火墙需要允许执行器访问。

数据库密码、注册码等敏感信息不要提交到 Git，应只保存在服务器外置配置中。

### 3. 初始化数据库

首次部署需要在 PostgreSQL 中执行项目提供的正式建表脚本。测试重建脚本和测试数据只允许在测试库使用，不能在生产库执行。

### 4. 首次纳管前准备配置

假设现有生产节点为：

    10.0.0.11 主节点
    10.0.0.12 从节点
    10.0.0.13 从节点

需要人工创建：

    /opt/redis-platform/data/nodeConfigs/current/10.0.0.11/redis.conf
    /opt/redis-platform/data/nodeConfigs/current/10.0.0.11/sentinel.conf
    /opt/redis-platform/data/nodeConfigs/current/10.0.0.12/redis.conf
    /opt/redis-platform/data/nodeConfigs/current/10.0.0.12/sentinel.conf
    /opt/redis-platform/data/nodeConfigs/current/10.0.0.13/redis.conf
    /opt/redis-platform/data/nodeConfigs/current/10.0.0.13/sentinel.conf

首次纳管完成后，数据库中的 Redis 和 Sentinel 记录会保存对应配置的 SHA-256。

## 四、执行器服务器目录结构

每台 Redis 服务器建议使用相同目录结构：

    /opt/redis/
    ├── agent/
    │   ├── redis-monitor-agent.jar
    │   ├── application.yml
    │   ├── agent-token.dat
    │   ├── data/
    │   ├── logs/
    │   └── task-results/
    ├── scripts/
    │   ├── precheck-host.sh
    │   ├── deploy-version.sh
    │   ├── switch-version.sh
    │   ├── start-redis.sh
    │   ├── stop-redis.sh
    │   ├── start-sentinel.sh
    │   └── stop-sentinel.sh
    ├── packages/
    │   └── staging/
    │       └── {taskNo}/
    │           └── package.tar.gz
    ├── configfiles/
    │   └── staging/
    │       └── {taskNo}/
    │           ├── redis.conf
    │           └── sentinel.conf
    ├── versions/
    │   └── {version}/
    │       └── bin/
    │           ├── redis-server
    │           ├── redis-cli
    │           ├── redis-sentinel
    │           └── redis-check-rdb
    ├── current -> /opt/redis/versions/{当前版本}
    ├── conf/
    │   ├── redis.conf
    │   └── sentinel.conf
    ├── backup/
    │   ├── redis.conf
    │   └── sentinel.conf
    └── data/

### 1. 必须人工创建的目录

执行器不会在启动或主机预检查时创建运维根目录，需要提前创建：

    mkdir -p /opt/redis/agent
    mkdir -p /opt/redis/scripts
    mkdir -p /opt/redis/packages
    mkdir -p /opt/redis/configfiles
    mkdir -p /opt/redis/versions
    mkdir -p /opt/redis/conf
    mkdir -p /opt/redis/backup
    mkdir -p /opt/redis/data

执行器会按任务自动创建：

- /opt/redis/packages/staging/{taskNo}
- /opt/redis/configfiles/staging/{taskNo}
- 配置允许时的 agent/data、agent/logs 和 agent/task-results

所有父目录必须提前存在，并确保执行器运行账户具有所需的读写和执行权限。

### 2. 必须人工放置的七个脚本

把执行器项目 scripts 目录中的以下文件原样复制到 /opt/redis/scripts：

    precheck-host.sh
    deploy-version.sh
    switch-version.sh
    start-redis.sh
    stop-redis.sh
    start-sentinel.sh
    stop-sentinel.sh

授予执行权限：

    chmod 700 /opt/redis/scripts/*.sh

脚本必须保持 Unix 换行符，不能带 Windows CRLF。

### 3. 正式配置和可信备份

运行中的正式配置：

    /opt/redis/conf/redis.conf
    /opt/redis/conf/sentinel.conf

故障恢复使用的可信备份：

    /opt/redis/backup/redis.conf
    /opt/redis/backup/sentinel.conf

要求：

- conf 和 backup 必须是不同目录，但不强制位于不同磁盘。
- 首次纳管现有生产服务器时，人工把当前有效配置分别放入正式目录和备份目录。
- 备份文件内容必须与平台 nodeConfigs/current/{本机IP} 中对应文件一致。
- 服务器切换时，平台下发的新配置先进入执行器暂存目录；校验通过后更新正式配置和可信备份。
- Sentinel 运行期间会重写正式 sentinel.conf，不能把正式文件和可信备份设置为同一个文件。

### 4. Redis 版本目录和 current 软链接

versions 保存已经编译完成的各 Redis 版本，current 始终指向当前运行版本：

    /opt/redis/current -> /opt/redis/versions/7.2.4

注意：

- current 必须是符号链接，不能是普通目录。
- 新服务器在首次部署 Redis 前允许不存在 current，版本切换步骤会创建它。
- 已经运行 Redis 的现有生产服务器，首次纳管前必须确保 current 能正确指向当前 Redis 版本目录，否则故障恢复或重启操作无法通过固定脚本启动 Redis/Sentinel。
- /opt/redis/versions 和 /opt/redis/current 的父目录必须对执行器账户可写。

### 5. 执行器外置配置

每台服务器都需要单独准备：

    /opt/redis/agent/application.yml

每台执行器的配置可以不同，至少确认以下内容：

    server:
      port: 9101

    agent:
      platform:
        url: http://{平台IP}:8100
        register-code: {与平台一致的注册码}
        token-file: /opt/redis/agent/agent-token.dat

      host-ip: {本机供平台访问的IP}

      data-dir: /opt/redis/agent/data
      log-dir: /opt/redis/agent/logs

      task-result:
        directory: /opt/redis/agent/task-results

      redis:
        host: 127.0.0.1
        port: 6379
        password: {本机Redis密码，没有则留空}
        config-file: /opt/redis/conf/redis.conf
        data-dir: /opt/redis/data
        backup-file: /opt/redis/backup/redis.conf

      sentinel:
        enabled: true
        host: 127.0.0.1
        port: 26379
        password: {本机Sentinel密码，没有则留空}
        service-name: {Sentinel监控的主节点名称}
        config-file: /opt/redis/conf/sentinel.conf
        backup-file: /opt/redis/backup/sentinel.conf

      operation:
        script-dir: /opt/redis/scripts
        package-dir: /opt/redis/packages
        config-file-dir: /opt/redis/configfiles
        install-dir: /opt/redis/versions
        current-dir: /opt/redis/current

执行器注册时会实时探测本机 Redis，并通过注册请求中的 `redisReady` 上报探测结果，不需要在配置文件中人工设置该字段。

## 五、两类服务器的准备区别

### 1. 已经运行 Redis 的现有生产服务器

用于首次纳管时，人工需要完成：

1. 部署执行器 JAR 和外置 application.yml。
2. 配置正确的 Redis、Sentinel 地址、端口、密码和 Sentinel 主节点名称。
3. 准备七个固定脚本。
4. 确保 /opt/redis/conf 中是当前正在使用的配置。
5. 把可信配置副本放入 /opt/redis/backup。
6. 确保 versions/{当前版本} 和 current 软链接能够支持后续启停。
7. 在平台 nodeConfigs/current/{IP} 中放置与本机备份一致的两份配置。
8. 启动执行器并确认平台注册、Redis 信息采集和 Sentinel 信息采集成功。

### 2. 尚未部署 Redis 的新服务器

用于以后服务器切换时，人工需要完成：

1. 部署执行器 JAR 和外置 application.yml。
2. 创建运维根目录并放置七个脚本。
3. 不需要提前安装 Redis，也不需要提前创建 current 软链接。
4. conf、backup、versions 等父目录需要存在并可写，正式文件可以暂时不存在。
5. 启动执行器，平台此时只应创建 executor_info，instance_id 为空。
6. 在平台 nodeConfigs/staging/{目标IP} 中准备三台目标服务器对应的六份配置。
7. 从前端上传并启用目标 Redis 源码安装包。
8. 执行服务器切换任务，由系统完成预检查、版本部署、配置部署、启动、拓扑验证和纳管关系更新。

## 六、服务器切换前人工检查清单

- [ ] 三台目标服务器 IP 不重复，并且与当前生产服务器 IP 不重复。
- [ ] 三台目标服务器的执行器已经启动并成功注册。
- [ ] 三台目标执行器当前未纳管，instance_id 为空。
- [ ] 三台新服务器当前均未运行 Redis 和 Sentinel。
- [ ] 七个脚本全部存在、可读、可执行，并使用 Unix 换行符。
- [ ] packages、configfiles、versions、conf、backup、data 目录均已创建并有正确权限。
- [ ] 平台已上传并启用目标版本源码包。
- [ ] 平台 staging/{IP} 中已经准备三台服务器对应的六份配置。
- [ ] 目标主节点 Redis 配置没有 replicaof 或 slaveof。
- [ ] 两个目标从节点 Redis 配置都指向目标主节点 6379。
- [ ] 三份 Sentinel 配置监控同一个主节点名称和目标主节点 IP。
- [ ] 平台可以访问执行器端口 9101。
- [ ] 执行器可以访问平台端口 8100。
- [ ] 服务器具备 Redis 源码编译依赖和足够磁盘空间。

## 七、首次纳管前人工检查清单

- [ ] 三台现有 Redis 和 Sentinel 当前正常运行。
- [ ] 三台执行器均能采集本机 Redis 和 Sentinel 信息。
- [ ] 平台 current/{IP} 中已经放置三台服务器对应的六份配置。
- [ ] 执行器 backup 中的配置与平台 current/{IP} 内容一致。
- [ ] 执行器 current 软链接能够解析到当前版本目录。
- [ ] 平台数据库表已经创建。
- [ ] 平台与执行器使用相同的注册码。
- [ ] 平台和执行器之间网络双向可达。

## 八、启动方式示例

平台：

    cd /opt/redis-platform
    java -jar redis-monitor-platform.jar \
      --spring.config.additional-location=file:/opt/redis-platform/application.yml

执行器：

    cd /opt/redis/agent
    java -jar redis-monitor-agent.jar \
      --spring.config.additional-location=file:/opt/redis/agent/application.yml

生产环境建议交给 systemd 等进程管理工具启动，并固定工作目录、运行账户、环境变量和自动重启策略。

## 九、容易出错的地方

1. 相对目录以 Java 进程工作目录为基准，生产环境建议全部使用绝对路径。
2. 平台配置按 IP 分目录，目录名必须与执行器上报和前端提交的 IP 完全一致。
3. 平台 current 是正式配置目录，staging 是下一次服务器切换配置目录，不能混用。
4. 执行器 /opt/redis/current 是 Redis 版本软链接，与平台 nodeConfigs/current 不是同一概念。
5. 正式配置和可信备份不能使用同一个目录。
6. 执行器是否上报 Redis 信息由本机 Redis 的实时探测结果决定，不需要人工维护就绪标记。
7. 源码安装包只能通过平台上传接口进入 versionPackages，不要手工放文件后直接写数据库记录。
8. current 软链接不存在时可以部署新服务器，但现有生产环境执行启停前必须确保它有效。
9. 配置文件、注册码、数据库密码和 Redis 密码属于敏感信息，不要提交到代码仓库。
