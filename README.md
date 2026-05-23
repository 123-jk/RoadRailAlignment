# RoadRailAlignment

<p align="center">
  <img src="src/main/resources/images/mapmode/roadrailalignment.svg" alt="RoadRailAlignment logo" width="96" height="96">
</p>

<p align="center">
  JOSM 道路、铁路与匝道平面线形辅助插件
</p>

English version: [README.en.md](README.en.md)

RoadRailAlignment 用于在 JOSM 中按控制点生成平面线形。它适合绘制直线、圆曲线、带缓和曲线的线路，以及从既有 `Way` 接出的匝道或连接线。

## 能做什么

- 地图模式和独立参数窗口
- 对象类型预设：道路、匝道、干线、铁路、高铁常用标签（`highway=road`、`highway=motorway`、`highway=motorway_link`、`highway=trunk`、`highway=primary`、`railway=rail`、`railway=rail` + `highspeed=yes`）
- 实时预览
- 两端匝道可自动优化半径和过渡段
- 界面已做 I18N，带中、英、法、德、西语资源
- 连续作业、撤销、清空
- 可吸附已有节点

## 怎么用

1. 在 JOSM 中启用 `Road/Rail Alignment` 地图模式。
2. 选择对象类型和绘制模式。
3. 按需要设置 `采样间距`、`曲线/最小半径`、`缓和曲线长度` 等参数。
4. 在地图上依次点击控制点，右侧会显示预览。
5. 确认后生成 `Way`，然后可以继续下一段。

### 常用模式

- `两点直线`
- `三点圆曲线（含缓和曲线）`
- `大转角圆曲线 / 回环`
- `从既有线接出匝道`
- `连接两条既有线`

### 快捷键

- `Esc`：清空当前控制点
- `Backspace`：撤销上一步
- `Ctrl+Z`：撤销上一步

## 参数

- `采样间距`：控制采样点密度，数值越小越密
- `曲线/最小半径`：曲线和匝道的目标半径
- `缓和曲线长度`：启用缓和曲线时的过渡段长度
- `使用缓和曲线`：给曲线和匝道加过渡螺旋
- `按半径推荐缓和曲线长度`：按半径推荐过渡长度
- `连续作业`：保留上一段终点，方便接着画
- `吸附已有节点`：优先复用已有节点
- `节点吸附容差`：节点吸附容差
- `两端连接自动优化半径/缓和段`：自动估算两端连接的半径和过渡段
- `生成后回填优化参数`：把自动优化结果回填到参数栏
- `附加整圈数`：给大转角曲线或两端连接增加整圈数
- `接入方向`：连接两条既有 `Way` 时控制接入方向（自动 / 沿源线 / 反向源线）

## 安装

把构建出的 `RoadRailAlignment.jar` 放到 JOSM 插件目录，然后重启 JOSM：

```text
%APPDATA%\JOSM\plugins
```

## 构建

前提：

- JDK 11+
- Maven
- `lib/josm-tested.jar`

先下载 JOSM tested jar：

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\tools\download-josm.ps1
```

然后构建：

```powershell
mvn package
```

产物：

```text
target\RoadRailAlignment.jar
```

备用脚本：

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\build.ps1
```

## 测试

运行几何烟雾测试：

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\tools\run-geometry-smoke.ps1
```

## 仓库结构

```text
src/main/java/       核心代码
src/main/resources/  图标和多语言资源
src/test/java/       几何烟雾测试
tools/               辅助脚本
pom.xml              Maven 配置
build.ps1            备用构建脚本
CHANGELOG.md         版本记录
```

## 范围

- 只做平面线形，不做纵断面、横断面或施工图
- 既有 `Way` 接入依赖当前可编辑数据层
- 自动优化是辅助功能，不是完整 CAD 求解器

## 版本记录

见 [CHANGELOG.md](CHANGELOG.md)。

## 许可

GPL-2.0-or-later
