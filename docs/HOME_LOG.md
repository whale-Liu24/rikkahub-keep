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
- **聊天页顶栏**：`ui/pages/chat/ChatPage.kt`（`TopBar()` 中 `TopAppBar` 容器色当前为 `Color.Transparent`）
- **输入栏模糊**：`ui/components/ai/ChatInputHaze.kt`
- **主题色板**：`ui/theme/Color.kt`、`ui/theme/presets/`、`ui/theme/Theme.kt`
- **字号与行高**：`ui/pages/setting/SettingPreferencesUIPage.kt`、`data/datastore/Settings.kt`、`ui/components/message/ChatMessage.kt`
- **Markdown 渲染链**：外部依赖 BetterText（`com.github.re-ovo.bettertext:markdown`）；调用链 `ChatMessage.kt` → `ui/components/richtext/Markdown.kt` → `MaterialMarkdown.kt`

---

## 3. 阶段 2：Grok 质感界面重装 + 应用名单点配置（⏳ 进行中）

### 阶段 2 验收清单（全部勾选后进入阶段 3）
- [x] **阶段 1 装机前置确认**：`#5` 产出的 `Home-release-apk` 已成功安装到手机，与 1.8 版完美共存。
- [ ] **去顶栏半透重影**：聊天页顶栏变为 100% 不透明实底，消息向上滚动时不再透出残字重影，且状态栏高度无错位。
- [ ] **Grok 暖暗色主题**：新增独立「深炭灰」主题预设（非冷蓝灰、非刺眼纯黑），正文为柔和暖灰白，暗光阅读不刺眼。
- [ ] **中英文阅读排版**：正文行高（`lineHeight`）、段落间距、无序/有序列表间距明显拉开，中文每行约 19～20 字不显发挤，英文断行舒展。
- [ ] **精细化字号滑块**：字号调节步进变细腻（如每档 2%），不再出现“调一格就缩太小”的断层。
- [ ] **清理视觉遮挡**：聊天页右侧遮挡文字的悬浮跳转按钮可彻底关闭或默认隐藏。
- [ ] **应用名单点配置**：`app_name` 改为 `build.gradle.kts` 单点定义（桌面仍显示 `Home`），8 个多语言 `strings.xml` 还原低冲突状态。
- [ ] **真机回归无损**：新 APK 可直接覆盖更新手机里的 `Home`，原有测试数据保留，1.8 版依然纹丝不动。

---

## 4. 后续路线图
- **阶段 3（Markdown 露星根治）**：针对中文全角标点 + Emoji 紧贴 `**` 及加粗嵌套在 BetterText 中失效的问题做渲染前规范化（须配原句 Kotlin 单元测试，不破坏流式吐字/代码块/公式），并用纯净脚本重导历史记录。
- **阶段 4（收尾搬家 & 可选静态换图标）**：确认所有历史记录安顿进 `Home` 后卸载旧 3.1.1 `[开发模式]` 版；如需更换专属桌面图标，在此阶段单独替换图标资源打包。
