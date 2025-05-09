# 项目名称：ThunderLike - 亿级流量高并发点赞系统

## 🌟 项目简介

ThunderLike 是一个基于 Spring Boot 3 + Java 21 构建的企业级高并发点赞系统，专为应对亿级流量场景设计。系统采用多级缓存、消息解耦、分布式数据库等先进架构，实现了高性能、高可用的点赞服务。

## 🚀 技术栈

- **核心框架**: Spring Boot 3 + Java 21
- **持久层**: MyBatis-Plus + TiDB (分布式数据库)
- **缓存系统**: Redis + Caffeine (多级缓存)
- **消息队列**: Apache Pulsar (异步处理)
- **监控系统**: Prometheus + Grafana (全方位监控)
- **部署方案**: Docker + Nginx (容器化部署)
- **热点检测**: HeavyKeeper 算法 (高效统计热点数据)

## ✨ 核心特性

1. **亿级流量支持**：通过多级缓存和分布式架构，轻松应对高并发场景
2. **高性能设计**：本地缓存+分布式缓存+热点检测，实现毫秒级响应
3. **高可用保障**：完善的降级方案和故障转移机制
4. **实时监控**：全方位监控体系，实时感知系统状态
5. **弹性扩展**：各组件支持水平扩展，满足业务增长需求

## 🛠️ 快速开始

### 环境准备

- JDK 21+
- Docker 20.10+
- Maven 3.6+

### 启动步骤

```bash
# 克隆项目
git clone https://github.com/FangMoyu/thunder-like.git

# 构建项目
mvn clean package

# 启动依赖服务 (Redis, Pulsar, TiDB等)
docker-compose up -d

# 运行应用
java -jar thunderlike-app/target/thunderlike-app.jar
```
## 性能优化
+ 使用 Caffeine 本地缓存减少 Redis 访问
+ 通过 HeavyKeeper 算法检索热点数据
+ 采用 Pulsar 消息队列进行流量削峰
+ 分布式数据库 TiDB 提供水平扩展能力

## 🏆 适用场景

- 社交媒体点赞系统
- 电商平台商品喜欢功能
- 内容平台互动计数
- 任何需要高并发计数服务的场景

## 🤝 贡献指南

欢迎提交Pull Request或Issue，共同完善项目！

## 📄 许可证

Apache License 2.0

---

ThunderLike - 为您的应用提供闪电般快速的点赞体验！⚡