# Commander Multi-Window Board System

## Overview

This package implements a multiplayer Commander board for the Forge desktop client.
It provides two features:

1. **2x2 Grid Board** -- The main Forge window displays all 4 player battlefields
   in a 2x2 grid layout (instead of the default 2-player stacked layout).
2. **Per-Player Popup Windows** -- When 2+ LOCAL human players are configured,
   each human gets their own private popup window showing their hand, prompts,
   and interactive buttons. AI players never get popup windows.

```
 Main Forge Window (shared board)     Player Popups (private per human)
+-------------------+                 +----------------------------+
| FIELD_1 | FIELD_3 |                 | Commander - Player 1       |
| (opp 1) | (opp 3) |                 | Life: 40  Phase: Main1     |
+---------+---------+                 | [My Hand: card1, card2...] |
| FIELD_0 | FIELD_2 |                 | [OK] [Cancel]              |
| (you)   | (opp 2) |                 +----------------------------+
+---------+---------+
| HAND_0            |
+-------------------+
```

## Files in This Package

### `CommanderLobby.java`
- **Extends**: `LocalLobby` (which extends `GameLobby`)
- **Purpose**: Overrides the lobby to enable multi-window mode for Commander.
- **Key logic**:
  - `getGui(index)` -- When 2+ LOCAL human players exist, returns a
    `CommanderPlayerWindow` for each human slot. AI slots fall through to
    the default single GUI.
  - `onGameStarted()` -- In multi-window mode, creates a spectator `CMatchUI`
    for the main Forge window so it transitions to the Commander board.
    Uses `CommanderSpectatorController` to forward clicks from the main board
    to the active player's controller.
  - `isMultiPlayerLocal()` -- Counts LOCAL slots; returns true for 2-4 humans.

### `CommanderPlayerWindow.java`
- **Extends**: `AbstractGuiGame` (implements `IGuiGame`)
- **Purpose**: A standalone JFrame popup window for one human player.
- **What it shows**:
  - Header: player name, life total, phase, turn, mana pool
  - Opponents summary: life, poison, battlefield card count
  - My Battlefield: cards the player controls (full-size, clickable)
  - My Hand: private hand cards (only this player can see them)
  - Card Detail: enlarged card preview on hover
  - Prompt area: game prompts, OK/Cancel buttons
  - Stack and Combat info
- **Interaction**: Cards are clickable and route to the game controller
  (`gc.selectCard(...)`, `gc.selectPlayer(...)`). Prompts and buttons
  are fully functional for that player's turn.
- **Concede**: In multiplayer, conceding only eliminates THIS player
  (doesn't end the entire match for everyone).

### `CommanderSpectatorController.java`
- **Extends**: `WatchLocalGame` (which extends `PlayerControllerHuman`)
- **Purpose**: Controller for the main board's spectator view.
- **Key logic**: Unlike a normal spectator that ignores clicks, this
  controller **forwards** interactive calls (`selectPlayer`, `selectCard`,
  `selectAbility`, `selectButtonOk/Cancel`) to whichever human player
  controller currently has an active input waiting.
  - `findActiveController()` -- Loops through human controllers to find
    one with a non-null input in its input queue.

## Files Modified Outside This Package

### `VMatchUI.java` (`forge/screens/match/`)
- **Change**: `populate()` method -- When `lstFields.size() == 4`, arranges
  battlefields in a 2x2 grid instead of adding extra fields as tabs.
- **How**:
  1. Shrinks the left side panels (stack/log, message/dock) from 20% to 10% width
  2. Shrinks the right side panels (card detail, card picture) from 20% to 10% width
  3. This expands the field area from 60% to 80% of window width
  4. Splits the expanded area into 4 equal quadrants (40% width x ~36.6% height each)
  5. Creates new `DragCell`s for FIELD_2 and FIELD_3
  6. Widens the hand panel to match the new grid width
  7. Calls `SResizingUtil.resizeWindow()` to apply the layout

### `TargetingOverlay.java` (`forge/screens/match/`)
- **Change**: `assembleArcs()` method -- Added null guard for
  `CDock.getArcState()`. During layout initialization, `arcState` hasn't been
  set yet (it's set in `CDock.initialize()` which runs after layout loading).
  Without the guard, any layout operation that triggers a repaint causes NPE.

### `VSubmenuConstructed.java` (`forge/screens/home/sanctioned/`)
- **Change**: Replaced `new LocalLobby()` with `new CommanderLobby()` so the
  Constructed game mode uses the Commander lobby (which falls back to normal
  behavior when < 2 LOCAL humans).

### `GameLobby.java` (`forge/gamemodes/match/`)
- **Change**: Added `protected getHostedMatch()` accessor so `CommanderLobby`
  can access the `hostedMatch` field (which is private in `GameLobby`).

### `LocalLobby.java` (`forge/gamemodes/match/`)
- **Change**: Removed `final` from class declaration so `CommanderLobby` can
  extend it.

## Configuration

To enable popup windows, set up the lobby with **2+ LOCAL players**:

| Slot | Type  | Result                          |
|------|-------|---------------------------------|
| 0    | LOCAL | Gets a CommanderPlayerWindow    |
| 1    | LOCAL | Gets a CommanderPlayerWindow    |
| 2    | AI    | No popup (AI controlled)        |
| 3    | AI    | No popup (AI controlled)        |

The main Forge window always shows the 2x2 Commander board regardless
of how many players are human vs AI (as long as there are 4 players total).

## Flow Diagram

```
User clicks [Start Game] in Lobby
        |
        v
GameLobby.startGame() returns a Runnable
        |
        v
Runnable runs:
  1. HostedMatch created
  2. For each slot: CommanderLobby.getGui(index)
     - LOCAL slot -> CommanderPlayerWindow (stored in guis map)
     - AI slot    -> default CMatchUI (discarded, AI has no GUI)
  3. HostedMatch.startMatch() -> startGame()
     a. For each human player:
        - Associates CommanderPlayerWindow as their GUI
        - Calls CommanderPlayerWindow.openView() -> JFrame becomes visible
     b. Game thread starts in background
  4. CommanderLobby.onGameStarted()
     a. Creates spectator CMatchUI (new instance)
     b. Calls HostedMatch.registerSpectator()
        - CMatchUI.openView(null) is called
        - Main Forge window transitions to match screen
        - SLayoutIO loads match.xml layout
        - VMatchUI.populate() creates the 2x2 grid
  5. Game is now running:
     - Main window: shared Commander board (spectator)
     - Per-human popups: private hand + interaction
```
