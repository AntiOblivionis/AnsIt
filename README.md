<p align="center">
  <img src="design/ansit-icon.svg" width="120" height="120" alt="AnsIt Logo">
</p>

<h1 align="center">AnsIt</h1>

<p align="center"><strong>更安全的Android AI答题助手</strong></p>

<p align="center">
  <a href="https://ansit.tgwgroup.ltd">官方网站</a> ·
  <a href="https://ansit.tgwgroup.ltd/#download">下载安装</a> ·
  <a href="#快速开始">使用教程</a> ·
  <a href="https://github.com/AntiOblivionis/AnsIt">GitHub</a>
</p>

<p align="center">Android 12+ · Root + LSPosed · MIT License</p>

**长按状态栏，让 AI 看懂题目，直接在屏幕上给出解答。**

AnsIt 把反检测放在设计的中心：从触发、识屏到答案显示，尽量减少目标应用可以感知的额外信号，让普通屏幕捕获保留完整底图。

## 反检测，贯穿一次答题

- **触发留在系统界面。** 仅在 SystemUI 中识别长按，目标应用内不注入 AnsIt 的手势处理代码，也不新增全屏触摸窗口。
- **识屏避开系统截图界面。** 通过 Root 捕获当前画面，日常使用不弹出截图工具，也不把截图写入相册。
- **答案独立显示，捕获保留底图。** 答案图层排除标准屏幕捕获，避免将答案一起录入，也避免用纯色块遮盖原画面。在已验证环境中，答案显示与收起时，普通捕获保留的底图一致。

已有测试覆盖真实长按、截图与录屏事件观察、普通权限捕获和底图像素比对。[查看专项验证与适用范围 →](https://ansit.tgwgroup.ltd/evidence/)

具体表现取决于系统、ROM 与应用的检测方式，请结合专项验证范围和设备实测使用。

## 让答题更顺手

- **轻振提示，松手解答。** 长按达到判定时间后轻振提醒，再次长按即可取消任务或收起答案。
- **接入你选择的模型。** 支持 OpenAI 格式的视觉模型接口，自行填写地址、API Key 和模型名称。
- **每次识屏都是独立会话。** 本次截图与解答单独处理，不携带上一张截图的问答历史。
- **需要时再搜索。** 可选接入 DeepSeek 网页搜索，由模型按需查询公开资料。
- **贴合你的屏幕。** Material 3 莫奈配色，支持调整答案区域的位置、大小与透明度。

## 快速开始

### 1. 准备设备

- Android **12 或更高版本**。
- 已获取 **Root** 权限。
- 已安装并能够正常使用 **LSPosed**。
- 准备支持图片输入的模型接口与 API Key。

触发采用 Android 通用状态栏入口，不依赖小米专有类。不同 ROM 的系统接口仍有差异，安装成功不代表设备已经完整适配。

### 2. 安装与激活

1. 从[官网下载安装包](https://ansit.tgwgroup.ltd/#download)，安装并打开 AnsIt。
2. 按应用内引导允许 Root 权限。
3. 在 **LSPosed → 模块 → AnsIt** 中启用模块。
4. 作用域仅勾选 **系统界面（com.android.systemui）**。
5. 返回 AnsIt，通过引导中的入口**重启 SystemUI**，等待状态栏恢复。

### 3. 连接模型

在「模型配置」中填写：

| 配置项 | 填写内容 |
| --- | --- |
| 接口地址 | 服务商提供的 OpenAI 兼容地址，支持根地址、`/v1` 或完整的 `/chat/completions` 地址 |
| API Key | 对应服务商的 API Key |
| 模型名称 | 服务商提供的、支持图片输入的模型 ID |

点击 **「检测并保存」**，检测通过后即可使用。纯文本模型无法完成识屏解答。

检测会发送测试图片，并产生真实 API 请求；费用按你所用服务商的规则计算。

### 4. 开始答题

1. 在 AnsIt 中点击 **「开始」**，等待服务就绪。
2. 打开要提问的画面，**长按状态栏电池附近**。
3. 感到轻振后松手，等待屏幕上出现解答。
4. 再次长按同一区域，可取消解答或收起答案。

电池布局无法识别时，**长按状态栏最右侧**。没有振动马达的设备，可按住至设置的长按时间后松手。

使用结束后，在应用内点击 **「停止」**。关闭设置页面不会停止已启动的服务。

### 可选：启用网页搜索

在「DeepSeek 网页搜索」中填写 DeepSeek 平台 API Key，打开 **「允许 AI 按需搜索」**，再点击 **「检测并保存」**。

启用搜索时，主模型需要同时支持图片输入和工具调用。按界面提示完成检测后，模型会在需要资料时搜索，也可以直接回答。关闭搜索会保留 Key，方便下次启用。

## 常见问题

**按住电池没有反应？**

先确认服务已经开始、LSPosed 模块已启用且作用域正确，再重启一次 SystemUI。特殊电池布局可尝试长按状态栏最右侧。锁屏、服务停止或状态无法确认时不会触发；全屏隐藏状态栏时，需要先调出状态栏。

**安装更新后需要做什么？**

打开 AnsIt，让应用检查 Root 服务与模块状态，然后重启一次 SystemUI，使新模块生效。仅覆盖 APK 不会替换已经加载到 SystemUI 中的旧代码。

**截图会发送到哪里？**

截图直接发送到你填写的模型接口，AnsIt 官网不参与中转。启用网页搜索时，搜索关键词会发送到 DeepSeek。API Key 保存在应用私有目录中，请使用可信的接口与设备环境。

**AI 的答案一定正确吗？**

识屏清晰度、题目条件与模型能力都会影响答案，重要结果请自行核对。

更多说明见[使用指南](https://ansit.tgwgroup.ltd/guide/)与[数据说明](https://ansit.tgwgroup.ltd/privacy/)。

## 作者与反馈

由 **[AntiOblivionis](https://github.com/AntiOblivionis)** 开发。

- 个人主页：[id.tgwgroup.ltd](https://id.tgwgroup.ltd)
- 项目官网：[ansit.tgwgroup.ltd](https://ansit.tgwgroup.ltd)
- 问题反馈：[GitHub Issues](https://github.com/AntiOblivionis/AnsIt/issues)
- 邮箱：[antioblivionis@gmail.com](mailto:antioblivionis@gmail.com)

欢迎提交设备兼容性反馈、改进建议与 Pull Request。反馈问题时，请说明应用版本、Android / ROM 版本与复现步骤，并隐去截图、日志中的个人信息及 API Key。

## 开源协议

本项目采用 **[MIT License](LICENSE)**，允许使用、修改与分发，请保留版权及许可声明。第三方组件遵循各自的许可证。

Copyright © 2026 AntiOblivionis.
