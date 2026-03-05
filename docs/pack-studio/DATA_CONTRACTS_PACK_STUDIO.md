# Pack Studio 数据契约

## 1. 文档目的

本文档定义 Pack Studio 一期所需的核心数据契约，包括：

- 项目元数据
- 中间书籍结构
- 句子 CSV
- 校验报告
- 构建报告

目标是让前端、后端、后续脚本和二期 TTS 扩展共享同一套稳定接口。

## 2. 项目文件 `project.json`

建议结构：

```json
{
  "id": "proj_20260306_001",
  "name": "mao-quotes",
  "status": "normalized",
  "sourceType": "epub",
  "sourcePath": "source/input.epub",
  "workspacePath": "workspace/projects/proj_20260306_001",
  "createdAt": "2026-03-06T10:00:00+08:00",
  "updatedAt": "2026-03-06T10:20:00+08:00",
  "metadata": {
    "packId": "com.example.maoquotes",
    "packVersion": 1,
    "formatVersion": 1,
    "title": "书名",
    "author": "作者",
    "edition": "版本"
  },
  "assetConfig": {
    "coverPath": "assets/images/cover.png",
    "flipSoundPath": "assets/sound/page_flip.wav.ogg",
    "narrationDir": "assets/audio/narration"
  }
}
```

## 3. 中间书籍结构 `structured_book.json`

建议结构：

```json
{
  "book": {
    "title": "书名",
    "author": "作者",
    "edition": "版本"
  },
  "chapters": [
    {
      "id": "01",
      "title": "第一章",
      "paragraphs": [
        {
          "id": "01-001",
          "type": "text",
          "content": "正文段落内容。",
          "sentences": [
            {
              "id": "01-001-s001",
              "content": "正文段落内容。",
              "chapterId": "01",
              "paragraphId": "01-001",
              "indexInParagraph": 1,
              "indexInBook": 1
            }
          ]
        }
      ]
    }
  ]
}
```

## 4. 输出到 Pack 的 `book.json`

输出给 Android Pack 时，应裁剪为兼容现有规范的结构：

```json
{
  "book": {
    "title": "书名",
    "author": "作者",
    "edition": "版本"
  },
  "chapters": [
    {
      "id": "01",
      "title": "第一章",
      "paragraphs": [
        {
          "id": "01-001",
          "type": "text",
          "content": "正文段落内容。",
          "sentences": [
            {
              "id": "01-001-s001",
              "content": "正文段落内容。"
            }
          ]
        }
      ]
    }
  ]
}
```

说明：

- 中间模型允许有更多统计字段
- 最终输出只保留阅读器所需字段

## 5. manifest.json

建议结构：

```json
{
  "formatVersion": 1,
  "packId": "com.example.maoquotes",
  "packVersion": 1,
  "book": {
    "title": "书名",
    "author": "作者",
    "edition": "版本"
  },
  "resources": {
    "text": {
      "path": "text/book.json",
      "sha256": "..."
    },
    "cover": {
      "path": "images/cover.png",
      "sha256": "..."
    },
    "flipSound": {
      "path": "sound/page_flip.wav.ogg",
      "sha256": "..."
    },
    "narration": {
      "dir": "audio/narration",
      "codec": "mp3"
    }
  }
}
```

## 6. 句子 CSV 契约

### 6.1 CSV 列定义

一期建议固定如下列：

1. `sentence_id`
2. `chapter_id`
3. `chapter_title`
4. `paragraph_id`
5. `sentence_index_in_book`
6. `sentence_index_in_paragraph`
7. `sentence_text`
8. `book_title`
9. `book_author`
10. `suggested_audio_file`
11. `tts_status`
12. `speaker`
13. `notes`

### 6.2 CSV 示例

```csv
sentence_id,chapter_id,chapter_title,paragraph_id,sentence_index_in_book,sentence_index_in_paragraph,sentence_text,book_title,book_author,suggested_audio_file,tts_status,speaker,notes
01-001-s001,01,第一章,01-001,1,1,世界上怕就怕认真二字。,书名,作者,01-001-s001_世界上怕就怕认真二字.wav,pending,,
```

### 6.3 编码要求

- UTF-8 with BOM

原因：

- 优先保证 Win 上用 Excel 打开时不乱码

## 7. 音频映射报告 `audio_mapping_report.json`

建议结构：

```json
{
  "projectId": "proj_20260306_001",
  "expectedCount": 100,
  "matchedCount": 80,
  "missingCount": 20,
  "orphanCount": 3,
  "duplicatePrefixCount": 1,
  "coverage": 0.8,
  "missingSentenceIds": [
    "01-001-s005"
  ],
  "orphanFiles": [
    "unknown_foo.wav"
  ],
  "duplicatePrefixes": [
    "01-001-s001"
  ]
}
```

## 8. 全量校验报告 `validation_report.json`

建议结构：

```json
{
  "projectId": "proj_20260306_001",
  "generatedAt": "2026-03-06T10:40:00+08:00",
  "status": "failed",
  "summary": {
    "errors": 1,
    "warnings": 2,
    "infos": 4
  },
  "items": [
    {
      "level": "error",
      "code": "DUPLICATE_SENTENCE_ID",
      "message": "发现重复 sentence.id",
      "path": "normalized/structured_book.json"
    },
    {
      "level": "warning",
      "code": "MISSING_COVER",
      "message": "未提供封面，将按纯占位资源处理",
      "path": "assets/images"
    }
  ]
}
```

### 8.1 级别定义

- `error`: 阻塞打包
- `warning`: 允许打包，但需要显式提示
- `info`: 仅用于说明和统计

## 9. 构建报告 `build_report.json`

建议结构：

```json
{
  "projectId": "proj_20260306_001",
  "buildId": "build_20260306_001",
  "generatedAt": "2026-03-06T11:00:00+08:00",
  "status": "success",
  "artifactPath": "build/dist/com.example.maoquotes_v1.hbs.zip",
  "durationMs": 4200,
  "packSummary": {
    "packId": "com.example.maoquotes",
    "packVersion": 1,
    "formatVersion": 1,
    "hasCover": true,
    "hasFlipSound": false,
    "hasNarration": true,
    "sentenceCount": 100,
    "matchedNarrationCount": 80
  }
}
```

## 10. 文本日志约定

日志建议采用统一前缀：

```text
[INFO] [import] detected source type: epub
[INFO] [normalize] generated 12 chapters, 520 paragraphs, 3400 sentences
[WARN] [audio-map] missing 120 narration files
[ERROR] [validate] duplicate sentence id: 03-021-s004
```

## 11. 项目状态值契约

允许值：

- `draft`
- `imported`
- `normalized`
- `audio_pending`
- `ready_to_build`
- `build_failed`
- `built`

## 12. 二期 TTS 预留字段

为避免二期返工，CSV 和中间任务模型应允许出现：

- `speaker`
- `voice_profile`
- `tts_provider`
- `tts_status`
- `generated_audio_path`

一期这些字段允许为空，不作为门禁项。
