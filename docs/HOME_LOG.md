# Home（RikkaHub 个人定制版）开发与验收日志

## 0. 项目基线与安全红线
- **仓库**：`whale-Liu24/rikkahub-keep`（Public，fork 自 `rikkahub/rikkahub`，默认分支 `master`）
- **不可违背红线**：
  1. **1.8 物理隔离**：绝不触碰手机原有官方 1.8 版（包名 `me.rerere.rikkahub`），定制版永久锁死独立包名 `me.rerere.rikkahub.keep`，禁止应用内触发官方更新。
  2. **公开仓库零敏感**：严禁在代码、日志、提交信息中写入任何签名密码、Keystore 文件、API Key 或个人隐私内容。
  3. **低侵入原则**：定制主题、Markdown 预处理等尽量独立成新文件，减少改动上游源码，方便未来 `Sync fork` 升级。
- **本地与云端资产现状**：
  - 专属 50 年期 PKCS12 正式签名密钥已完成本地离线备份（永不入库）。
  - 仓库 Actions Secrets 已配置齐全 4 项签名凭证：`KEEP_KEYSTORE_BASE64`、`KEEP_KEYSTORE_PASSWORD`、`KEEP_KEY_ALIAS`、`KEEP_KEY_PASSWORD`（明文永不记录）。

---

## 1. 阶段 0 & 阶段 1：体检、同步、隔离与正式签名 CI（✅ 已完成）

