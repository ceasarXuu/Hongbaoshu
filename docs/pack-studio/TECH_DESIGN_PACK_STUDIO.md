# Pack Studio 技术设计

## 1. 设计原则

Pack Studio 的技术设计遵循以下原则：

1. 本地优先：所有导入、处理、校验、打包均在本机完成。
2. 跨平台优先：Win/macOS 是明确目标平台。
3. 中间模型统一：不同电子书格式统一转成同一套结构模型。
4. 先校验再打包：不把脏数据留给 Android 导入阶段发现。
5. 日志驱动：关键流程输出可追踪日志和结构化报告。
6. 音频分期：一期只解决拼装和映射，二期再接 TTS。

## 2. 子项目定位

建议新增目录：

`tools/pack-studio`

该子项目独立于 Android `app` 模块，不参与 App 运行时逻辑，只负责资源包生产。

## 3. 总体架构

### 3.1 架构分层

建议采用本地 Web 形态：

1. Web UI 层
2. 本地 API 服务层
3. 核心处理引擎层
4. 工作区与日志存储层

### 3.2 分层职责

#### Web UI

负责：

- 页面交互
- 表单编辑
- 任务触发
- 报告展示

不负责：

- 文件格式解析
- 打包逻辑
- 持久化规则

#### 本地 API 服务

负责：

- 项目生命周期
- 文件上传与导入
- 任务编排
- 状态查询
- 日志输出

#### 核心处理引擎

负责：

- 源格式导入
- 文本标准化
- 句子 CSV 导出
- 音频映射
- 校验
- manifest 生成
- zip 打包

#### 工作区与日志存储

负责：

- 原始文件保存
- 中间产物保存
- 构建产物保存
- 报告与日志保存

## 4. 模块设计

建议拆成以下核心模块。

### 4.1 `project`

职责：

- 创建、读取、删除项目
- 保存项目元数据
- 维护项目状态
- 维护工作区路径

建议接口：

- `createProject()`
- `listProjects()`
- `getProject(projectId)`
- `deleteProject(projectId)`
- `updateProjectMetadata(projectId, payload)`

### 4.2 `importers`

职责：

- 识别源文件类型
- 执行 `txt/epub/mobi` 解析
- 输出原始导入结果

建议子模块：

- `txtImporter`
- `epubImporter`
- `mobiImporter`

统一输出：

- `RawImportedBook`

### 4.3 `normalize`

职责：

- 将 `RawImportedBook` 转为标准中间模型
- 执行章节、段落、句子标准化
- 生成稳定 ID
- 输出统计信息

建议接口：

- `normalizeBook(rawBook, options)`

### 4.4 `sentence-export`

职责：

- 从标准句子模型生成 CSV
- 提供固定列结构
- 为外部音频工具提供稳定输入

建议接口：

- `exportSentencesCsv(projectId, options)`

### 4.5 `audio-map`

职责：

- 扫描 narration 目录
- 按 `<sentenceId>_` 前缀匹配句子
- 输出缺失、孤儿、重复报告

建议接口：

- `scanNarrationDir(projectId, dirPath)`
- `validateNarrationMapping(projectId)`

### 4.6 `manifest`

职责：

- 基于项目元数据和资源清单生成 `manifest.json`
- 统一输出 pack 规范所需字段

建议接口：

- `generateManifest(projectId)`

### 4.7 `validate`

职责：

- 做结构校验
- 做命名校验
- 做资源校验
- 做打包前门禁判定

建议接口：

- `validateProject(projectId)`

### 4.8 `package`

职责：

- 生成标准目录结构
- 输出 `book.json`
- 输出 `manifest.json`
- 压缩为 `.hbs.zip`

建议接口：

- `buildPack(projectId)`

## 5. 统一数据模型

### 5.1 项目模型 `PackProject`

建议字段：

```text
id
name
status
sourceType
sourcePath
workspacePath
createdAt
updatedAt
metadata
assetConfig
lastValidationSummary
lastBuildSummary
```

### 5.2 书籍中间模型 `StructuredBook`

```text
book
chapters[]
```

### 5.3 章节模型 `Chapter`

```text
id
title
paragraphs[]
```

### 5.4 段落模型 `Paragraph`

```text
id
type
content
ref?
sentences[]
```

### 5.5 句子模型 `Sentence`

```text
id
content
chapterId
paragraphId
indexInParagraph
indexInBook
```

### 5.6 资源清单 `AssetInventory`

```text
cover?
flipSound?
narrationDir?
narrationStats
```

### 5.7 音频统计 `NarrationStats`

```text
expectedCount
matchedCount
missingCount
orphanCount
duplicatePrefixCount
missingSentenceIds[]
orphanFiles[]
duplicatePrefixes[]
```

## 6. 导入适配器设计

### 6.1 TXT

设计要求：

- 支持常见编码识别
- 支持按空行合并段落
- 支持简单章节识别

说明：

TXT 结构最弱，应通过标准化阶段进行更多补齐。

### 6.2 EPUB

设计要求：

- 提取线性阅读顺序正文
- 提取章节标题
- 提取封面
- 尽量忽略样式层

