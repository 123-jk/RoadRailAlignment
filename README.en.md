# RoadRailAlignment

<p align="center">
  <img src="src/main/resources/images/mapmode/roadrailalignment.svg" alt="RoadRailAlignment logo" width="96" height="96">
</p>

<p align="center">
  JOSM plugin for horizontal road, rail, and ramp alignments
</p>

中文版本: [README.md](README.md)

RoadRailAlignment generates horizontal alignments in JOSM from control points. It is suited for straight segments, circular curves, transition curves, and ramps or connectors tied into existing `Way`s.

## What it does

- Map mode and standalone parameter window
- Object presets for common road, ramp, rail, and high-speed rail tags (`highway=road`, `highway=motorway`, `highway=motorway_link`, `highway=trunk`, `highway=primary`, `railway=rail`, `railway=rail` + `highspeed=yes`)
- Real-time preview
- Auto-optimized radius and transition length for two-end ramps
- I18N resources for Chinese, English, French, German, and Spanish
- Continuous work, undo, and clear
- Snap to existing nodes

## How to use

1. Enable `Road/Rail Alignment` in JOSM.
2. Pick the object type and drawing mode.
3. Set `Sample interval`, `Curve/minimum radius`, `Transition spiral length`, and other parameters as needed.
4. Click control points on the map and watch the preview.
5. Confirm the shape to create a `Way`, then continue with the next segment if needed.

### Common modes

- `Two-point straight line`
- `Three-point circular curve (with transitions)`
- `Large-sweep circular curve/loop`
- `Ramp from existing way`
- `Connect two existing ways`

### Shortcuts

- `Esc`: clear current control points
- `Backspace`: undo the last step
- `Ctrl+Z`: undo the last step

## Parameters

- `Sample interval`: controls point density; smaller values give denser output
- `Curve/minimum radius`: target radius for curves and ramps
- `Transition spiral length`: transition length used when spirals are enabled
- `Use transition spirals`: add transition spirals to curves and ramps
- `Recommend by R`: suggest a transition length from the radius
- `Continuous work`: keep the previous end point for the next segment
- `Snap to existing nodes`: prefer reusing existing nodes
- `Node snap tolerance`: node snapping tolerance
- `Auto-optimize max R/transition for two-way connection`: estimate radius and transition length automatically
- `Apply optimized parameters after generation`: write the optimized values back to the panel
- `Extra full turns`: add extra full loops for large-sweep curves or two-end connections
- `Tie-in direction`: control the tie-in direction when connecting two existing ways (`auto`, `along source way`, `reverse source way`)

## Install

Copy the built `RoadRailAlignment.jar` into the JOSM plugin directory, then restart JOSM:

```text
%APPDATA%\JOSM\plugins
```

## Build

Requirements:

- JDK 11+
- Maven
- `lib/josm-tested.jar`

Download the JOSM tested jar:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\tools\download-josm.ps1
```

Build the plugin:

```powershell
mvn package
```

Output:

```text
target\RoadRailAlignment.jar
```

Fallback script:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\build.ps1
```

## Test

Run the geometry smoke tests:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\tools\run-geometry-smoke.ps1
```

## Repository layout

```text
src/main/java/       core code
src/main/resources/  icons and I18N resources
src/test/java/       geometry smoke tests
tools/               helper scripts
pom.xml              Maven config
build.ps1            fallback build script
CHANGELOG.md         version history
```

## Scope

- Horizontal alignment only; no vertical profile, cross section, or construction drawings
- Existing-way tie-ins depend on the current editable data layer
- Automatic optimization is a helper, not a full CAD solver

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

GPL-2.0-or-later
