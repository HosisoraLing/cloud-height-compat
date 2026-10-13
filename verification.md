# 离线验证记录

日期：2026-10-13，Asia/Hong_Kong。

产物：`cloud-height-compat-1.1.1-neoforge-1.21.1.jar`。

参考安装仅用于只读访问编译依赖、配置和光影文件，不包含在本仓库中。

## 环境和命令

- Windows / JDK 21.0.9。
- Minecraft 1.21.1，NeoForge 21.1.249。
- Iris `1.8.14-beta.1+mc1.21.1`。
- Sodium `0.8.12+mc1.21.1`，含其打包的原生 Config API。
- BSL `10.1.1` 和 `10.1.8`。
- 参考 Tectonic `3.0.26`，配置 `min_y=-64`、`max_y=768`、`vertical_scale=2.5`。

```powershell
python build.py --minecraft 'C:/Games/Minecraft' --iris 'C:/Games/Minecraft/mods/iris-neoforge-1.8.14-beta.1+mc1.21.1.jar' --sodium 'C:/Games/Minecraft/mods/sodium-neoforge-0.8.12+mc1.21.1.jar' --test-shader 'C:/Games/Minecraft/shaderpacks/BSL_v10.1.1.zip' --test-shader 'C:/Games/Minecraft/shaderpacks/BSL_v10.1.8.zip'
```

最终命令退出码 **0**，共 **81 项断言通过**：61 项原有策略和光影检查，19 项 Sodium 注册/保存检查，1 项独立 Java 进程读取检查。执行后完整输出位于 `build/build.log`。

## 已覆盖

- T0：用实际 NeoForge 客户端运行时编译全部发布源码，Java class 版本为 65；发布 jar 含模组元数据和客户端 Mixin 配置，不包含测试类、Minecraft 类或第三方依赖。
- T1：普通世界、扩高世界、较低维度、4096 顶部、自动模式、手动偏移、负偏移、绝对覆盖、关闭功能、不适用维度、维度切换和无效配置。
- 在实际 Iris jar 的字节码中核对 DoubleSupplier uniform 注册调用，命中一次；核对 include 加载方法的返回类型。
- 两个真实 BSL 压缩包中的云和云影源码均命中预期结构；补丁不会重复应用，未知路径不修改。
- 通过实际 Iris `IncludeProcessor` 展开 BSL 10.1.1 的 176 个和 BSL 10.1.8 的 182 个 shader stage，所有 include 展开且每个 stage 仅保留一个 version 指令。
- 通过实际 Iris `JcppProcessor` 预处理两个版本的 deferred1、gbuffers_terrain、gbuffers_water；每个程序测试 `CLOUD_HEIGHT=-1` 和 `320`，启用云影，检查 helper 唯一定义和正确高度分支。
- 通过包内实际 Sodium `ConfigBuilderImpl` 和 `Config` 注册页面，完成依赖关系验证；检查高度控件为实际 `SliderControl`，范围 -512～8192，步进 1，默认 832。
- 用真实 NeoForge `LoadedConfig` / `ModConfigSpec.save` 文件保存路径，验证暂存不影响已应用设置、应用写入 TOML、共享 storage 只保存一次、取消恢复旧值、恢复默认、开关禁用状态和旧配置迁移。测试使用隔离的模组容器与事件总线，不启动游戏。
- 启动另一个 Java 进程重新读取测试 TOML，恢复手动模式和高度 960。测试配置、FML 路径和文件位于构建目录 `build/menu-test/`，未访问游戏实例配置的写入路径。

## 未覆盖与限制

用户要求不要影响正在玩的游戏，因此没有启动任何 Minecraft 客户端或服务端。无游戏 run ID、客户端/服务端退出码或渲染截图。

用户在本次请求中反馈已经完成 1.0.0 实际游戏测试；这属于用户提供的验证信息。本次新增 Sodium 菜单的实际布局、鼠标点击/拖动、不同 GUI 缩放和游戏内即时高度变化尚未实机检查。离线测试验证真实 Sodium 选项状态和文件保存流程，没有模拟截图或声称实际控制过窗口。

尚未验证真实 ModLauncher/Mixin 应用、GPU GLSL 编译、进入实际扩高世界后的画面、服务器登录及整合包所有模组之间的运行时交互。IncludeProcessor 测试使用子类覆盖来模拟 RETURN 注入，不能代替 Mixin 实际应用测试。

编译器报告了 NeoForge 配置列表 API 的弃用提示；编译成功，无编译错误。最终 GLSL 离线预处理无告警。其他 Iris / BSL 版本未逐一验证，其他写死高度的光影尚未适配。

没有修改参考实例的 mods、config、shaderpacks、世界或启动参数；没有自动安装产物。