说明：

EPUB 作为一期主推荐输入格式。

### 6.3 MOBI

设计要求：

- 能完成文本抽取
- 尽量恢复章节顺序
- 容忍结构质量较低

说明：

MOBI 一期支持，但要在 UI 中明确解析质量可能弱于 EPUB。

## 7. 文本标准化规则

一期建议固定如下规则，避免早期过度可配置。

### 7.1 ID 规则

- `chapter.id`: `01`, `02`, `03`
- `paragraph.id`: `<chapterId>-001`
- `sentence.id`: `<paragraphId>-s001`

### 7.2 切分规则

- 空白段落丢弃
- 正文段落做句子切分
- 注释段落不切句
- 中文句号、问号、感叹号、分号优先作为句边界
- 尾句无结束标点也保留

### 7.3 标准化输出

除标准结构外，还要生成摘要：

- 章节数
- 段落数
- 句子数
- 空段落数
- 超长句子数

## 8. 音频一期设计

### 8.1 一期边界

一期不生成音频，只解决：

- 句子导出
- 音频回收
- 句子映射
- 完整性校验

### 8.2 匹配规则

音频文件名匹配规则：

- 使用 `<sentenceId>_` 作为前缀匹配依据

示例：

- `01-001-s001_xxx.wav`
- `01-001-s002_xxx.mp3`

### 8.3 支持格式

一期建议先支持：

- `wav`
- `mp3`
- `ogg`

### 8.4 音频校验结果

必须输出：

- 已匹配数量
- 缺失句子数量
- 孤儿音频数量
- 重复前缀数量
- 覆盖率

## 9. 二期 TTS 预留设计

### 9.1 原则

- TTS 为插件，不侵入核心打包链路
- 二期优先 Windows
- 以 CosyVoice 3.0 为候选实现

### 9.2 预留模块

建议模块：

- `tts-providers`

建议接口：

- `generateAudio(projectId, providerConfig)`
- `getGenerationStatus(jobId)`

### 9.3 预留字段

建议在句子导出和任务模型中预留：

- `speaker`
- `voiceProfile`
- `ttsProvider`
- `ttsStatus`
- `generatedAudioPath`

## 10. 打包设计

### 10.1 目标目录

```text
<root>/
  manifest.json
  text/
    book.json
  images/
    cover.png
  sound/
    page_flip.wav.ogg
  audio/
    narration/
      <sentenceId>_*.mp3
```

### 10.2 输出策略

- `manifest.json` 必须生成
- `text/book.json` 必须生成
- cover 可缺失
- flipSound 可缺失
- narration 可缺失

### 10.3 包名策略

建议：

`<packId>_v<packVersion>.hbs.zip`

## 11. API 草图

### 11.1 项目

- `POST /api/projects`
- `GET /api/projects`
- `GET /api/projects/:id`
- `DELETE /api/projects/:id`

### 11.2 导入与标准化

- `POST /api/projects/:id/import`
- `POST /api/projects/:id/normalize`
- `GET /api/projects/:id/book`

### 11.3 音频

- `POST /api/projects/:id/export/sentences`
- `POST /api/projects/:id/assets/audio`
- `POST /api/projects/:id/validate/audio`

### 11.4 校验与打包

- `POST /api/projects/:id/validate`
- `POST /api/projects/:id/build`
- `GET /api/projects/:id/reports`
- `GET /api/projects/:id/logs`
- `GET /api/projects/:id/artifact`

## 12. 工作区布局

建议目录：

```text
workspace/
  projects/
    <projectId>/
      project.json
      source/
      normalized/
      exports/
      assets/
      build/
      reports/
      logs/
```

目录职责：

- `source/`: 原始输入
- `normalized/`: 标准化中间结果
- `exports/`: CSV 等导出
- `assets/`: 封面、音效、narration
- `build/`: 最终构建目录与产物
- `reports/`: 结构化报告
- `logs/`: 文本日志

## 13. 日志设计

### 13.1 日志原则

关键任务必须落双份结果：

1. 文本日志
2. 结构化 JSON 报告

### 13.2 任务类型

- import
- normalize
- export-sentences
- audio-scan
- validate
- build

### 13.3 命名建议

```text
logs/
  import-20260306-101530.log
  build-20260306-103000.log
reports/
  import-20260306-101530.json
  build-20260306-103000.json
```

## 14. 跨平台注意事项

### 14.1 文件系统

- 统一使用跨平台路径库
- 避免手写路径分隔符
- 文件名生成避免保留字符

### 14.2 编码

- 文本导入统一转 UTF-8
- CSV 导出使用 UTF-8 BOM，兼容 Excel

### 14.3 打包

- zip 生成库必须验证 Win/macOS 结果一致
- 文件路径全部按 UTF-8 处理

## 15. 风险点

1. `mobi` 解析质量不可完全控
2. 句子切分规则一旦调整，会影响全部音频映射
3. 大规模音频目录扫描可能导致 UI 卡顿，任务需异步化
4. TTS 二期依赖体积和环境复杂，必须隔离在插件层
