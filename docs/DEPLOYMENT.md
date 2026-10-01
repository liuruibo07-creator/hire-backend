# 容器化部署说明

## 部署结构

两个仓库需要同级放置，后端 Compose 通过 `../hire-frontend` 构建用户指定的智聘前端：

```text
workspace/
  hire-backend/
    Dockerfile
    compose.yaml
    .env.example
    deploy/mysql/01-init.sql
    deploy/elasticsearch/Dockerfile
    deploy/seata/application.yml
  hire-frontend/
    Dockerfile
    deploy/nginx.conf
```

前端用 Node.js 22 构建，Nginx 提供页面、History 路由回退及 `/api` 同源代理；后端每个可执行服务以 Maven/JDK 17 构建，再放入 JRE 17 镜像并以非 root 用户运行。公共模块在 Maven reactor 中参与构建，不独立运行。

建议为单机演示预留至少 4 核 CPU、8 GB 可用内存与 10 GB 磁盘空间；实际资源取决于数据规模与并发。需要 Docker Engine/Desktop 和 Compose v2，能够访问 Maven、npm、镜像仓库及 IK 插件下载地址。

## 首次启动

```sh
git clone https://github.com/liuruibo07-creator/hire-backend.git
git clone https://github.com/liuruibo07-creator/hire-frontend.git
cd hire-backend
cp .env.example .env
```

Windows：`Copy-Item .env.example .env`。编辑 `.env`，分别设置 MySQL、Redis、RabbitMQ 密码，以及至少 32 字符的随机 `JWT_SECRET`。示例中的 `CHANGE_ME` 只是占位符；`.env` 已被 Git 与 Docker 构建上下文忽略。密码建议使用长随机字母数字字符串，避免 Compose 中 `$` 的插值歧义。所有业务服务通过同一个 JWT 密钥签发/验证登录令牌。

```sh
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 cloud-gateway job-service application-service
```

打开 `http://localhost:8080`。`WEB_PORT` 可改变前端端口，修改时同步更新 `CHAT_ALLOWED_ORIGINS`。首次构建需要下载依赖及插件。MySQL、Redis、RabbitMQ、Nacos 和 Elasticsearch 配置健康检查；业务服务在依赖就绪后启动，Seata 使用启动顺序和服务重试，仍需观察日志确认事务协调器可用。

本环境未安装 Docker，因此以上步骤未实机执行。部署后应至少验证匿名搜索、注册登录、发布职位、管理员审核、简历投递、通知消费、私信历史与已读状态。

## 中间件与数据

| 服务 | 容器内部端口 | 说明 |
| --- | --- | --- |
| mysql | 3306 | 五个业务库；初始化脚本仅在空数据卷时执行 |
| redis | 6379 | 密码认证、AOF 持久化，用于缓存、计数和限流 |
| rabbitmq | 5672 / 15672 | `hire` 虚拟主机；独立账号，消费重试由业务配置控制 |
| nacos | 8848 / 9848 | 单节点内置存储；容器内服务发现，不向宿主机公开控制台 |
| elasticsearch | 9200 | 7.12.1；镜像构建时安装同版本 IK 中文分词插件 |
| seata | 8091 / 7091 | 2.0.0；file 注册/配置、file 事务日志，卷持久化 |

数据库为 `cloud_user`、`cloud_job`、`cloud_application`、`cloud_notification` 和 `cloud_chat`。`deploy/mysql/01-init.sql` 包含用户、职位分类、职位、简历、投递、通知、聊天和 Seata `undo_log` 表，不包含可登录的预置用户。职位分类提供一条基础示例。初始化 DDL 根据当前实体和 Mapper 补齐，不是既有数据库的迁移脚本。

Seata 的客户端事务组为 `hire_tx_group`，映射到 `default`，连接 `seata:8091`；`cloud_application` 与 `cloud_job` 有 AT 所需的 `undo_log`。本方案的事务日志采用 file 存储，适合单机演示；生产需要另行设计持久化数据库、备份与高可用。

Elasticsearch 的映射使用 `ik_max_word` 与 `ik_smart`，不能直接换成未安装 IK 的官方基础镜像。插件下载失败时检查 `deploy/elasticsearch/Dockerfile` 的同版本官方发布地址，或通过 `IK_PLUGIN_URL` 构建参数指定可信镜像源。Linux 宿主机可能需要管理员设置 `vm.max_map_count=262144` 后再启动 Elasticsearch。

## 路由与配置中心

网关的 `application.yml` 已包含可用的基础静态路由，不需要先手动发布 Nacos 配置才能转发请求。用户服务保留 `/api/user` 前缀，其他服务剥除两段；WebSocket 路由排在普通聊天路由之前。

已有 `RouteConfigListener` 继续监听 Nacos `DEFAULT_GROUP` 中的 `gateway-routes.json`。如启用动态路由，请使用不同于静态路由的 ID，并避免发布相互冲突的路径；大幅修改路由时统一调整静态配置与动态配置。共享 JDBC/Seata 配置不再依赖本机 Nacos 私有内容，容器连接参数由 Compose 环境变量提供。

前端默认同源调用 `/api`，避免跨域配置。若改为跨域部署，需配置网关 CORS 和准确的聊天 Origin 白名单。前端当前聊天使用 REST 轮询；若后续使用 WebSocket，Nginx 需补充 Upgrade/Connection 代理头。

## 更新、停止与排障

```sh
# 拉取两个仓库的更新后，在后端目录重建受影响的服务
docker compose up -d --build
# 查看日志
docker compose logs -f cloud-gateway
# 停止并保留数据卷
docker compose down
```

不要在已有数据需要保留时使用删除数据卷的参数。更新 SQL 文件不会自动修改已存在的数据库，需先备份，再实施审查过的迁移。

- 页面正常但 API 失败：检查 `cloud-gateway` 是否完成启动，Nacos 是否发现服务，路由是否被远程配置干扰。
- 登录或聊天令牌失效：检查所有服务的 `JWT_SECRET` 是否相同；更换密钥后需重新登录。
- 职位搜索失败：检查 Elasticsearch 健康状态、IK 插件、`job_index` 创建日志；搜索仅展示正确索引的招聘中职位。
- 投递失败：检查 MySQL 表结构、Seata 连接及 `undo_log`，并查看 application/job 两个服务日志。
- 通知未到达：检查 RabbitMQ 账号、虚拟主机、队列绑定与消费端重试日志。
- 数据库缺少字段：确认启动使用的是全新初始化数据卷，或为既有数据库执行了相应迁移。

## 对外部署

示例仅将前端绑定 `127.0.0.1:8080`。服务器对外使用时，可在宿主机配置 HTTPS 反向代理转发至此端口，并设置真实域名的 Origin。中间件与业务端口保持内部访问，因为服务间接口依赖网关身份透传。此单机示例的 Nacos 与 Elasticsearch 未启用认证，仅适用于可信开发环境；生产应增加认证、TLS、网络隔离、最小权限数据库账号、备份、日志管理及高可用，并按实际需要启用 Sentinel 规则。

参考：[Nacos Docker 官方仓库](https://github.com/nacos-group/nacos-docker)、[Seata 2.0 Docker Compose 文档](https://seata.apache.org/docs/v2.0/ops/deploy-by-docker-compose/)、[IK 官方仓库及下载说明](https://github.com/infinilabs/analysis-ik)。
