# Cloud Height Compat

让光影中的体积云适应扩高世界。

Cloud Height Compat 是 **Minecraft 1.21.1 / NeoForge** 的客户端模组。当世界生成模组或数据包提高世界高度后，光影的云层可能仍停留在原来的位置，穿过山体或高空建筑。本模组根据实际维度高度自动抬升云层，也支持在 Sodium 设置中拖动滑条指定高度。

目前针对 **BSL 10.1.1 和 10.1.8** 适配体积云、云影和水面反射中的体积云。其他光影的支持范围见下方兼容说明。

**[下载最新版本](https://github.com/HosisoraLing/cloud-height-compat/releases/latest)** · **[报告问题](https://github.com/HosisoraLing/cloud-height-compat/issues)**

## 运行环境

| 组件 | 要求 |
| --- | --- |
| Minecraft | 1.21.1 |
| 模组加载器 | NeoForge 21.1.x |
| 光影加载器 | Iris 1.8.x |
| 渲染模组与设置菜单 | Sodium 0.8.x |
| 安装位置 | 仅客户端 |

无需在服务器安装，也无需额外的设置界面模组。

## 安装

1. 安装上述版本的 NeoForge、Iris 和 Sodium。
2. 从 [Releases](https://github.com/HosisoraLing/cloud-height-compat/releases) 下载模组 JAR，放入游戏实例的 `mods` 文件夹。升级时替换旧 JAR，保留配置文件。
3. 在 Iris 中启用 BSL。若要使用体积云，请在 BSL 的光影选项中选择 **Clouds = Volumetric**。

首次启动会生成 `config/cloud-height-compat-client.toml`。默认启用自动高度修正，可直接用于已有世界；无需修改光影压缩包或重新生成存档。

## 自动高度

默认情况下，模组读取当前维度的世界高度上限：

- **上限不超过 Y=320：**保留光影原来的云高度。
- **上限超过 Y=320：**云底至少位于高度上限上方 64 格；光影原本设置得更高时，保留较高值。

例如，世界高度上限为 Y=768 时，自动修正后的云底至少为 **Y=832**。这是一项随维度变化的规则，手动滑条中的默认值 832 仅在开启手动模式后使用。

云层移动时保持原有厚度，并同步修正已开启的云影。模组不扫描地形；自动高度依据维度的建造高度上限，而非附近山峰的高度。希望云层位于山腰时，可使用手动模式。

高度修正适用于具有天空、没有天花板且提供原版云高度的维度。无云维度不启用修正。

## 手动调整

进入 **选项 → 视频设置 → Cloud Height Compat → 体积云高度**：

1. 开启“手动设置云高度”。
2. 拖动“体积云高度”滑条，设置云底的 Y 坐标。
3. 点击 Sodium 的“应用”保存并生效。

滑条范围为 **-512～8192**，默认 **Y=832**。选中滑条后，可用左右方向键每次微调 1 格。应用后无需重载光影；未应用的修改可通过 Sodium 的还原操作撤销。

手动模式默认关闭。关闭手动模式会恢复配置中的自动高度或偏移规则；恢复默认值会关闭手动模式，并将滑条值设回 832。

## 按维度配置

需要让不同维度使用不同高度时，编辑 `config/cloud-height-compat-client.toml`：

```toml
dimensionOverrides = ["minecraft:overworld=600", "example:sky=1200"]
```

此例将主世界的云底设为 Y=600，将 `example:sky` 维度的云底设为 Y=1200。维度 ID 必须替换为实际使用的 ID。

高度设置的优先级为 **维度专属高度 → 手动高度 → 自动高度与偏移**。当前维度存在专属高度时，菜单滑条不会覆盖该值；移除对应项即可使用滑条。

| 配置项 | 默认值 | 用途 |
| --- | --- | --- |
| `enabled` | `true` | 开关全部高度修正 |
| `automatic` | `true` | 在扩高维度中自动抬升云层 |
| `clearance` | `64` | 自动模式下，云底高于世界上限的距离 |
| `extraOffset` | `0.0` | 在自动计算或原光影高度上追加偏移，可为负数 |
| `manualHeightEnabled` | `false` | 使用全局手动高度 |
| `manualHeight` | `832` | 手动模式下的云底 Y 坐标 |
| `dimensionOverrides` | `[]` | 按维度指定绝对云底高度 |

手动高度和维度专属高度不叠加 `extraOffset`。关闭 `automatic` 和手动模式时，可以只使用额外偏移。

## 兼容范围

**世界高度模组与数据包：**读取游戏同步的实际维度高度，不依赖某个模组的名称或配置格式。只向地下扩展、没有提高世界上限的改动，不会触发自动抬云。

**BSL：**已针对 10.1.1 和 10.1.8 的体积云模式适配自动高度、固定高度和云影。BSL 的 Skybox 云模式不在此适配范围内。

**其他光影：**使用 Iris `cloudHeight` 作为云层高度时，可获得修正。自行写死高度或使用独立云层参数的光影，需要单独适配；本模组不保证所有光影包通用。

**版本范围：**当前面向 NeoForge 1.21.1、Iris 1.8.x 和 Sodium 0.8.x。没有 Fabric 或 OptiFine 版本。

构建与离线检查使用 NeoForge 21.1.249、Iris 1.8.14-beta.1 和 Sodium 0.8.12，覆盖高度策略、BSL 源码预处理、Sodium 选项注册及配置保存。详细结果与未覆盖项目见 [验证记录](verification.md)。

## 报告问题

提交 [Issue](https://github.com/HosisoraLing/cloud-height-compat/issues) 时，请附上 Minecraft、NeoForge、Iris、Sodium、本模组和光影包的版本，以及当前维度 ID、世界高度设置和相关配置。涉及画面或崩溃时，请同时提供截图或日志。

## 从源码构建

需要 **JDK 21、Python 3.10+** 和完整安装的 NeoForge 1.21.1 客户端。构建脚本使用客户端安装中的编译依赖，不会启动游戏。

```powershell
python build.py --minecraft 'C:/Games/Minecraft' --iris 'C:/Games/Minecraft/mods/iris-neoforge-1.8.14-beta.1+mc1.21.1.jar' --sodium 'C:/Games/Minecraft/mods/sodium-neoforge-0.8.12+mc1.21.1.jar'
```

替换为实际安装路径。`--minecraft` 指向包含 `libraries` 的目录；JDK 不在 PATH 时，可添加 `--jdk 'JDK 21 的安装目录'`。产物位于 `build/`。

运行离线检查时，追加 `--test-shader 'BSL 压缩包路径'`，可重复指定多个光影版本。测试会使用独立的构建目录，不会写入游戏实例。

## 许可

本项目采用 **AGPL-3.0-only** 许可，完整条款见 [LICENSE](LICENSE)。仓库和发布包不包含第三方光影文件或 Minecraft、NeoForge、Iris、Sodium 的二进制依赖。
