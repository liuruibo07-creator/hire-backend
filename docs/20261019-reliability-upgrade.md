# 第一批安全与可靠性改动

## 部署顺序

1. 在 `cloud_notification` 执行 `db/migrations/20261019_notification_receipts.sql`。
2. 在 `cloud_application` 执行 `db/migrations/20261019_application_outbox.sql`。
3. 先发布 notification-service，使重发消息可以被去重；再发布 application-service。
4. 给 user-service、cloud-gateway、chat-service 设置相同的 `HIRE_JWT_SECRET`，然后一起重启。

两个脚本只创建新表，不修改或删除现有业务数据，可重复执行。此代码改动没有连接或修改远程数据库。
数据库需为 MySQL 8 / InnoDB。现有 Seata AT 配置和本库 `undo_log` 表必须有效。
旧版本应用不要与新版本长期混跑：旧消费者没有消息去重，旧网关无法校验新密钥签发的 JWT。

GitHub 容器部署已通过 `deploy/mysql/02-reliability.sql` 为全新 MySQL 数据卷自动建表；已有数据卷仍须执行上面的迁移步骤。

## JWT 与密码

`HIRE_JWT_SECRET` 必须是至少 32 字节的随机密钥；不再提供代码内默认密钥。为兼容已有 GitHub 容器配置，未设置 `HIRE_JWT_SECRET` 时也接受 `JWT_SECRET`。三个服务必须使用同一个有效值。
可在 PowerShell 中生成一次（并安全保存、为三个服务配置同一值）：

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtBytes)
$jwtRandom.Dispose()
$env:HIRE_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

也可使用 JVM 参数 `-Dhire.jwt.secret=...`。不要将实际密钥提交到代码库。
更换密钥会使旧 JWT 失效，用户需要重新登录。测试密钥仅通过 Maven Surefire 注入测试 JVM，不打包进服务。

注册和修改密码使用 BCrypt（cost=12）。已有 SHA-256 密码在正确登录后，以“旧哈希仍未变化”为条件更新为 BCrypt；
如果同时改密或禁用导致更新失败，则拒绝签发 token，提示重试。无需重置全部用户密码；原表 password 长度需至少 64 字符。
发布后如需回滚密码代码，必须保留 BCrypt 校验能力，否则已经升级的账户无法登录。

## 通知与事件

未读数直接查询数据库；不再在可能缺失的 Redis 缓存上 INCR。正确性优先，后续若要缓存应增加版本控制以处理并发失效。

投递与状态变更事件跟随业务事务写入 `t_application_outbox`，写入失败会使业务事务回滚。
发送任务每 5 秒取最多 20 条；每条使用独立事务和 `FOR UPDATE SKIP LOCKED`，支持多个实例竞争。
发送必须同时满足 broker confirm ACK 和未被 mandatory return，才标记 sent_at；失败按 5 秒至 1 小时退避，持续重试。
等待 ACK 的上限为 5 秒，此期间持有该条 outbox 行锁。

全局投递事务记录 `global_xid`。任务等待同库 `undo_log` 中对应 XID 消失后才发送：
Seata AT 全局提交后异步清理 undo log；全局回滚会同时撤销 outbox 插入。
因此不可手工清理活跃事务的 undo_log，也不可在关闭 Seata 数据源代理时继续使用全局投递流程。
Seata 清理延迟会相应延迟通知；数据库缺表或不可用时任务保留数据并记录错误。

发送语义为至少一次：broker ACK 后进程崩溃、数据库提交失败或确认超时均可能重发。
消费者先在同一个本地事务中插入 `t_notification_event.message_id` 唯一凭据，再插入通知；
重复事件直接返回，插入通知失败则连同凭据回滚，允许重试。不要在消息仍可能重放时删除去重凭据。

排查积压（在 cloud_application 中执行）：

```sql
SELECT message_id, attempts, created_at, next_attempt_at, global_xid, last_error
FROM t_application_outbox WHERE sent_at IS NULL ORDER BY created_at;
```

对 `attempts >= 5` 或长时间未发送的记录告警。修复 RabbitMQ 路由/连接后自动重试，不应人工重新构造新 message_id。
sent_at 不为空的记录可以按审计保留期限归档；未发送记录必须保留。

## 接口兼容

application-service 公开异常统一返回 `{code,message,data}`，业务错误 400、重复键 409、方法错误 405、未知错误 500。
保留现有 HTTP 200 + 业务码的约定。内部旧版 Feign 成功响应暂不变更，避免破坏 `code == 1` 的现有调用方。
前端 transport 无需改动即可显示实际错误提示。

## 验证范围

2026-10-19 验证结果：`mvn -o -B -q test`，125 个后端测试通过，无失败、错误或跳过；
前端 `npm test` 的 11 个测试通过。GitHub 发布目录的后端验证日志保存在本地 `publication-test.log`（不提交到仓库）。
同时修复了聊天数据库测试对缺失的外部 `sql/chat.sql` 的依赖，改用 `src/test/resources/chat-schema.sql`，
并显式提供 Redis 测试替身；没有恢复用户已删除的生产 SQL 文件。

Maven 测试覆盖密码升级/冲突、新旧密码校验、错误响应、未读数、消费去重和发送确认/失败退避。
本地事务测试使用 H2；MySQL 的 SKIP LOCKED 与真实 Seata/RabbitMQ 链路仍需部署环境联调。
联调时应验证：投递分支回滚不生成通知、断开 MQ 后恢复可补发、重复投递同一事件仅产生一条通知。