### 完成内容
- **版本同步**：对齐官方上游 **3.3.1** 基线。
- **双开隔离与去噪**（[`5d9ecee`](https://github.com/whale-Liu24/rikkahub-keep/commit/5d9ecee0a8a0aa33c8fe0a7feb27cb27288fb634)）：
  - 锁定全变体包名为 `me.rerere.rikkahub.keep`，移除 `.debug` 后缀；核查 15 个 Manifest，Provider authorities 均为 `${applicationId}` 动态隔离；自定义 scheme 改为 `rikkahub-keep`。
  - 应用名称改为 `Home`；移除官方更新检查（`UpdateCard` / `UpdateChecker`）、更新通知开关及 Firebase/Google Services 依赖。
- **正式版云端流水线**（[`90622ae`](https://github.com/whale-Liu24/rikkahub-keep/commit/90622aea19ca777e4a0283e098f1d75bcc047cec)、[`a69117b`](https://github.com/whale-Liu24/rikkahub-keep/commit/a69117b) 及签名预检加固提交）：
  - 新增仅手动触发的工作流 `.github/workflows/build-home-release.yml`，输出 Artifact `Home-release-apk`。
  - 内置编译前 20 秒 `keytool` 开锁预检 + 上传前包名/应用名/Provider/签名断言。
  - **里程碑**：Run `#5` 绿勾通过（耗时 9m 39s），真机安装成功，与 1.8 版双开共存。

### 踩坑与解决备忘
1. **CI 环境反复失败（#1 / #2）**：手写 `sdkmanager` 安装命令与 runner 环境不匹配（报错 `command not found` / `platforms;android-37` 未找到）→ 直接复用上游 `daily-build.yml` / `release.yml` 的 JDK 21 + runner 内置 Android SDK 配置后一次通关。
2. **签名开锁失败（#3 / #4）**：编译 12 分钟后在 `:app:packageGithubRelease` 报 `keystore password was incorrect` → 重新核对覆写 Secrets 去除多余空白/前缀，并在工作流最前面增加 20 秒 `keytool` 预检，避免无效等待。
3. **前端长推理断流**：高推理强度偶发断流导致误停 → 改用中度推理并在异常时先只读查 `commits/master` 现场再续传。

---

## 2. 源码关键位置地图（阶段 0 核查沉淀）
（路径前缀：`app/src/main/java/me/rerere/rikkahub/`）
- **聊天页顶栏**：`ui/pages/chat/ChatPage.kt`（阶段 0 核查时 `TopAppBar` 容器色为 `Color.Transparent`，后续调整见阶段 2 和 2.5）
- **输入栏模糊**：`ui/components/ai/ChatInputHaze.kt`
- **主题色板**：`ui/theme/Color.kt`、`ui/theme/presets/`、`ui/theme/Theme.kt`
- **字号与行高**：`ui/pages/setting/SettingPreferencesUIPage.kt`、`data/datastore/Settings.kt`、`ui/components/message/ChatMessage.kt`
- **Markdown 渲染链**：外部依赖 BetterText（`com.github.re-ovo.bettertext:markdown`）；调用链 `ChatMessage.kt` → `ui/components/richtext/Markdown.kt` → `MaterialMarkdown.kt`

---

## 3. 阶段 2：Grok 质感界面重装 + 应用名单点配置（✅ 已完成）

### 完成记录

- 提交 [`918adbe`](https://github.com/whale-Liu24/rikkahub-keep/commit/918adbecbe8c99aa0485c90f81141c7bae316180)。[Run #6](https://github.com/whale-Liu24/rikkahub-keep/actions/runs/45463934587) 通过签名构建、APK 校验和产物上传，耗时 9m 27s。
- BetterText `v0.1.6` 排版结论。正文行高为实际字号 × 1.6，段落间距为实际字号换算成 dp 后 × 1.25。默认字号 16 sp、系统字体缩放 100% 时，正文行高为 25.6 sp，段落和普通列表项间距约 20 dp，紧凑列表项约 6.7 dp。

### 验收清单

- [x] 阶段 1 装机前置确认。#5 的 Home APK 与官方 1.8 版共存。
- [x] 顶栏与状态栏实底遮挡，滚动时无透字重影。
- [x] 独立深炭灰主题可选，包含深浅两套色板。
- [x] 中英文正文行高、段落间距与列表间距已调优。
- [x] 字号范围 50%–200%，每档 2 个百分点。
- [x] 悬浮消息跳转按钮默认隐藏，可在设置中开启。
- [x] 应用名由 Gradle 的 `home_app_name` 单点定义，8 个多语言 `strings.xml` 恢复上游的 `app_name`。
- [x] 真机覆盖更新与数据保留已验收，官方 1.8 版保持独立。

---

## 4. 阶段 2.5（Grok 质感细节精修）（✅ 已完成）

- 提交 [`6c5d16b`](https://github.com/whale-Liu24/rikkahub-keep/commit/6c5d16b152ac12c5be004dc68a35402588932250)。[Run #7](https://github.com/whale-Liu24/rikkahub-keep/actions/runs/45532065138) 通过签名构建、APK 校验和产物上传，耗时 9m 55s。

| 项目 | 改动前 | 改动后 |
|---|---|---|
| 普通助手正文，每侧 | 16 + 4 = 20 dp | 10 + 4 = 14 dp |
| 助手气泡正文，每侧 | 16 + 10 + 4 = 30 dp | 10 + 0 + 4 = 14 dp |
| 深炭灰 `background` / `surface` | `#141416` / `#1C1C1F` | 均为 `#0E0E10` |
| 输入栏 / 卡片 / 助手气泡 | `#1E1E21` / `#222226` / `#29292D` | `#18181B` / `#1B1B1E` / `#202024` |
| 正文 `onBackground` / `onSurface` | `#EAEAEF` | `#F0F0F4` |
| 列表圆点 `colorScheme.primary` | `#D7D2CB` | `#C5BFB7` |
| `TopAppBar` 静止及滚动底色 | 100% 不透明 `surface` | 100% 不透明 `background` |
| 正文 `letterSpacing` | 继承 Material 3 的 0.5 sp | 保留 0.5 sp，默认字号 16 sp、行高 25.6 sp |

边距按手机普通聊天布局计算，不含系统侧边栏、选择模式或宽屏抽屉。

### 验收清单

- [x] Run #7 的签名构建、包名与产物检查通过。
- [x] 真机核对普通及气泡模式的助手正文、用户气泡、引用与思考块、长代码块和消息操作图标，边距自然且无裁切。
- [x] 深炭灰暗色下滚动核对顶栏与状态栏，底色一致、无横带或透字，输入栏和气泡层次清晰。
- [x] 正式签名 APK 原地覆盖安装 Home 后保留对话记录，官方 1.8 版仍可独立使用。

### 阶段 2 & 2.5 踩坑与解决备忘

1. BetterText `v0.1.6` 没有独立的列表项间距参数。普通有序和无序列表沿用 `blockSpacing`，紧凑列表取其三分之一。
2. 缩略图并排对比发现，左右留白过宽会迫使字号调小，顶栏浅灰带也会割裂阅读区。缩小左右留白、压深炭灰底色并让顶栏与背景同色后，阅读观感显著改善。
3. 正式签名已通过覆盖更新验证，原有对话记录得以保留。后续使用同一正式签名升级 Home 可原地安装。

---

## 5. 阶段 3：Markdown 露星根治与纯净重导（⏳ 待开工）

### 待验收

- [ ] 脱敏结构单测绿灯：针对「全角标点/Emoji 紧贴 `**`」「加粗嵌套加粗」两类脱敏用例，通过 BetterText 真实解析验证无露星。
- [ ] 零误伤保护：代码块、行内代码、数学公式（`$` / `$$`）以及普通星号不被误改。
- [ ] 流式吐字稳定：新发带加粗与 Emoji 的对话时，流式输出不闪烁、不崩溃。
- [ ] 历史记录纯净重导：用纯净脚本重导原始记录后，原露星段落全部正常渲染。
- [ ] 仓库零隐私泄漏：测试代码与日志中不含任何个人对话原文。

---

## 6. 后续路线图
- **阶段 4（收尾搬家 & 可选静态换图标）**：确认所有历史记录安顿进 `Home` 后卸载旧 3.1.1 `[开发模式]` 版；如需更换专属桌面图标，在此阶段单独替换图标资源打包。
