# Match Screen (`forge/screens/match/`)

## Overview

This package contains the desktop match UI for Forge -- everything you see
during an active game of Magic: The Gathering.

## Key Files

| File | Role |
|------|------|
| `CMatchUI.java` | **Main controller** for the match screen. Implements `IGuiGame`. Manages all panels, handles game events, routes player interactions. One instance per game tab. |
| `VMatchUI.java` | **Top-level view**. Owns the drag-layout structure. `populate()` arranges field panels -- including the **2x2 Commander grid** for 4-player games. |
| `TargetingOverlay.java` | Draws targeting arcs (attack/block/spell target lines) on top of the match. Paints on a transparent overlay layer. |
| `GameLogPanel.java` | Scrollable game log display. |
| `ViewWinLose.java` / `ControlWinLose.java` | Win/Lose screen shown after a game ends. |

## Subdirectories

### `commander/`
Commander multi-window board system. Creates a 2x2 grid of battlefields in the
main window and optional per-player popup windows for private hand views.
**See [`commander/INSTRUCTIONS.md`](commander/INSTRUCTIONS.md) for full details.**

### `views/`
View classes (V-prefix) for individual panels in the match screen:
- `VField.java` -- One player's battlefield (cards in play)
- `VHand.java` -- One player's hand
- `VStack.java` -- The spell stack
- `VPrompt.java` -- Action prompt with OK/Cancel buttons
- `VDock.java` -- Dock panel (concede, settings, targeting toggle)
- `VDetail.java` -- Card detail text panel
- `VPicture.java` -- Card picture display
- `VLog.java` -- Game log panel
- `VCombat.java` -- Combat info panel
- `VDev.java` -- Developer mode panel
- `VAntes.java` -- Ante cards panel
- `VDependencies.java` -- Card dependencies display

### `controllers/`
Controller classes (C-prefix) paired with the views above:
- `CField.java` / `CHand.java` / `CStack.java` / `CPrompt.java`
- `CDock.java` -- Manages targeting arc state (`ArcState`: OFF / MOUSEOVER / ON)
- `CDetail.java` / `CPicture.java` / `CDetailPicture.java`
- `CLog.java` / `CCombat.java` / `CDev.java` / `CAntes.java` / `CDependencies.java`

### `menus/`
Menu bar items for the match screen:
- `CMatchUIMenus.java` -- Builds the full menu bar
- `GameMenu.java` -- Game menu (concede, restart, etc.)
- `DevModeMenu.java` -- Developer cheats menu
- `CardOverlaysMenu.java` -- Card overlay display options

## Layout System

The match screen uses a **drag-layout** framework (`forge/gui/framework/`).
The layout is defined in XML (`forge-gui/res/defaults/match.xml`) with cells
positioned using percentage-based coordinates (0.0 to 1.0):

```xml
<layout>
  <cell x="0.2" y="0.0" w="0.6" h="0.364">
    <doc>FIELD_1</doc>   <!-- opponent battlefield -->
  </cell>
  <cell x="0.2" y="0.364" w="0.6" h="0.368">
    <doc>FIELD_0</doc>   <!-- your battlefield -->
  </cell>
  <cell x="0.2" y="0.732" w="0.6" h="0.268">
    <doc>HAND_0</doc>    <!-- your hand -->
  </cell>
  <!-- ... other panels ... -->
</layout>
```

For 4-player Commander, `VMatchUI.populate()` dynamically rearranges
FIELD_0 and FIELD_1 into a 2x2 grid and creates new cells for FIELD_2
and FIELD_3. See `commander/INSTRUCTIONS.md` for details.
