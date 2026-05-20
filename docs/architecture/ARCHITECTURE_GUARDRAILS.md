# 红宝匣架构护栏

本文档记录当前架构升级的硬约束，优先服务长期可维护性。

## 目标风格

红宝匣采用“模块化单体 + Clean Architecture 边界 + MVVM/MVI 单向状态流 + Ports & Adapters 资源适配”。

选择这个风格的理由：

- 当前产品仍是单 Android App 和本地 Pack 工具，不需要过早拆分成复杂多服务或多 Gradle 模块。
- Reader、Pack、Storage、Audio 已经存在天然边界，先用包级模块化和接口约束能获得最大收益。
- 后续若需要多端或独立 SDK，稳定的 domain/data/presentation 边界可以平滑迁移到 Gradle module。

## 分层规则

- `ui` 只渲染状态并发出事件，不直接读写文件、DataStore 或 Pack 索引。
- `presentation` 管理页面状态机，接收 UI intent，调用 use case 或 repository。
- `domain` 定义实体、策略和用例，不依赖 Android、Compose、ExoPlayer。
- `data` 实现本地存储、Pack 文件、Zip、DataStore 和资产读取。
- `audio` 播放器只处理播放计划和媒体状态，不依赖“当前 Pack”隐式全局状态。
- `pack.repository.PackRepository` 是 UI/入口层访问 Pack 能力的唯一门面。

## 文件与删除安全

- 禁止新增散落的 `deleteRecursively()`。
- Pack 覆盖、删除、迁移必须通过 `SafeFileOps` 先移动到备份目录。
- Zip 解压必须使用 canonical path 校验，确保目标路径在临时目录内。

## 日志规则

- 新增日志通过 `AppLogger` 输出。
- 关键操作日志必须包含可串联字段，例如 `packId`、`chapterId`、`pageIndex`、`sentenceId`、`operation`。
- JVM 单元测试可直接覆盖调用日志的业务类，日志门面不得破坏测试。

## 文件规模

- 新增单个代码文件不得超过 500 行。
- 既有超限文件不在无关变更里继续扩大；Reader 拆分阶段必须把 `ReaderScreen` 和 `ReaderViewModel` 拆到 500 行以内。

## 当前已收口边界

- 入口层通过 `PackRepository` 执行导入、删除、重校验、打开记录和封面解析。
- 直接 Android Log 调用已收口到 `AppLogger`。
- Pack 删除、覆盖、builtin 迁移已通过 `SafeFileOps` 备份后替换。
