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

## 5. 阶段 3：Markdown 露星根治与纯净重导（✅ 已完成）

### 完成记录

- App 侧提交 [`c85b22a`](https://github.com/whale-Liu24/rikkahub-keep/commit/c85b22a6530a6c2631e71f9edb1ddbbe45b87532) 新增 `HomeMarkdownNormalizer.kt` 和 `HomeMarkdownNormalizerTest.kt`，在 `MaterialMarkdown.kt` 接入规范化，并在 `build-home-release.yml` 的签名预检后增加 JVM 单测闸门。
- [Run #8](https://github.com/whale-Liu24/rikkahub-keep/actions/runs/45538403185) 通过 BetterText JVM 单测、签名构建、APK 校验和产物上传，耗时 9m 40s。
- 本地侧验收记录。使用移除旧人工垫空格补丁、保留思考计时修复（`startTime` / `duration`）的纯净脚本重导原始历史记录。真机正文和流式输出验收通过。

### 验收清单

- [x] 脱敏结构单测绿灯：针对「全角标点/Emoji 紧贴 `**`」「加粗嵌套加粗」两类脱敏用例，通过 BetterText 真实解析验证无露星。
- [x] 零误伤保护：代码块、行内代码、数学公式（`$` / `$$`）以及普通星号不被误改。
- [x] 流式吐字稳定：新发带加粗与 Emoji 的对话时，流式输出不闪烁、不崩溃。
- [x] 历史记录纯净重导：用纯净脚本重导原始记录后，原露星段落全部正常渲染。
- [x] 仓库零隐私泄漏：测试代码与日志中不含任何个人对话原文。

### 阶段 3 踩坑与解决备忘

1. JVM 下 `U+200B` 无法通过 `CommonDefsImplJvm.kt` 的空白判定。`U+200A`（Hair Space）通过 `Character.isSpaceChar`，用来在开闭失败的 `**` 外侧精确补边界。
2. 嵌套加粗修复收紧为括号包裹的同行结构，保留普通并列加粗。CI 用 BetterText 的真实静态及流式解析器做回归测试。
3. 导入脚本移除旧的人工垫空格补丁后，原始文本交给 App 渲染层统一处理。此次重导验收覆盖了旧补丁拆裂嵌套星号的问题。

---

## 6. 阶段 4（方案 B）：护眼细节打磨 + 历史预览页改回左右气泡与时间戳（✅ 已完成）

- 提交 [`46dc376`](https://github.com/whale-Liu24/rikkahub-keep/commit/46dc376198e8ac0af4db5cbd1bd3377674a3af53)。[Run #9](https://github.com/whale-Liu24/rikkahub-keep/actions/runs/45623496664) 通过 JVM 单测、签名构建、APK 校验与产物上传，耗时 10m 26s。
- 真机验收按项目验收记录归档。

### 方案 B 实现摘要

- 深炭灰暗色用户气泡使用 `#242428` 底色和 `#F0F0F4` 文字。用户气泡单击不再进入编辑，三点菜单仍可编辑。
- 思考正文去除 32 dp 的额外起始缩进。字号按正文基准的 90% 乘聊天字号比例计算，最低 12 sp，行高为字号的 1.55 倍。
- 历史预览按原始消息节点逐条展示，用户靠右、助手靠左。有效时间戳显示在气泡下方，搜索按条计数，点击按消息 ID 找到当前索引。压缩检查点继续使用独立卡片。

### 改动文件

- `ui/theme/presets/GrokCharcoalTheme.kt`
- `ui/components/message/ChatMessage.kt`
- `ui/components/ui/ChainOfThought.kt`
- `ui/components/message/ChatMessageReasoning.kt`
- `ui/pages/chat/ChatListPreview.kt`
- `docs/HOME_LOG.md`

以上 Kotlin 路径以 `app/src/main/java/me/rerere/rikkahub/` 为前缀。

### 验收清单

- [x] #9 通过现有 Markdown 单测闸门、正式签名和 APK 校验，覆盖安装后 Home 数据与官方 1.8 隔离正常。
- [x] 深炭灰用户气泡、选中态控件与正文可清晰区分。
- [x] 滑动对话时单击气泡不触发编辑，三点菜单编辑、长按选字复制及附件交互正常。
- [x] 思考正文对齐卡片内缘，在多个字号档位下可舒适阅读。
- [x] 预览页左右气泡、逐条时间戳、搜索计数和点击定位准确，压缩检查点正常显示。

### 改动备忘

1. 用户气泡采用深炭灰暗色预设中的中性容器色。编辑入口保留在三点菜单，减少滚动误触。
2. 思考正文取消额外缩进，字号提高并设 12 sp 下限。工具步骤沿用原缩进。
3. 预览按单条消息筛选和计数。点击时通过消息 ID 重新查找原列表索引，避免将筛选后的序号用作跳转目标。

---

## 7. 阶段 4.5：收尾精修（24 小时制 & 可选自定义图标）（✅ 已完成）

- 主聊天与历史预览的当天时间统一为 `HH:mm`。历史预览的非当天时间保留短日期和时分。主聊天的非当天时间、消息详情及共用完整格式的聊天导出保留日期和 `HH:mm:ss`。
- 内置网页版聊天消息时间也改为 24 小时制，保留原有日期和秒数精度。

- 24 小时制提交 [`5290085`](https://github.com/whale-Liu24/rikkahub-keep/commit/5290085b7f1259b7dac41689530ceb50e24dbbea)。[Run #10](https://github.com/whale-Liu24/rikkahub-keep/actions/runs/45645013858) 通过单测、签名构建、APK 校验和产物上传，耗时 12m 01s。
- 雪屋图标接入提交 [`8377bc4`](https://github.com/whale-Liu24/rikkahub-keep/commit/8377bc4d33a59ecd4ff0aa15c926b5c19df55fa7)。雪屋自适应桌面图标使用独立的 `home_ic_launcher`，前景引用已上传的雪屋 PNG，背景使用 `#4A9FF5`。图标未定义单色层，官方 20 个 PNG 资源保留。应用最低支持 API 26，无需旧版图标兜底。
- 聊天图片导出的顶部角标同步使用雪屋 PNG，保留原有 60 dp 尺寸。

### 验收清单

- [x] Run #10 通过现有单测闸门、签名构建和 APK 校验。
- [x] 按真机验收记录，简体中文和繁体中文下的主聊天、历史预览和消息详情均使用 24 小时制，日期精度保持原样。
- [x] [Run #11](https://github.com/whale-Liu24/rikkahub-keep/actions/runs/45659428221) 通过单测闸门、签名构建、APK 校验和产物上传，工作流耗时 8m 43s。真机验收确认雪屋图标与导出角标生效。因自适应裁切偏紧，转入阶段 5 验收新版弯月构图。

---

## 8. 阶段 5（番外精修：顶栏 1.8 布局、整词搜索与弯月雪屋定稿）（✅ 已完成）

- 本节随 [`24ca857`](https://github.com/whale-Liu24/rikkahub-keep/commit/24ca857e6ed00119fae1dc6ba3c137c193e10d44) 提交。聊天顶栏从左到右为新建对话、对话地图、更多。新建用圆形加号气泡，对话地图可原位打开或关闭，更多保留导出和删除。
- 历史预览搜索在输入框与命中计数之间新增默认关闭的「整词」开关。筛选、计数和摘要高亮共用 ASCII 词边界匹配逻辑，中文仍可按子串搜索。新增纯 JVM 测试覆盖两种模式、摘要裁切和高亮区间。
- 弯月雪屋图标已随上传提交 [`67bf428`](https://github.com/whale-Liu24/rikkahub-keep/commit/67bf428a6d529bb19bbc0d2e136883dd59af485b) 到达 `master`，核对为 432×432 PNG。沿用桌面自适应图标和导出角标对该资源的引用。

### 验收清单

- [x] [Run #12](https://github.com/whale-Liu24/rikkahub-keep/actions/runs/45877112541) 通过 Markdown 单测闸门、正式签名、APK 校验和产物上传，工作流耗时 8m 26s。
- [x] 按真机验收记录，弯月雪屋桌面图标和导出角标显示正常。
- [x] 真机核对顶栏顺序、新建对话图标与对话地图原位开关，更多菜单只显示导出和删除。
- [x] 真机核对整词开关默认关闭，开启后英文整词和中文搜索的计数、摘要高亮与点击跳转准确。

---

## 9. 阶段 5.1（#13）：对话地图切换动画改为 1.8 原地淡入淡出（✅ 已完成）

- 提交 [`9a27494`](https://github.com/whale-Liu24/rikkahub-keep/commit/9a27494d0b3ab393fc0ea4b851328a75d5d2f114)。[Run #13](https://github.com/whale-Liu24/rikkahub-keep/actions/runs/45882778303) 成功，工作流耗时 13m 19s。
- [x] 原地淡入淡出已生效。长对话首次进入地图仍会因预览摘要冷算短暂闪空，转入阶段 5.2 优化。

---

## 10. 阶段 5.2（#14）：地图丝滑缓存、1.8 侧边栏图标与导出全链路升级（✅ 已完成）

- 实现提交 [`1a9c7e7`](https://github.com/whale-Liu24/rikkahub-keep/commit/1a9c7e7aa5e860d172eae123859d7f35fdf48d84)。[Run #14](https://github.com/whale-Liu24/rikkahub-keep/actions/runs/45935753710) 成功，工作流耗时 8m 40s。

### 验收清单

- [x] Actions 通过 Markdown 单测闸门、签名预检、正式签名构建、APK 校验和产物上传。
- [x] 源码核对：地图摘要在聊天页后台预备，按消息复用未变化的摘要。数据就绪后开始 200 ms 淡变，搜索词和整词开关可恢复。
- [x] 源码核对：左上角使用 `HugeIcons.ListTree` 并沿用侧边栏打开逻辑。
- [x] 源码核对：顶栏导出初始选中 0 条。全选与取消全选可切换，未选有效消息时确认禁用。
- [x] 源码核对：长图移除域名与底部免责声明，保留标题、24 小时制时间和雪屋角标。截图前解码头像，保留彩色标志原色。
- [x] 源码核对：API 29+ 的 PNG 保存通过 MediaStore 写入 `Pictures/Home`，写入完成后发布到图库。
- [ ] API 26–28 回退到系统文件选择器，仅源码/分支逻辑核对，尚无实机验收记录。

真机反馈：保留左滑手势后，拖动叠加原地透明度淡变，手指移动与画面变化错位，切换发僵。继续评估 1.8 经典图标、消息头尾排布与输入栏样式。

---

## 11. 阶段 6：1.8 手感深度复刻评估（⏳ 只读摸底中）

- [ ] 对比原版地图动效，评估保留左滑时的跟手位移与阈值触发淡变。
- [ ] 核查 1.8 Lucide 核心图标及轻量引入方式。
- [ ] 对比消息抬头、时间戳和底部操作及统计排布。
- [ ] 评估底部输入栏分体胶囊样式与键盘、附件及语音交互。

### 收尾待办

- [ ] 确认 Home 的历史记录和覆盖更新正常后，卸载旧版 3.1.1 `[开发模式]`。
