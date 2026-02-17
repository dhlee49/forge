package forge.gamemodes.net.server;

import forge.game.GameType;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;

import java.util.Collections;

public class CommanderServerGameLobby extends ServerGameLobby {
    private Runnable gameStartedCallback;

    public CommanderServerGameLobby() {
        super(); // creates LOCAL + 1 OPEN
        // Add 2 more OPEN slots for 4 total players
        addSlot(new LobbySlot(LobbySlotType.OPEN, null, -1, -1, 2, false, false, Collections.emptySet()));
        addSlot(new LobbySlot(LobbySlotType.OPEN, null, -1, -1, 3, false, false, Collections.emptySet()));
        applyVariant(GameType.Commander);
    }

    public void setGameStartedCallback(final Runnable callback) {
        this.gameStartedCallback = callback;
    }

    @Override
    protected void onGameStarted() {
        if (gameStartedCallback != null) {
            gameStartedCallback.run();
        }
    }
}
