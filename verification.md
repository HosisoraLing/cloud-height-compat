# 验证记录

本记录对应 1.1.1 的构建与离线检查，记录日期为 2026-10-13。检查在隔离的构建目录中运行，未启动 Minecraft 图形客户端或服务端。

## 参考版本

| 组件 | 版本 |
| --- | --- |
| 操作系统 | Windows |
| JDK | 21.0.9 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.249 |
| Iris | 1.8.14-beta.1+mc1.21.1 |
| Sodium | 0.8.12+mc1.21.1 |
| BSL | 10.1.1、10.1.8 |

高度策略的参考场景为世界底部 Y=-64、顶部 Y=768，自动模式的云底至少为 Y=832。

## 复现命令

```powershell
python build.py --minecraft 'C:/Games/Minecraft' --iris 'C:/Games/Minecraft/mods/iris-neoforge-1.8.14-beta.1+mc1.21.1.jar' --sodium 'C:/Games/Minecraft/mods/sodium-neoforge-0.8.12+mc1.21.1.jar' --test-shader 'C:/Games/Minecraft/shaderpacks/BSL_v10.1.1.zip' --test-shader 'C:/Games/Minecraft/shaderpacks/BSL_v10.1.8.zip'
```

将路径替换为实际安装位置。命令退出码为 **0**，共 **81 项断言通过**：61 项高度策略与光影检查、19 项 Sodium 注册与配置保存检查，以及 1 项独立 Java 进程重新读取检查。执行后的完整输出位于 `build/build.log`。

## 检查覆盖

- **发布构建：**使用实际 NeoForge 客户端依赖编译，Java class 版本为 65。发布 JAR 包含模组元数据和客户端 Mixin 配置，不包含测试类或第三方依赖类。
- **高度策略：**覆盖普通世界、扩高世界、较低维度、Y=4096 上限、自动模式、偏移、绝对高度、禁用状态、维度切换与非法配置。
- **Iris 接口：**检查实际 Iris 字节码中的 uniform 注入目标和 include 加载方法返回类型。
- **BSL 源码：**两个版本的云和云影源码均匹配预期结构；检查补丁不会重复应用，未知路径不修改。
- **Include 展开：**使用实际 Iris `IncludeProcessor` 展开 BSL 10.1.1 的 176 个和 BSL 10.1.8 的 182 个 shader stage，检查 include 完整展开且每个 stage 仅有一个 version 指令。
- **GLSL 预处理：**使用实际 Iris `JcppProcessor` 处理两个版本的 deferred1、gbuffers_terrain 和 gbuffers_water，在自动及固定高度、开启云影的组合中检查辅助函数定义和高度分支。
- **Sodium 选项：**使用实际 `ConfigBuilderImpl` 和 `Config` 验证页面注册、依赖关系、整数滑条、取值范围、默认值和禁用条件。
- **配置保存：**使用 NeoForge 的文件保存流程验证暂存、应用、取消、恢复默认和旧配置迁移；在另一个 Java 进程中重新读取已保存的手动模式与高度。

测试源码位于 [tests/Checks.java](tests/Checks.java) 和 [tests/SodiumChecks.java](tests/SodiumChecks.java)。配置保存检查使用隔离的模组容器与事件总线，测试文件位于 `build/menu-test/`。

## 未覆盖范围

离线检查没有覆盖实际 ModLauncher/Mixin 应用、GPU GLSL 编译、游戏画面、Sodium 菜单布局、鼠标拖动、GUI 缩放或服务器登录。IncludeProcessor 检查通过子类覆盖模拟 RETURN 注入，不能替代实际 Mixin 加载测试。

其他 Iris、Sodium 和 BSL 版本未逐一验证，整合包运行时交互和其他写死云高度的光影包也不在此检查范围内。

构建存在 NeoForge 配置列表 API 的弃用提示，无编译错误；最终 GLSL 离线预处理无告警。
