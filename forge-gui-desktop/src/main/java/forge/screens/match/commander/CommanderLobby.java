package forge.screens.match.commander;

import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.match.LocalLobby;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;

import java.util.HashMap;
import java.util.Map;

/**
 * A lobby for Commander games (3-4 players) that creates a separate
 * player window ({@link CommanderPlayerWindow}) for each LOCAL human player.
 * <p>
 * In a 3-4 player game with at least 1 LOCAL human, each human gets their
 * own {@link CommanderPlayerWindow} popup showing their private hand, while
 * the main Forge window displays a shared Commander board (2x2 grid).
 * <p>
 * AI players never get popup windows.
 * For 2-player games, this behaves identically to {@link LocalLobby}.
 */
public class CommanderLobby extends LocalLobby {

    private final Map<Integer, IGuiGame> playerGuis = new HashMap<>();
    private boolean multiWindowMode = false;

    @Override
    protected IGuiGame getGui(final int index) {
        if (isCommanderSized()) {
            // Only create popup windows for LOCAL (human) players, not AI
            final LobbySlot slot = getSlot(index);
            if (slot != null && slot.getType() == LobbySlotType.LOCAL) {
                multiWindowMode = true;
                return playerGuis.computeIfAbsent(index,
                        i -> new CommanderPlayerWindow(i));
            }
            // AI slots: fall through to default single GUI
        }
        return super.getGui(index);
    }

    @Override
    protected void onGameStarted() {
        if (!multiWindowMode) {
            // Normal behavior: clear GUI reference and re-randomize
            super.onGameStarted();
            return;
        }

        // In multi-window mode, register a spectator CMatchUI for the main
        // Forge window so it transitions from the lobby to the game board.
        // Use a custom spectator controller that forwards interactive clicks
        // (selectPlayer, selectCard, etc.) to the active player's controller.
        final HostedMatch match = getHostedMatch();
        if (match != null) {
            final IGuiGame spectatorGui = GuiBase.getInterface().getNewGuiGame();
            spectatorGui.setGameView(null);
            spectatorGui.setGameView(match.getGameView());

            final CommanderSpectatorController spectator =
                    new CommanderSpectatorController(match.getGame(), spectatorGui);
            spectator.setPlayerControllers(match.getHumanControllers());
            match.registerSpectator(spectatorGui, spectator);
        }
    }

    /**
     * Check if this is a Commander-sized game (3-4 total players) with
     * at least 1 LOCAL human player. Each human gets their own popup
     * window for private hand view, regardless of how many are human vs AI.
     */
    private boolean isCommanderSized() {
        int totalSlots = getNumberOfSlots();
        if (totalSlots < 3 || totalSlots > 4) {
            return false;
        }
        for (int i = 0; i < totalSlots; i++) {
            final LobbySlot slot = getSlot(i);
            if (slot != null && slot.getType() == LobbySlotType.LOCAL) {
                return true; // at least 1 human in a 3-4 player game
            }
        }
        return false;
    }
}
