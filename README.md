# SuperGemma Mobile Test

独立 Android 真机测试项目，用于验证以下组合：

- DeepSeek OpenAI-compatible API 文字对话；
- `typomonster/supergemma4-e4b-abliterated-litert-lm` 本地图片识别；
- `.litertlm` 模型从手机文件选择器导入，模型权重不进入 APK 或 GitHub；
- 本地识图结果可选择性地以纯文字发送给 DeepSeek，原图片不会上传。

## 测试流程

1. 安装 Actions 构建出的 APK。
2. 在“本地识图”打开模型下载页，下载约 3.65GB 的 `.litertlm` 文件。
3. 点击“选择模型文件”导入。App 会复制模型、校验完整大小并计算 SHA-256。
4. 选择 CPU 或 GPU 主后端，点击“加载模型”。视觉后端固定使用 GPU，推测解码固定关闭。
5. 选择普通或 NSFW 图片，点击“开始本地识图”。
6. 如需测试组合链路，再点击“把识图文字发送给 DeepSeek”。

> 导入完成后，模型副本位于 App 专属目录，可以删除手机“下载”目录中的原文件。卸载 App 会同时删除导入副本。

## 隐私

- API Key 由用户在 App 中填写，使用 Android Keystore 加密后仅存本机。
- 模型推理与图片预处理完全在手机本地进行。
- 只有用户主动点击“把识图文字发送给 DeepSeek”时，识图后的文字才会上云。
- 仓库及构建产物不包含 API Key、测试图片或模型权重。

## 已知风险

这是验证性质的测试 App。社区转换模型是否完整保留 Gemma 4 E4B 的视觉编码器，需要以真机结果为准。App 会把“视觉部分缺失”“图片模板不兼容”“引擎创建失败”等错误分开显示，便于判断下一步是否值得接入 AI 伴侣。

模型来源：[typomonster/supergemma4-e4b-abliterated-litert-lm](https://huggingface.co/typomonster/supergemma4-e4b-abliterated-litert-lm)

## 构建

```bash
gradle testDebugUnitTest assembleDebug
```

GitHub Actions 会生成 APK、SHA-256 文件、14 天构建产物和 Draft Prerelease。
