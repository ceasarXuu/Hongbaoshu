# Pack Studio 实施计划

## 1. 实施原则

1. 小主题拆分，完成即提交。
2. 每个里程碑都要形成可运行闭环。
3. 每个关键流程都要同时落文本日志和 JSON 报告。
4. 优先解决真实问题，不做伪闭环和 UI 空壳。

## 2. 里程碑规划

## Milestone A：最小文本打包闭环

目标：

- 从单一本地文本源完成标准 Pack 打包

范围：

1. 项目管理基础能力
2. 本地工作区初始化
3. `txt` 导入
4. 文本标准化
5. `book.json` 生成
6. `manifest.json` 生成
7. `.hbs.zip` 打包
8. import / normalize / build 日志

验收标准：

1. 能从 `txt` 生成纯文本 `.hbs.zip`
2. 产物结构符合现有 Pack 规范
3. 关键任务均有日志和 JSON 报告

## Milestone B：多格式导入与可视化校验

目标：

- 支持主流电子书格式导入并可视化检查结构结果

范围：

1. `epub` 导入
2. `mobi` 导入
3. 内容页结构预览
4. 章节、段落、句子可视化
5. 校验页
6. `validation_report.json`

验收标准：

1. 用户可导入 `txt/epub/mobi`
2. 可看到标准化结果与基本异常
3. 有错误时阻止打包

## Milestone C：音频一期

目标：

- 建立句子导出与音频回收闭环

范围：

1. 句子 CSV 导出
2. 音频目录导入
3. 音频映射校验
4. 覆盖率统计
5. 纯文本包 / 带音频包统一打包
6. audio-scan 日志和报告

验收标准：

1. 可导出句子级 CSV
2. 可导入音频并得到缺失清单
3. 带 narration 资源包可成功输出

## Milestone D：二期 TTS 预留

目标：

- 以最小侵入方式为 Windows TTS 集成铺路

范围：

1. `tts-providers` 插件接口
2. TTS 任务模型
3. CSV 预留字段
4. Windows CosyVoice 适配设计稿

验收标准：

1. 一期功能不受影响
2. TTS 能力可独立接入

## 3. 建议任务拆分

### 3.1 基础骨架

1. 初始化 `tools/pack-studio`
2. 确定前后端目录结构
3. 初始化本地工作区与配置文件
4. 建立统一日志组件

### 3.2 项目管理

1. 新建项目
2. 项目列表
3. 项目详情
4. 删除项目

### 3.3 导入器

1. `txt` 解析器
2. `epub` 解析器
3. `mobi` 解析器
4. 源格式识别器

### 3.4 标准化

1. 章节归一化
2. 段落归一化
3. 句子切分
4. ID 生成
5. 中间结构持久化

### 3.5 校验

1. ID 唯一性校验
2. metadata 校验
3. 资源存在性校验
4. 打包门禁判定

### 3.6 音频一期

1. CSV 导出
2. narration 目录扫描
3. 前缀匹配
4. 覆盖率报告

### 3.7 打包

1. build 目录组装
2. `book.json` 输出
3. `manifest.json` 输出
4. zip 打包
5. artifact 索引

## 4. 提交策略

建议按以下粒度提交：

1. `docs: add pack-studio product and tech design`
2. `feat: scaffold pack-studio workspace and project model`
3. `feat: add txt importer and normalize pipeline`
4. `feat: add epub importer`
5. `feat: add mobi importer`
6. `feat: add validation pipeline`
7. `feat: add sentence csv export`
8. `feat: add narration mapping validation`
9. `feat: add pack builder`

原则：

- 每个 commit 只覆盖一个小主题
- 每个 commit 完成后都应可解释、可回滚

## 5. 日志建设要求

### 5.1 必须记录的任务

- 导入
- 标准化
- CSV 导出
- 音频扫描
- 全量校验
- 打包

### 5.2 每个任务至少输出

1. 文本日志
2. JSON 报告
3. 起止时间
4. 状态
5. 核心统计

### 5.3 统一日志字段建议

```json
{
  "taskType": "validate",
  "taskId": "validate_20260306_001",
  "projectId": "proj_20260306_001",
  "startedAt": "2026-03-06T10:30:00+08:00",
  "finishedAt": "2026-03-06T10:30:05+08:00",
  "status": "failed"
}
```

## 6. 风险应对

### 6.1 MOBI 风险

问题：

- 结构恢复不稳定

应对：

- 标记为支持但非首选
- UI 明示解析质量可能较低
- 强依赖标准化预览页做人工核查

### 6.2 句子切分风险

问题：

- 切分规则变化会影响 CSV 与音频映射

应对：

- 一期尽量冻结规则
- 在文档中显式约定 ID 和切句规则
- 对变更做专项提交和日志记录

### 6.3 双平台兼容风险

问题：

- 路径、编码、压缩实现差异

应对：

- 从第一天起统一用跨平台库
- CI 后续补 Win/macOS 双平台验证

### 6.4 二期模型集成风险

问题：

- CosyVoice 本地依赖复杂

应对：

- 先抽象 provider 接口
- 不把 TTS 能力写入一期打包主链路

## 7. 当前阶段建议

当前建议先按以下顺序进入开发：

1. 落地 `tools/pack-studio` 骨架
2. 先打通 `txt -> normalize -> validate -> build`
3. 再补 `epub/mobi`
4. 再补 CSV 与音频映射
5. 最后再预留 TTS provider 接口
