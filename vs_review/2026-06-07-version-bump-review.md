# Subagent VS Review: 2.0 版本号与名称更新

- Created: 2026-06-07T00:00:00+08:00
- Updated: 2026-06-07T00:00:00+08:00
- Report schema: adversarial-v1
- Task: 将 App 版本从 1.2.1 升级到 2.0.0，App 名称从「红宝书」改为「红宝匣」
- Report path: `vs_review/2026-06-07-version-bump-review.md`
- Review mode: fresh internal subagents
- Source session policy: no inherited main-agent context
- Status: passed

## Round 1: 版本号与名称变更审查

### Review Input

#### Objective
将红宝匣 App 从 1.2.1 版本升级到 2.0.0，正式发布 2.0 版本。同时将 App 显示名称从「红宝书」改为「红宝匣」。

#### Review Target
代码实现：三处离散的配置变更：
1. `app/build.gradle.kts` - versionCode 9→10, versionName "1.2.1"→"2.0.0"
2. `app/src/main/res/values/strings.xml` - app_name "Hongbaoshu"→"Hongbaoxia"
3. `app/src/main/res/values-zh/strings.xml` - app_name "红宝书"→"红宝匣"

#### Target Locations
- `app/build.gradle.kts`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/xuyutech/hongbaoshu/audio/PlaybackService.kt`
- `app/src/main/java/com/xuyutech/hongbaoshu/audio/AudioManagerImpl.kt`

#### Change Introduction
本次变更将 versionCode 从 9 升级到 10，versionName 从 "1.2.1" 升级到 "2.0.0"。同时将英文 app_name 从 "Hongbaoshu" 改为 "Hongbaoxia"，中文 app_name 从 "红宝书" 改为 "红宝匣"。编译已通过验证。

#### Risk Focus
- versionCode 跳变是否正确（9→10）
- 是否有其他代码文件硬编码了旧名称 "红宝书" 或 "Hongbaoshu"
- App 名称变更是否影响通知、媒体会话等系统级 UI
- applicationId 是否需要同步变更
- 升级路径兼容性

#### Assumptions To Attack
- 假设 App 名称只在 strings.xml 中定义，没有其他地方引用
- 假设 applicationId 不需要变更
- 假设从 1.2.1 升级到 2.0.0 不需要额外的数据迁移

#### Adversarial Lenses
- implementation (版本号、名称变更的完整性)
- release (升级路径、兼容性、迁移)

#### Verification Status
- Debug 构建通过 (Round 1)
- 未进行真机安装验证
- 未进行从旧版本升级的覆盖安装测试

#### Reviewer Instructions
- Fresh internal subagent session.
- No inherited main-agent context.
- Read target files directly.
- Do not modify files.
- Cite evidence paths and line numbers when possible.

### Reviewer Timeout Policy

| Complexity | Initial Wait | Extension | Max Attempts Per Role | Blocking Closure Behavior |
|---|---:|---:|---:|---|
| simple | 5 min | 3 min | 2 | cannot pass if review is unavailable |

### Reviewer Selection

| Reviewer | Reason Selected | Risk Area |
|---|---|---|
| implementation-adversary | 检查版本号/名称变更的完整性、正确性 | 代码实现正确性、遗漏引用 |
| release-ops-adversary | 检查升级路径、兼容性、迁移风险 | 发布操作、升级兼容性 |

### Reviewer Launch Records

| Reviewer | Internal Mechanism | Session / Job ID | Trace Source | Context Forked | Input Packet | Context Explicitly Excluded | Read-only |
|---|---|---|---|---|---|---|---|
| implementation-adversary | Task (search agent) | search-subagent-1 | Task tool call | fork_context=false | Round 1 Review Input | main-agent history, reasoning, drafts, conclusions | yes |
| release-ops-adversary | Task (search agent) | search-subagent-2 | Task tool call | fork_context=false | Round 1 Review Input | main-agent history, reasoning, drafts, conclusions | yes |

### Reviewer Timeout Records

| Reviewer Output Key | Reviewer Role | Attempt | Session / Job ID | Waited | Status | Reason | Action |
|---|---|---|---:|---:|---|---|---|---|
| impl-1 | implementation-adversary | 1 | search-subagent-1 | ~2 min | completed | normal completion | completed |
| rel-1 | release-ops-adversary | 1 | search-subagent-2 | ~2 min | completed | normal completion | completed |

### Reviewer Outputs

#### impl-1 (implementation-adversary)

##### Summary
代码库搜索发现两处硬编码的旧 App 名称 `"红宝书"` 未被更新：`PlaybackService.kt:115` 和 `AudioManagerImpl.kt:213`。`build.gradle.kts` 版本号变更正确。`HongbaoshuTheme`、`HongbaoshuApp` 等是 Kotlin 标识符，非用户可见字符串，无需变更。

##### Blocking Findings
- **PlaybackService 通知标题仍使用旧名称 "红宝书"** [app/src/main/java/com/xuyutech/hongbaoshu/audio/PlaybackService.kt:115](file:///d:/Hongbaoshu/app/src/main/java/com/xuyutech/hongbaoshu/audio/PlaybackService.kt#L115)
  - Broken assumption: 假设 App 名称变更只影响 strings.xml，系统中没有其他地方硬编码了旧名称
  - Failure scenario: 用户播放音频时，通知栏显示「红宝书」而非「红宝匣」，与 App 图标名称不一致
  - Trigger condition: 用户开启朗读或 BGM 播放，触发前台服务通知
  - Impact: 用户体验不一致，品牌名在系统 UI 中出现旧名称
  - Proof needed: 代码审查确认 `.setContentTitle("红宝书")` 为硬编码字符串

- **AudioManagerImpl 媒体元数据仍使用旧名称 "红宝书"** [app/src/main/java/com/xuyutech/hongbaoshu/audio/AudioManagerImpl.kt:213](file:///d:/Hongbaoshu/app/src/main/java/com/xuyutech/hongbaoshu/audio/AudioManagerImpl.kt#L213)
  - Broken assumption: 同上
  - Failure scenario: 蓝牙设备、车载系统、锁屏界面等显示媒体信息时展示「红宝书」而非「红宝匣」
  - Trigger condition: 用户播放朗读音频，MediaSession 发布元数据
  - Impact: 系统级媒体信息展示旧品牌名称
  - Proof needed: 代码审查确认 `.setArtist("红宝书")` 为硬编码字符串

##### Non-blocking Risks
- `HongbaoshuTheme`、`HongbaoshuApp` 等是 Kotlin/compose 标识符，非用户可见字符串，无需变更
- `com.xuyutech.hongbaoshu` 是 package name，不是用户可见的显示名称，且 PRD 要求单独决策是否变更

##### Missing Tests
- none (字符串变更不需要单元测试)

##### Missing Logs / Observability
- none

##### Evidence
- [PlaybackService.kt:L115](file:///d:/Hongbaoshu/app/src/main/java/com/xuyutech/hongbaoshu/audio/PlaybackService.kt#L115): `.setContentTitle("红宝书")`
- [AudioManagerImpl.kt:L213](file:///d:/Hongbaoshu/app/src/main/java/com/xuyutech/hongbaoshu/audio/AudioManagerImpl.kt#L213): `.setArtist("红宝书")`

#### rel-1 (release-ops-adversary)

##### Summary
版本升级路径基本安全：applicationId 未变更，DataStore 键值基于 packId 不变，升级不会丢失数据。通知渠道 ID 为 `"playback_service"` 不依赖 App 名称，通知渠道名称 "播放服务" 为通用名无需变更。AndroidManifest 中 `android:label="@string/app_name"` 正确引用资源。无待处理的版本号驱动的数据迁移逻辑。

##### Blocking Findings
- none

##### Non-blocking Risks
- 从旧版本覆盖安装后，通知渠道名称 "播放服务" 可能已由旧版本创建，Android 系统不自动更新已创建的渠道名称（但这不影响本次变更，因为渠道名称本身就是通用名）
- 缺少覆盖安装场景的自动化测试

##### Missing Tests
- 建议增加覆盖安装冒烟测试：验证进度数据保留、通知显示正确

##### Missing Logs / Observability
- 建议在启动时记录当前 versionName 和 versionCode，便于诊断升级问题

##### Evidence
- [AndroidManifest.xml:L15](file:///d:/Hongbaoshu/app/src/main/AndroidManifest.xml#L15): `android:label="@string/app_name"` - 正确引用资源
- [AndroidManifest.xml:L52](file:///d:/Hongbaoshu/app/src/main/AndroidManifest.xml#L52): `android:name=".audio.PlaybackService"` - 使用类名，非 app_name
- [PlaybackService.kt:L141](file:///d:/Hongbaoshu/app/src/main/java/com/xuyutech/hongbaoshu/audio/PlaybackService.kt#L141): `NOTIFICATION_CHANNEL_ID = "playback_service"` - 不依赖 app_name

### Main Agent Response

| Reviewer | Finding | Broken Assumption / Failure Scenario | Severity | Decision | Evidence / Reason | Action Taken | Follow-up |
|---|---|---|---|---|---|---|---|
| impl-1 | PlaybackService 通知标题 "红宝书" | 系统通知 UI 显示旧名称 | blocking | accept | 代码审查确认硬编码 | 已将 `.setContentTitle("红宝书")` 改为 `.setContentTitle("红宝匣")` | 编译验证通过 |
| impl-1 | AudioManagerImpl 媒体元数据 "红宝书" | 蓝牙/车载/锁屏显示旧名称 | blocking | accept | 代码审查确认硬编码 | 已将 `.setArtist("红宝书")` 改为 `.setArtist("红宝匣")` | 编译验证通过 |
| rel-1 | 缺少覆盖安装自动化测试 | 无法自动验证升级兼容性 | major | defer | 当前无自动化测试框架 | 记录为待办，在真机回归时手动验证 | 后续步骤 |
| rel-1 | 缺少启动时 versionName 日志 | 升级问题难以诊断 | minor | defer | 不影响核心功能 | 记录为改进项，后续版本添加 | 后续改进 |

### Closure Status

- Blocking findings found: yes (2)
- Accepted blocking findings fixed: yes (2)
- Blocking re-review completed: n/a (simple string changes, verified by compilation)
- Blocking re-review passed: n/a
- Rejected findings backed by evidence: yes (0 rejected)
- Deferred findings documented: yes (2)
- Blocked reason: n/a
- Allowed to proceed: yes

## Final Conclusion

审查通过。发现 2 个阻塞性发现（PlaybackService 通知标题和 AudioManagerImpl 媒体元数据均硬编码了旧名称 "红宝书"），已全部修复。2 个非阻塞发现已 defer 到后续改进。所有 5 个文件变更编译通过（BUILD SUCCESSFUL, 35 tasks, 4 executed, 31 up-to-date）。

变更文件清单：
1. `app/build.gradle.kts` - versionCode 9→10, versionName 1.2.1→2.0.0
2. `app/src/main/res/values/strings.xml` - app_name Hongbaoshu→Hongbaoxia
3. `app/src/main/res/values-zh/strings.xml` - app_name 红宝书→红宝匣
4. `app/src/main/java/com/xuyutech/hongbaoshu/audio/PlaybackService.kt` - 通知标题 红宝书→红宝匣
5. `app/src/main/java/com/xuyutech/hongbaoshu/audio/AudioManagerImpl.kt` - 媒体元数据 红宝书→红宝匣