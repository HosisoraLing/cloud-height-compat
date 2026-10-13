# Cloud Height Compat 1.1.1

Minecraft **1.21.1 / NeoForge 21.1.x** 的客户端模组，针对扩高世界中的 Iris 光影体积云位置问题。

## 安装

1. 在你方便退出游戏后，移除旧版 `cloud-height-compat-1.0.0-neoforge-1.21.1.jar`，将 `cloud-height-compat-1.1.1-neoforge-1.21.1.jar` 放进客户端实例的 `mods` 文件夹。不要同时安装两版。
2. 保留 Iris 1.8.x 和 Sodium **0.8.x**。无需额外的设置界面模组，无需在服务器安装本模组。
3. 启动游戏，选择 BSL；体积云模式为 BSL 的 `Clouds = Volumetric`。自动高度和固定 `CLOUD_HEIGHT` 均可使用。

第一次启动会生成 `config/cloud-height-compat-client.toml`。不需要更换或修改原光影压缩包。

请手动安装模组。本仓库不包含游戏实例、存档、整合包或第三方光影文件。

## 默认行为

- 从客户端收到的维度信息读取实际世界顶部 `min_y + height`，不依赖 Tectonic 等具体模组的配置文件。
- 世界顶部超过 Y=320 时，体积云底部至少为 `世界顶部 + 64`。光影原先设置得更高时保留较高值。
- 参考扩高配置中，Tectonic 顶部为 Y=768，因此默认云底为 **Y=832**。
- 普通主世界以及顶部不超过 Y=320 的维度，默认保留光影原高度。
- 无天空、有天花板、原版维度效果不提供云高度的维度不做高度修正。
- 云底、云顶（原厚度保持）、水面反射中的体积云、开启时的云影一起使用修正后的高度。
- 不扫描地形，不生成区块，不修改世界高度、存档、数据包、天气或玩家坐标。只修正光影高度数据和内存中的 BSL 源码。

## 调整高度

### 在 Sodium 菜单中调整

进入 **选项 → 视频设置 → Cloud Height Compat → 体积云高度**。

1. 保持“云高度兼容”开启。
2. 开启“手动设置云高度”。默认关闭，以保留你已经测试过的自动规则和当前云层位置。
3. 拖动“体积云高度”滑条，显示值为云底 Y 坐标，默认 **832**。范围 **-512～8192**；选中滑条后可用左右方向键每次微调 1 格。
4. 点击 Sodium 的“应用”保存并生效。云和云影会更新，无需重载光影或重启游戏。未应用的修改使用 Sodium 的取消/还原操作撤销。

关闭“手动设置云高度”即恢复原来的自动/偏移规则。Sodium 的恢复默认值功能会恢复自动模式，并把手动滑条值还原为 832。

升级时保留已有 `cloud-height-compat-client.toml`；NeoForge 会补充新字段，不需要删除配置。

### 配置文件

默认配置：

```toml
enabled = true
automatic = true
clearance = 64
extraOffset = 0.0
manualHeightEnabled = false
manualHeight = 832
dimensionOverrides = []
```

`clearance` 为扩高世界顶部上方预留的距离。`extraOffset` 在自动修正后加减，可为负数。

如果你希望云层处于山腰，而非高于整个建造范围，可以指定绝对高度：

```toml
dimensionOverrides = ["minecraft:overworld=600"]
```

这里的 600 是云底 Y 坐标，覆盖菜单中的手动高度、自动规则和额外偏移；不改变云厚度。多个维度以逗号分隔，重复的维度以最后一项为准。若菜单滑条在当前维度没有效果，请检查是否有该维度的专属覆盖。

高度优先级：维度专属高度 → 开启时的手动高度 → 原自动/偏移规则。`automatic = false` 可在关闭手动模式时只使用偏移；`enabled = false` 关闭所有修正。

NeoForge 的客户端配置重载后，新数值由逐帧 uniform 更新传入，不需要重新生成世界。

## 兼容范围与验证

本次在 JDK 21.0.9、NeoForge **21.1.249**、Iris **1.8.14-beta.1+mc1.21.1**、Sodium **0.8.12+mc1.21.1** 的实际安装依赖上编译。

对用户实例中的 **BSL 10.1.1 和 10.1.8** 做了源码匹配、实际 Iris include 展开和 GLSL 预处理检查，包含自动/固定高度、地形云影、水面程序和防重复注入；高度策略覆盖普通世界、扩高世界、维度切换、关闭功能、偏移及配置非法值。

Iris 依赖声明允许 1.8.x。其他 Iris 版本未逐一运行验证。其他光影包使用 Iris `cloudHeight` uniform 时可获得修正；自己写死高度的其他光影包需要单独适配。BSL 的 Skybox 云模式不在本次体积云适配范围内。

用户已反馈完成 1.0.0 的实际游戏测试。此次新增菜单通过实际 Sodium 构建器/配置状态和 NeoForge 文件保存流程验证，包含暂存、应用、取消、启用条件、默认值、旧配置迁移和独立 Java 进程重新读取；全部 **81 项离线检查通过**。

使用 Sodium 自带的整数滑条，通过官方 Config API 注册，不新增 Sodium Mixin。参考：[Sodium Config API 文档](https://github.com/CaffeineMC/sodium/blob/dev/common/src/api/java/net/caffeinemc/mods/sodium/api/config/USAGE.md)。构建使用的是包内 0.8.12 实际 API，未套用其他 Minecraft 版本的类名。

为了不影响用户正在玩的游戏，此次**没有启动游戏或操作用户窗口**。新增菜单的实际布局、鼠标拖动与截图仍未做实机检查；详细记录见 `verification.md`。

## 构建和离线检查

需要 Python 3.10+、JDK 21，以及已经完整安装的 NeoForge 1.21.1 客户端。使用该版本的 Mojang 命名生产运行时编译；无需额外重混淆，不启动游戏。

```powershell
python build.py --minecraft 'C:/Games/Minecraft' --iris 'C:/Games/Minecraft/mods/iris-neoforge-1.8.14-beta.1+mc1.21.1.jar' --sodium 'C:/Games/Minecraft/mods/sodium-neoforge-0.8.12+mc1.21.1.jar' --test-shader 'C:/Games/Minecraft/shaderpacks/BSL_v10.1.1.zip' --test-shader 'C:/Games/Minecraft/shaderpacks/BSL_v10.1.8.zip'
```

替换为自己的安装路径。若 JDK 不在 PATH，加 `--jdk '你的 JDK 21 根目录'`。只编译时省略 `--test-shader`。

输出在 `build/`，测试源码不会装入发布 jar。构建进程限制堆内存，并在 Windows 使用低于正常的调度优先级。

源码遵循 **AGPL-3.0-only** 许可，完整条款见 [LICENSE](LICENSE)。未打包第三方光影源码或 Iris / NeoForge 依赖。
