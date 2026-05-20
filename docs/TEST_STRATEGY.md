# 测试策略 v1.0

覆盖解析、排版、音频映射和核心交互，保证离线阅读稳定。

## 1. 单元测试
- **ContentLoader**
  - JSON 解析：章节/段落/句子数、ID 唯一性、注释类型与 `ref`。
  - 音频映射：文件名前缀匹配句子 ID，缺失文件返回 null 并汇总 `missingSentenceAudioIds`。
- **PageEngine**
  - 给定屏幕参数生成页数，断言句子/段落顺序正确。
  - 段间距/行高配置生效（可检查布局数据）。
- **AudioManager**
  - 句子播放状态机：play/pause/resume/stop 状态变更。
  - 顺播逻辑：播放完成回调触发下一句。
  - BGM 控制：播放/暂停/下一首，状态流正确。
- **ProgressStore**
  - 读写进度/音频状态，冷启动恢复。

## 2. 仪表测试（UI / Espresso / Compose UI Test）
- 冷启动 → 封面 → 阅读 → 目录跳转。
- 翻页手势与动画触发（左右滑动、点击区域）。
- 朗读按钮：播放当前句子，高亮显示；完成后自动下一句。
- 跨章节跳转时朗读停止；返回后可重新播放。
- BGM 控制面板：播放/暂停/下一首/静音。
- 进度恢复：重启后回到上次页/句。

## 2.1 真机自动化执行要求
- 真机冒烟测试使用 `app:connectedDebugAndroidTest`，设备必须保持解锁、亮屏、前台可启动应用。
- Reader 主流程测试使用 UiAutomator 驱动真实系统窗口，避免部分 MIUI 真机上 Compose `ActivityScenario` 在启动 Activity 前卡住。
- 如果测试进程被宿主超时中断，先执行 `adb shell am force-stop com.xuyutech.hongbaoshu` 和 `adb shell am force-stop com.xuyutech.hongbaoshu.test`，必要时再用 `adb shell pidof com.xuyutech.hongbaoshu` 确认无残留进程。
- 每次真机测试前用 `adb shell dumpsys window` 确认 `screenState=SCREEN_STATE_ON` 且 `mDreamingLockscreen=false`。
- MIUI 真机会对安装弹窗做强确认；调试循环中优先保留已安装包，先用 `adb install -r -t app/build/outputs/apk/debug/app-debug.apk` 和 `adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 覆盖安装，再用 `adb shell am instrument -w -r com.xuyutech.hongbaoshu.test/androidx.test.runner.AndroidJUnitRunner` 直接复跑测试。只有 APK 内容变化时才重新覆盖安装，避免反复卸载重装触发授权。

## 3. 性能与体验
- 启动时间：封面加载 <1s。
- 翻页帧率：目标 60fps（可通过 Systrace/调试工具观察）。
- 音频无爆音：切歌淡入淡出、朗读开始/暂停无杂音。
- 内存：分页缓存 LruCache 不导致 OOM，滑动多章不崩溃。

## 4. 兼容性
- 设备：Android 8.0、11、14 常见分辨率；横竖屏（如支持）。
- 离线模式：无网络权限/无请求。

## 5. 手动验收清单
- 封面 → 跳转上次进度。
- 目录列出全部章节，点击可跳转。
- 每页句子可播放且高亮，缺失音频提示。
- BGM 开关、下一首、静音可用。
- 翻页音效正常。
- 退出重进保持进度与音频状态。
