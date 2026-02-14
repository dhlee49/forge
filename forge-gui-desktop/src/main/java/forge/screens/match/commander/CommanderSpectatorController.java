package forge.screens.match.commander;

import forge.game.Game;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.gui.control.WatchLocalGame;
import forge.gui.interfaces.IGuiGame;
import forge.player.PlayerControllerHuman;
import forge.util.ITriggerEvent;

import java.util.List;

/**
 * A spectator controller for the main board in Commander multi-window mode.
 * Unlike {@link WatchLocalGame} which silently ignores all clicks, this
 * controller forwards interactive calls (selectPlayer, selectCard, etc.)
 * to whichever player controller currently has an active Input waiting.
 */
public class CommanderSpectatorController extends WatchLocalGame {

    private List<PlayerControllerHuman> playerControllers;

    public CommanderSpectatorController(final Game game, final IGuiGame gui) {
        super(game, null, gui);
    }

    public void setPlayerControllers(final List<PlayerControllerHuman> controllers) {
        this.playerControllers = controllers;
    }

    /**
     * Find the player controller that currently has an active Input
     * (i.e. is waiting for user interaction).
     */
    private PlayerControllerHuman findActiveController() {
        if (playerControllers == null) return null;
        for (final PlayerControllerHuman c : playerControllers) {
            if (c == this) continue; // skip self (spectator)
            if (c.getInputQueue() != null && c.getInputQueue().getInput() != null) {
                return c;
            }
        }
        return null;
    }

    @Override
    public void selectPlayer(final PlayerView player, final ITriggerEvent triggerEvent) {
        final PlayerControllerHuman active = findActiveController();
        if (active != null) {
            active.selectPlayer(player, triggerEvent);
        }
    }

    @Override
    public boolean selectCard(final CardView card,
            final List<CardView> otherCardViewsToSelect,
            final ITriggerEvent triggerEvent) {
        final PlayerControllerHuman active = findActiveController();
        if (active != null) {
            return active.selectCard(card, otherCardViewsToSelect, triggerEvent);
        }
        return false;
    }

    @Override
    public void selectAbility(final SpellAbilityView sa) {
        final PlayerControllerHuman active = findActiveController();
        if (active != null) {
            active.selectAbility(sa);
        }
    }

    @Override
    public void selectButtonOk() {
        final PlayerControllerHuman active = findActiveController();
        if (active != null) {
            active.selectButtonOk();
            return;
        }
        super.selectButtonOk();
    }

    @Override
    public void selectButtonCancel() {
        final PlayerControllerHuman active = findActiveController();
        if (active != null) {
            active.selectButtonCancel();
            return;
        }
        super.selectButtonCancel();
    }
}
