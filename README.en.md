# RoadRailAlignment

<p align="center">
  <img src="src/main/resources/images/mapmode/roadrailalignment.svg" alt="RoadRailAlignment logo" width="96" height="96">
</p>

<p align="center">
  JOSM plugin for road, rail, ramp, and connector horizontal alignments
</p>

Chinese version: [README.md](README.md)

RoadRailAlignment creates horizontal alignments in JOSM from clicked control points. It writes native JOSM `Node` / `Way` objects and focuses on plan geometry only; it does not model vertical profiles, cross sections, superelevation, or construction drawings.

## Features

- Map mode and standalone parameter window
- Default `Basic smart alignment` mode for ordinary lines, ramp tie-ins, and existing-way connections
- Straight lines, PI circular curves, circular curves with transition spirals, large-sweep curves, and loops
- Single-end ramps, two-end existing-way connectors, and automatic radius/transition optimization for two-end connectors
- Continuous work: keep the generated endpoint, tangent, and ramp curvature when useful for the next segment
- Existing-node snapping and endpoint reuse
- Presets for common road, ramp, trunk road, railway, and high-speed rail tags
- Interface resources for Chinese, English, French, German, and Spanish

## Basic Mode

The default drawing mode is `Basic smart alignment`. It decides what to create from where you click:

1. First click on empty space: create a new start point. Because there is no source tangent, the first segment from that point is a straight line.
2. First click near an existing `Way`: use it as a ramp tie-in. A second click on empty space creates a single-end ramp.
3. First click near an existing `Way`, second click near another existing `Way`: create a tangent-continuous connector between the two ways.
4. With `Continuous work` enabled, the generated endpoint is kept as the next segment start.

The older explicit modes are still available:

- `Two-point straight line`
- `Three-point circular curve (with transitions)`
- `Large-sweep circular curve/loop`
- `Ramp from existing way`
- `Connect two existing ways`

## Ramp Curvature Continuity

With `Continuous work` and `Keep ramp curvature for next segment` enabled, a single-end ramp normally uses:

```text
0 curvature / previous curvature -> entry spiral -> curve -> exit spiral -> 0 curvature
```

If the next ramp bends in the same direction, the plugin updates the previously generated ramp after the next segment is successfully created:

```text
Previous segment: 0 curvature / previous curvature -> entry spiral -> curve
Next segment:     previous end curvature -> entry spiral -> curve -> exit spiral -> 0 curvature
```

This avoids forcing continuous same-direction ramps back to zero curvature between segments, which makes U-turn ramps possible. If the next ramp bends the other way, or the next segment is not a continuous ramp, the previous segment keeps its normal exit spiral back to zero.

## Parameters

- `Sample interval`: spacing between generated nodes; smaller values create denser output.
- `Curve/minimum radius`: radius constraint for curves and ramps.
- `Transition spiral length`: length of transition spirals when enabled.
- `Use transition spirals`: add spiral transition sections to curves and ramps.
- `Keep ramp curvature for next segment`: reuse signed end curvature for continuous same-direction ramps.
- `Continuous work`: keep the generated endpoint for the next segment.
- `Snap to existing nodes` / `Node snap tolerance`: prefer reusing nearby existing nodes.
- `Auto-optimize max R/transition for two-way connection`: estimate usable radius and transition length for two-end connectors.
- `Apply optimized parameters after generation`: write optimized values back to the panel.
- `Extra full turns`: add complete loops for large-sweep curves or two-end connectors.
- `Tie-in direction`: choose automatic, source-way, or reverse source-way tangent direction for two-end connectors.

## Shortcuts

- `Esc`: clear current control points and preview.
- `Backspace`: undo the last control point.
- `Ctrl+Z`: in the plugin mode, undo the last control point first.

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

Fallback build script:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\build.ps1
```

## Test

Run the geometry smoke tests:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\tools\run-geometry-smoke.ps1
```

You can also run the Maven test phase:

```powershell
mvn test
```

## Install

Copy `target\RoadRailAlignment.jar` into the JOSM plugin directory, then restart JOSM:

```text
%APPDATA%\JOSM\plugins
```

## Repository Layout

```text
src/main/java/       plugin source code
src/main/resources/  icons and language resources
src/test/java/       geometry smoke tests
tools/               download, build, and language helper scripts
pom.xml              Maven config
build.ps1            fallback build script
CHANGELOG.md         version history
```

## Notes

- Generated `Way`s only receive the selected object preset tags; no plugin marker tag is written.
- Existing-way tie-ins depend on the current editable data layer.
- Automatic optimization is a drawing helper, not a full engineering CAD solver.

## License

GPL-2.0-or-later
