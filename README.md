# RoadRailAlignment

<p align="center">
  <img src="src/main/resources/images/mapmode/roadrailalignment.svg" alt="RoadRailAlignment plugin logo" width="96" height="96">
</p>

<p align="center">
  JOSM 道路、铁路和互通匝道平面线形辅助绘制插件。
</p>

RoadRailAlignment 用于在 JOSM 中绘制道路、铁路和互通匝道中心线。当前版本是可试用 V1，只处理平面线形，不处理纵断面、横断面、超高或施工图，输出为 JOSM 原生 `Node` / `Way`。

仓库只提交源代码、资源、构建脚本和文档。`lib/josm-tested.jar`、`target/`、`build/`、`dist/` 都是本地构建产物，不进入 Git。

## 功能

- `道路/铁路线形` 地图模式按钮。
- 右侧参数面板，以及可浮动的独立参数窗口。
- 道路 / 铁路输出类型选择。
- 实时线形预览。
- 两点直线。
- 三点 PI 圆曲线，可选两端缓和曲线。
- 大角度圆曲线 / 回环，可绘制超过 180 度的弯道并支持附加整圈。
- 按当前半径推荐缓和曲线长度。
- 插件模式内点击既有 Way 附近即可识别并选中接入线。
- 从一条既有 Way 切线接出匝道。
- 从两条既有 Way 生成两端 G1 连续连接线。
- 两线连接可自动估算尽可能大的半径和缓和曲线长度，也可将结果回填到参数栏。
- 两线连接可按附加整圈数强制加入回环，并支持三段圆弧候选。
- 接入方向可按“自动朝目标 / 按所在线方向 / 反向所在线”生成连接线，适合灯泡线、回头线等场景。
- 单端匝道生成后可保留终点和末端切线，继续绘制后续线形。
- 接入点可吸附到既有 Way 线段或既有节点，生成 Way 时会复用被吸附的既有节点。
- 从既有线端头接出匝道时，第二点靠近端头延长线会自动做直线投影吸附，便于直接从线端续接直线。
- 支持 JOSM 撤销 / 重做；无编辑图层时会自动创建 OSM 数据图层。

完整版本记录见 [CHANGELOG.md](CHANGELOG.md)。

## 使用方式

1. 在 JOSM 中启用 `道路/铁路线形` 地图模式。
2. 在右侧面板或独立窗口中选择对象类型、绘制模式和参数。
3. 按需要设置 `曲线/最小半径`、`缓和曲线长度`、`采样间距`、节点吸附容差等。
4. 在地图上依次点击控制点，预览线形确认无误后，点满当前模式所需控制点即生成 JOSM Way。
5. 生成后可用 JOSM 自带撤销恢复，也可继续在插件模式内绘制下一段。

### 常用模式

- `两点直线`：点击起点和终点，生成普通直线 Way。起点吸附既有线时，第二点靠近该线方向会沿直线方向投影。
- `三点圆曲线（可带缓和）`：依次点击起点、PI 点和终点，按半径生成圆曲线；勾选 `使用缓和曲线` 后会加入两端缓和段。
- `大角度圆曲线/回环`：依次点击起点、起点切线方向点和终止方位点，用于大转角或回环线；`附加整圈数` 可强制增加完整回环。
- `从既有线接出匝道`：先在既有 Way 附近点击接入点，再点击匝道目标点。若接入点落在既有线首端或末端，目标点靠近端头切线延长线时会被吸附到直线上。
- `连接两条既有线`：分别在两条 Way 附近点击接入点，生成两端切线连续的连接线。默认会自动优化半径和缓和曲线长度。

### 推荐工作流

- 先用 `道路/铁路` 类型确定生成 Way 的标签。
- 绘制普通中心线时保持 `连续作业` 开启，上一段终点会自动成为下一段起点。
- 需要从既有线精确接出时，先点击既有 Way 附近；插件会自动吸附到最近线段并选中该 Way。
- 接入点在线端头时，把第二点点在线端延长方向附近即可生成直线接出；如果第二点偏离较大，则仍按普通匝道曲线生成。
- 生成节点需要复用既有节点时，开启 `吸附已有节点` 并设置合适的节点吸附容差。
- 两条既有线连接失败或半径过小时，可尝试增大 `曲线/最小半径`、开启自动优化，或调整 `接入方向`。

### 快捷操作

- `Esc`：清空当前控制点和预览。
- `Backspace`：撤回最后一个控制点。
- 地图获得焦点且已有控制点时，`Ctrl+Z` 优先撤回插件控制点。

## 构建

先下载 JOSM tested jar：

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\tools\download-josm.ps1
```

然后使用 Maven 构建插件：

```powershell
mvn package
```

构建结果：

```text
target\RoadRailAlignment.jar
```

`build.ps1` 保留为备用手动构建脚本，默认推荐使用 Maven。

## 测试

运行几何烟雾测试：

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\tools\run-geometry-smoke.ps1
```

测试覆盖：

- 直线端点。
- PI 圆曲线端点和实际转角。
- 带缓和曲线的 PI 圆曲线。
- 大角度圆曲线和附加整圈。
- 单端 / 双端匝道端点。
- 双端连接强制回环和接入方向。
- 复合匝道端点。
- 缓和曲线前进方向。

## 安装

将构建出的插件 jar 放入 JOSM 插件目录：

```text
%APPDATA%\JOSM\plugins
```

然后重启 JOSM。

## 仓库结构

```text
src/main/java/       插件源代码
src/main/resources/  JOSM 插件图标等资源
src/test/java/       几何烟雾测试
tools/               本地辅助脚本
.github/workflows/   GitHub Actions 构建流程
pom.xml              Maven 构建配置
build.ps1            备用手动构建脚本
plan.md              开发计划和后续路线
CHANGELOG.md         版本记录
```

## 发布前检查

- 确认 `pom.xml`、`CHANGELOG.md` 和发布 tag 使用同一个版本号。
- 运行 `powershell.exe -ExecutionPolicy Bypass -File .\tools\run-geometry-smoke.ps1`。
- 运行 `mvn package`，确认生成 `target\RoadRailAlignment.jar`。
- 确认插件图标路径为 `images/mapmode/roadrailalignment.svg`，并已写入 `pom.xml` 的 `Plugin-Icon`。
- 许可证已声明为 `GPL-2.0-or-later`；发布前建议添加完整 `LICENSE` 文件。

## 限制

- 只做平面线形，不做纵断面、横断面、超高和施工图。
- 双端匝道使用 Hermite G1 曲线与缓和过渡段组合，不是完整工程 CAD 的全局优化求解。
- 缓和曲线为 clothoid 风格数值近似片段，用于编辑辅助。
- 既有 OSM Way 通常是折线，局部半径只能估算。

## License

RoadRailAlignment 使用 GNU General Public License v2.0 or later 授权。

SPDX 标识：

```text
GPL-2.0-or-later
```

这意味着你可以使用、修改、分发和商用本插件；如果分发修改版或 jar 包，需要同时提供对应源代码，并保留 GPL 许可证和版权声明。用户通过插件生成的 OSM `Node` / `Way`、地图数据或工程数据不受本插件 GPL 许可证约束。
