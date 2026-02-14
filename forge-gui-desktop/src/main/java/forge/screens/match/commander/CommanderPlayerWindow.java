package forge.screens.match.commander;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import forge.LobbyPlayer;
import forge.ai.GameState;
import forge.deck.CardPool;
import forge.game.GameEntityView;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.combat.CombatView;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.IHasIcon;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.AbstractGuiGame;
import forge.gui.FThreads;
import forge.gui.GuiChoose;
import forge.gui.GuiDialog;
import forge.gui.util.SOptionPane;
import forge.interfaces.IGameController;
import forge.item.PaperCard;
import forge.localinstance.skin.FSkinProp;
import forge.player.PlayerZoneUpdate;
import forge.player.PlayerZoneUpdates;
import forge.toolbox.FOptionPane;
import forge.toolbox.FSkin;
import forge.toolbox.MouseTriggerEvent;
import forge.toolbox.imaging.FImageUtil;
import forge.trackable.TrackableCollection;
import forge.util.FSerializableFunction;
import forge.util.ITriggerEvent;
import forge.util.Localizer;
import forge.util.collect.FCollectionView;

/**
 * A standalone JFrame-based IGuiGame implementation for one player in a
 * multiplayer Commander game. Each player gets their own window showing
 * their hand, all battlefields, prompts, and interactive buttons.
 */
public class CommanderPlayerWindow extends AbstractGuiGame {

    private static final int CARD_WIDTH = 160;
    private static final int CARD_HEIGHT = 224;
    private static final int CARD_SMALL_WIDTH = 80;
    private static final int CARD_SMALL_HEIGHT = 112;
    private static final Color ACTIVE_BORDER = new Color(0, 180, 0);
    private static final Color SELECTABLE_BORDER = new Color(0, 200, 255);
    private static final Color HIGHLIGHT_BG = new Color(255, 255, 200);

    private final int playerIndex;
    private final JFrame frame;

    // All-players bar (replaces separate header + opponents section)
    private final JPanel pnlAllPlayers = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    // Game-info strip below players (phase, turn, mana)
    private final JLabel lblPhase = new JLabel("Phase: ---");
    private final JLabel lblTurn = new JLabel("Turn: ---");
    private final JLabel lblMana = new JLabel("Mana: ---");

    // Battlefield
    private final JPanel pnlMyBattlefield = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    private final JScrollPane scrMyBattlefield;

    // Hand
    private final JPanel pnlMyHand = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    private final JScrollPane scrMyHand;

    // Card detail overlay (semi-transparent, covers entire window)
    private Image overlayCardImage = null;
    private String overlayCardText = null;
    private final JPanel cardOverlayPane = new JPanel() {
        @Override
        protected void paintComponent(final Graphics g) {
            if (overlayCardImage == null && overlayCardText == null) return;
            final Graphics2D g2 = (Graphics2D) g.create();
            // Semi-transparent dark background
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (overlayCardImage != null) {
                // Draw card image centered, large (scaled to ~40% of window height)
                final int imgH = Math.min((int) (getHeight() * 0.6), 500);
                final int imgW = (int) (imgH * (250.0 / 350.0)); // standard card aspect
                final int x = (getWidth() - imgW) / 2;
                final int y = (getHeight() - imgH) / 2;
                g2.drawImage(overlayCardImage, x, y, imgW, imgH, null);
            } else if (overlayCardText != null) {
                // Fallback: draw card name as text
                g2.setColor(Color.WHITE);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 18f));
                final FontMetrics fm = g2.getFontMetrics();
                final int tx = (getWidth() - fm.stringWidth(overlayCardText)) / 2;
                final int ty = getHeight() / 2;
                g2.drawString(overlayCardText, tx, ty);
            }
            g2.dispose();
        }

        @Override
        public boolean contains(final int x, final int y) {
            return false; // Never consume mouse events — purely visual overlay
        }
    };

    // Prompt area
    private final JLabel lblPrompt = new JLabel("Waiting...");
    private final JButton btnOk = new JButton("OK");
    private final JButton btnCancel = new JButton("Cancel");

    // Stack display
    private final JLabel lblStack = new JLabel("Stack: empty");

    // Combat display
    private final JLabel lblCombat = new JLabel("");

    // State
    private FCollectionView<PlayerView> sortedPlayers;
    private PlayerView myPlayer;
    private final Map<Integer, CardLabel> cardPanelMap = new LinkedHashMap<>();
    private boolean windowOpen = false;

    public CommanderPlayerWindow(final int playerIndex) {
        this.playerIndex = playerIndex;

        frame = new JFrame("Commander - Player " + (playerIndex + 1));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                if (concede()) {
                    frame.dispose();
                }
            }
        });

        scrMyBattlefield = new JScrollPane(pnlMyBattlefield);
        scrMyBattlefield.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrMyBattlefield.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrMyBattlefield.setBorder(new TitledBorder("My Battlefield"));

        scrMyHand = new JScrollPane(pnlMyHand);
        scrMyHand.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrMyHand.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrMyHand.setBorder(new TitledBorder("My Hand"));

        buildUI();

        btnOk.addActionListener(e -> {
            final IGameController gc = getGameController();
            if (gc != null) { gc.selectButtonOk(); }
        });
        btnCancel.addActionListener(e -> {
            final IGameController gc = getGameController();
            if (gc != null) { gc.selectButtonCancel(); }
        });

        btnOk.setEnabled(false);
        btnCancel.setEnabled(false);

        frame.setSize(1024, 768);
        frame.setLocationByPlatform(true);
    }

    private void buildUI() {
        final JPanel root = new JPanel(new BorderLayout(5, 5));
        root.setBorder(new EmptyBorder(5, 5, 5, 5));

        // --- Top: unified players bar + game-info strip ---
        final JPanel pnlTop = new JPanel();
        pnlTop.setLayout(new BoxLayout(pnlTop, BoxLayout.Y_AXIS));

        // All-players row (self + opponents in same box style)
        final JScrollPane scrPlayers = new JScrollPane(pnlAllPlayers);
        scrPlayers.setBorder(new TitledBorder("Players"));
        scrPlayers.setPreferredSize(new Dimension(0, 90));
        scrPlayers.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        scrPlayers.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrPlayers.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pnlTop.add(scrPlayers);

        // Game-info strip: Phase | Turn | Mana
        final JPanel pnlGameInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 2));
        pnlGameInfo.setBorder(new LineBorder(Color.LIGHT_GRAY));
        lblPhase.setFont(lblPhase.getFont().deriveFont(11f));
        lblTurn.setFont(lblTurn.getFont().deriveFont(11f));
        lblMana.setFont(lblMana.getFont().deriveFont(11f));
        pnlGameInfo.add(lblPhase);
        pnlGameInfo.add(lblTurn);
        pnlGameInfo.add(lblMana);
        pnlGameInfo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        pnlTop.add(pnlGameInfo);

        root.add(pnlTop, BorderLayout.NORTH);

        // --- Center: stack/combat + battlefield ---
        final JPanel pnlCenter = new JPanel();
        pnlCenter.setLayout(new BoxLayout(pnlCenter, BoxLayout.Y_AXIS));

        // Stack & Combat info
        final JPanel pnlInfo = new JPanel(new GridLayout(1, 2, 5, 0));
        lblStack.setBorder(new TitledBorder("Stack"));
        lblCombat.setBorder(new TitledBorder("Combat"));
        pnlInfo.add(lblStack);
        pnlInfo.add(lblCombat);
        pnlInfo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        pnlCenter.add(pnlInfo);

        // My battlefield
        scrMyBattlefield.setPreferredSize(new Dimension(0, 200));
        pnlCenter.add(scrMyBattlefield);

        root.add(pnlCenter, BorderLayout.CENTER);

        // --- South: hand + prompt (card detail is now a full-window overlay) ---
        final JPanel pnlSouth = new JPanel(new BorderLayout(5, 5));

        scrMyHand.setPreferredSize(new Dimension(0, CARD_HEIGHT + 50));
        pnlSouth.add(scrMyHand, BorderLayout.CENTER);

        // Prompt + buttons
        final JPanel pnlPrompt = new JPanel(new BorderLayout(5, 0));
        pnlPrompt.setBorder(new TitledBorder("Action"));
        lblPrompt.setFont(lblPrompt.getFont().deriveFont(14f));
        lblPrompt.setBorder(new EmptyBorder(4, 8, 4, 8));
        pnlPrompt.add(lblPrompt, BorderLayout.CENTER);

        final JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        btnOk.setPreferredSize(new Dimension(100, 30));
        btnCancel.setPreferredSize(new Dimension(100, 30));
        pnlButtons.add(btnOk);
        pnlButtons.add(btnCancel);
        pnlPrompt.add(pnlButtons, BorderLayout.EAST);

        pnlSouth.add(pnlPrompt, BorderLayout.SOUTH);

        root.add(pnlSouth, BorderLayout.SOUTH);

        frame.setContentPane(root);

        // Set up the card detail overlay as the glass pane
        cardOverlayPane.setOpaque(false);
        cardOverlayPane.setVisible(false);
        frame.setGlassPane(cardOverlayPane);
    }

    // ==================== Concede (multiplayer-safe) ====================

    /**
     * Override concede so that in a multiplayer game, only THIS player is
     * eliminated.  The base implementation in {@link AbstractGuiGame} sends
     * {@code NextGameDecision.QUIT} after the concession, which instantly
     * ends the entire match.  Here we skip that step when the game is still
     * ongoing (other players are alive).
     */
    @Override
    public boolean concede() {
        final GameView gv = getGameView();
        if (gv == null || gv.isGameOver()) {
            return true;
        }
        if (!hasLocalPlayers()) {
            return true;
        }

        // Confirm with the player
        if (!showConfirmDialog(
                Localizer.getInstance().getMessage("lblConcedeCurrentGame"),
                Localizer.getInstance().getMessage("lblConcedeTitle"),
                Localizer.getInstance().getMessage("lblConcede"),
                Localizer.getInstance().getMessage("lblCancel"))) {
            return false;
        }

        // Concede only the controllers owned by THIS window (should be 1)
        for (final IGameController c : getOriginalGameControllers()) {
            c.concede();
        }

        // If the game ended (e.g. last opponent left), let the normal flow
        // handle it.  Otherwise do NOT send QUIT — just disable this window.
        if (!gv.isGameOver()) {
            // Game still running with other players — just close this window
            FThreads.invokeInEdtNowOrLater(() -> {
                frame.setTitle(frame.getTitle() + " (eliminated)");
                btnOk.setEnabled(false);
                btnCancel.setEnabled(false);
                pnlMyHand.removeAll();
                pnlMyHand.revalidate();
                pnlMyHand.repaint();
                lblPrompt.setText("You have conceded. Spectating...");
            });
            return false; // don't close the window yet
        }
        return false; // wait for win/lose screen
    }

    // ==================== IGuiGame: Lifecycle ====================

    @Override
    public void openView(final TrackableCollection<PlayerView> myPlayers) {
        final GameView gameView = getGameView();
        sortedPlayers = gameView.getPlayers();

        // Find my player
        if (myPlayers != null && !myPlayers.isEmpty()) {
            myPlayer = myPlayers.get(0);
        }

        FThreads.invokeInEdtNowOrLater(() -> {
            final String name = myPlayer != null ? myPlayer.getName() : "Player " + (playerIndex + 1);
            rebuildAllPlayers();
            rebuildBattlefield();
            rebuildHand();
            frame.setTitle("Commander - " + name);
            frame.setVisible(true);
            windowOpen = true;
        });
    }

    @Override
    public void afterGameEnd() {
        super.afterGameEnd();
        FThreads.invokeInEdtNowOrLater(() -> {
            if (frame != null) {
                frame.dispose();
                windowOpen = false;
            }
        });
    }

    @Override
    public void finishGame() {
        FThreads.invokeInEdtNowOrLater(() -> {
            final GameView gv = getGameView();
            if (gv != null) {
                final String winner = gv.getWinningPlayerName();
                final String msg = winner != null
                    ? "Game Over! Winner: " + winner
                    : "Game Over!";
                JOptionPane.showMessageDialog(frame, msg, "Game Over", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    // ==================== IGuiGame: Updates ====================

    @Override
    protected void updateCurrentPlayer(final PlayerView player) {
        FThreads.invokeInEdtNowOrLater(() -> {
            if (player != null && player.equals(myPlayer)) {
                frame.getRootPane().setBorder(BorderFactory.createLineBorder(ACTIVE_BORDER, 3));
                frame.toFront();
            } else {
                frame.getRootPane().setBorder(null);
            }
        });
    }

    @Override
    public void updatePhase(final boolean saveState) {
        FThreads.invokeInEdtNowOrLater(() -> {
            final GameView gv = getGameView();
            if (gv == null) return;
            final PhaseType ph = gv.getPhase();
            lblPhase.setText("Phase: " + (ph != null ? ph.nameForUi : "---"));
        });
    }

    @Override
    public void updateTurn(final PlayerView player) {
        FThreads.invokeInEdtNowOrLater(() -> {
            lblTurn.setText("Turn: " + (player != null ? player.getName() : "---"));
            rebuildAllPlayers();
        });
    }

    @Override
    public void updateLives(final Iterable<PlayerView> livesUpdate) {
        FThreads.invokeInEdtNowOrLater(this::rebuildAllPlayers);
    }

    @Override
    public void updateShards(final Iterable<PlayerView> shardsUpdate) {
        // mobile adventure only - no-op
    }

    @Override
    public void updateManaPool(final Iterable<PlayerView> manaPoolUpdate) {
        FThreads.invokeInEdtNowOrLater(() -> {
            if (myPlayer != null) {
                lblMana.setText("Mana: " + describeMana(myPlayer));
            }
        });
    }

    @Override
    public void updateZones(final Iterable<PlayerZoneUpdate> zonesToUpdate) {
        FThreads.invokeInEdtNowOrLater(() -> {
            boolean needBattlefield = false;
            boolean needHand = false;
            boolean needOpponents = false;
            for (final PlayerZoneUpdate update : zonesToUpdate) {
                for (final ZoneType zone : update.getZones()) {
                    switch (zone) {
                        case Battlefield: needBattlefield = true; needOpponents = true; break;
                        case Hand: needHand = true; break;
                        default: break;
                    }
                }
            }
            if (needBattlefield) { rebuildBattlefield(); }
            if (needHand) { rebuildHand(); }
            if (needOpponents) { rebuildAllPlayers(); }
        });
    }

    @Override
    public void updateCards(final Iterable<CardView> cards) {
        FThreads.invokeInEdtNowOrLater(() -> {
            for (final CardView c : cards) {
                final CardLabel lbl = cardPanelMap.get(c.getId());
                if (lbl != null) {
                    lbl.updateFromCard(c);
                }
            }
        });
    }

    @Override
    public void updateStack() {
        FThreads.invokeInEdtNowOrLater(() -> {
            final GameView gv = getGameView();
            if (gv == null) return;
            final int stackSize = gv.getStack().size();
            if (stackSize == 0) {
                lblStack.setText("Stack: empty");
            } else {
                final StringBuilder sb = new StringBuilder("<html>Stack (" + stackSize + "):<br>");
                for (int i = 0; i < Math.min(stackSize, 5); i++) {
                    sb.append("- ").append(gv.getStack().get(i).toString()).append("<br>");
                }
                if (stackSize > 5) sb.append("...");
                sb.append("</html>");
                lblStack.setText(sb.toString());
            }
        });
    }

    @Override
    public void updatePlayerControl() {
        // Refresh hand views when control changes
        FThreads.invokeInEdtNowOrLater(this::rebuildHand);
    }

    @Override
    public void showCombat() {
        FThreads.invokeInEdtNowOrLater(() -> {
            final GameView gv = getGameView();
            if (gv == null) return;
            final CombatView combat = gv.getCombat();
            if (combat != null && combat.getNumAttackers() > 0) {
                lblCombat.setText("<html>Combat: " + combat.getNumAttackers() + " attackers</html>");
            } else {
                lblCombat.setText("");
            }
        });
    }

    // ==================== IGuiGame: Prompts & Buttons ====================

    @Override
    public void showPromptMessage(final PlayerView playerView, final String message) {
        FThreads.invokeInEdtNowOrLater(() ->
            lblPrompt.setText(message != null ? message : "")
        );
    }

    @Override
    public void showCardPromptMessage(final PlayerView playerView, final String message, final CardView card) {
        showPromptMessage(playerView, message);
        if (card != null) { setCard(card); }
    }

    @Override
    public void updateButtons(final PlayerView owner, final String label1, final String label2,
                              final boolean enable1, final boolean enable2, final boolean focus1) {
        FThreads.invokeInEdtNowOrLater(() -> {
            btnOk.setText(label1);
            btnCancel.setText(label2);
            btnOk.setEnabled(enable1);
            btnCancel.setEnabled(enable2);
            if (enable1 && focus1) {
                btnOk.requestFocusInWindow();
            } else if (enable2) {
                btnCancel.requestFocusInWindow();
            }
        });
    }

    @Override
    public void flashIncorrectAction() {
        FThreads.invokeInEdtNowOrLater(() -> {
            final Color orig = lblPrompt.getForeground();
            lblPrompt.setForeground(Color.RED);
            final javax.swing.Timer t = new javax.swing.Timer(500, e -> lblPrompt.setForeground(orig));
            t.setRepeats(false);
            t.start();
        });
    }

    @Override
    public void alertUser() {
        FThreads.invokeInEdtNowOrLater(() -> {
            Toolkit.getDefaultToolkit().beep();
            frame.toFront();
        });
    }

    // ==================== IGuiGame: Selection / Highlighting ====================

    @Override
    public void setSelectables(final Iterable<CardView> cards) {
        super.setSelectables(cards);
        FThreads.invokeInEdtNowOrLater(() -> {
            for (final CardView c : cards) {
                final CardLabel lbl = cardPanelMap.get(c.getId());
                if (lbl != null) {
                    lbl.setSelectable(true);
                }
            }
        });
    }

    @Override
    public void clearSelectables() {
        super.clearSelectables();
        FThreads.invokeInEdtNowOrLater(() -> {
            for (final CardLabel lbl : cardPanelMap.values()) {
                lbl.setSelectable(false);
            }
        });
    }

    @Override
    public void setPanelSelection(final CardView card) {
        FThreads.invokeInEdtNowOrLater(() -> {
            final CardLabel lbl = cardPanelMap.get(card.getId());
            if (lbl != null) {
                lbl.setHighlighted(true);
            }
        });
    }

    @Override
    public void setCard(final CardView c) {
        FThreads.invokeInEdtNowOrLater(() -> {
            if (c == null) {
                overlayCardImage = null;
                overlayCardText = null;
                cardOverlayPane.setVisible(false);
                return;
            }
            try {
                final BufferedImage img = FImageUtil.getImage(c.getCurrentState());
                if (img != null) {
                    overlayCardImage = img;
                    overlayCardText = null;
                } else {
                    overlayCardImage = null;
                    overlayCardText = c.toString();
                }
            } catch (final Exception ex) {
                overlayCardImage = null;
                overlayCardText = c.toString();
            }
            cardOverlayPane.setVisible(true);
            cardOverlayPane.repaint();
        });
    }

    @Override
    public void setPlayerAvatar(final LobbyPlayer player, final IHasIcon ihi) {
        // Stored but not displayed in the simple UI
    }

    // ==================== IGuiGame: Overlays & Mana ====================

    @Override
    public void enableOverlay() { /* no-op for simple window */ }

    @Override
    public void disableOverlay() { /* no-op for simple window */ }

    @Override
    public void showManaPool(final PlayerView player) { /* always visible in header */ }

    @Override
    public void hideManaPool(final PlayerView player) { /* always visible in header */ }

    // ==================== IGuiGame: Zones ====================

    @Override
    public Iterable<PlayerZoneUpdate> tempShowZones(final PlayerView controller, final Iterable<PlayerZoneUpdate> zonesToUpdate) {
        // Show a popup dialog with zone contents
        FThreads.invokeInEdtNowOrLater(() -> {
            for (final PlayerZoneUpdate update : zonesToUpdate) {
                final PlayerView p = update.getPlayer();
                for (final ZoneType zone : update.getZones()) {
                    if (zone == ZoneType.Battlefield) continue;
                    showZonePopup(p, zone);
                }
            }
        });
        return zonesToUpdate;
    }

    @Override
    public void hideZones(final PlayerView controller, final Iterable<PlayerZoneUpdate> zonesToUpdate) {
        // Zone popups auto-close as modal dialogs
    }

    @Override
    public PlayerZoneUpdates openZones(final PlayerView controller, final Collection<ZoneType> zones,
                                        final Map<PlayerView, Object> players, final boolean backupLastZones) {
        final PlayerZoneUpdates updates = new PlayerZoneUpdates();
        for (final PlayerView pv : players.keySet()) {
            for (final ZoneType zone : zones) {
                if (zone == ZoneType.Battlefield || zone == ZoneType.Hand || zone == ZoneType.Stack) continue;
                updates.add(new PlayerZoneUpdate(pv, zone));
            }
        }
        tempShowZones(controller, updates);
        return updates;
    }

    @Override
    public void restoreOldZones(final PlayerView playerView, final PlayerZoneUpdates playerZoneUpdates) {
        hideZones(playerView, playerZoneUpdates);
    }

    // ==================== IGuiGame: Dialogs ====================

    @Override
    public void message(final String message, final String title) {
        SOptionPane.showMessageDialog(message, title);
    }

    @Override
    public void showErrorDialog(final String message, final String title) {
        SOptionPane.showErrorDialog(message, title);
    }

    @Override
    public boolean confirm(final CardView c, final String question, final boolean defaultIsYes, final List<String> options) {
        return GuiDialog.confirm(c, question, defaultIsYes, options, null);
    }

    @Override
    public boolean showConfirmDialog(final String message, final String title,
                                      final String yesButtonText, final String noButtonText, final boolean defaultYes) {
        final List<String> options = ImmutableList.of(yesButtonText, noButtonText);
        final int reply = SOptionPane.showOptionDialog(message, title, SOptionPane.QUESTION_ICON, options, defaultYes ? 0 : 1);
        return reply == 0;
    }

    @Override
    public int showOptionDialog(final String message, final String title, final FSkinProp icon,
                                 final List<String> options, final int defaultOption) {
        return FOptionPane.showOptionDialog(message, title, icon == null ? null : FSkin.getImage(icon), options, defaultOption);
    }

    @Override
    public String showInputDialog(final String message, final String title, final FSkinProp icon,
                                   final String initialInput, final List<String> inputOptions, final boolean isNumeric) {
        return FOptionPane.showInputDialog(message, title, icon == null ? null : FSkin.getImage(icon), initialInput, inputOptions);
    }

    // ==================== IGuiGame: Choices ====================

    @Override
    public <T> List<T> getChoices(final String message, final int min, final int max,
                                   final List<T> choices, final List<T> selected,
                                   final FSerializableFunction<T, String> display) {
        return GuiChoose.getChoices(message, min, max, choices, selected, display, null);
    }

    @Override
    public <T> List<T> order(final String title, final String top, final int remainingObjectsMin,
                              final int remainingObjectsMax, final List<T> sourceChoices,
                              final List<T> destChoices, final CardView referenceCard,
                              final boolean sideboardingMode) {
        return GuiChoose.order(title, top, remainingObjectsMin, remainingObjectsMax,
                sourceChoices, destChoices, referenceCard, sideboardingMode, null);
    }

    @Override
    public List<PaperCard> sideboard(final CardPool sideboard, final CardPool main, final String message) {
        return GuiChoose.sideboard(null, sideboard.toFlatList(), main.toFlatList(), message);
    }

    @Override
    public GameEntityView chooseSingleEntityForEffect(final String title,
            final List<? extends GameEntityView> optionList, final DelayedReveal delayedReveal,
            final boolean isOptional) {
        if (delayedReveal != null) {
            reveal(delayedReveal.getMessagePrefix(), delayedReveal.getCards());
        }
        if (isOptional) {
            return oneOrNone(title, optionList);
        }
        return one(title, optionList);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<GameEntityView> chooseEntitiesForEffect(final String title,
            final List<? extends GameEntityView> optionList, final int min, final int max,
            final DelayedReveal delayedReveal) {
        if (delayedReveal != null) {
            reveal(delayedReveal.getMessagePrefix(), delayedReveal.getCards());
        }
        return (List<GameEntityView>) order(title, Localizer.getInstance().getMessage("lblSelected"),
                optionList.size() - max, optionList.size() - min, optionList, null, null, false);
    }

    @Override
    public List<CardView> manipulateCardList(final String title, final Iterable<CardView> cards,
            final Iterable<CardView> manipulable, final boolean toTop, final boolean toBottom,
            final boolean toAnywhere) {
        // Simplified fallback: return cards in original order
        final List<CardView> result = new ArrayList<>();
        for (final CardView c : cards) { result.add(c); }
        return result;
    }

    // ==================== IGuiGame: Abilities & Combat ====================

    @Override
    public SpellAbilityView getAbilityToPlay(final CardView hostCard, final List<SpellAbilityView> abilities,
                                              final ITriggerEvent triggerEvent) {
        if (abilities.isEmpty()) return null;
        if (abilities.size() == 1) {
            if (triggerEvent == null) return abilities.get(0);
            if (!abilities.get(0).promptIfOnlyPossibleAbility()) {
                return abilities.get(0).canPlay() ? abilities.get(0) : null;
            }
        }

        if (triggerEvent == null) {
            return GuiChoose.oneOrNone(
                    Localizer.getInstance().getMessage("lblChooseAbilityToPlay"), abilities);
        }

        // Show a popup menu for ability selection (like CMatchUI)
        final AtomicReference<SpellAbilityView> chosen = new AtomicReference<>();
        FThreads.invokeInEdtNowOrLater(() -> {
            final JPopupMenu menu = new JPopupMenu("Abilities");
            for (final SpellAbilityView ab : abilities) {
                final boolean enabled = ab.canPlay();
                final JMenuItem item = new JMenuItem(ab.toString());
                item.setEnabled(enabled);
                item.addActionListener(e -> {
                    getGameController().selectAbility(ab);
                });
                menu.add(item);
            }
            // Show near the frame center
            menu.show(frame.getContentPane(), triggerEvent.getX(), triggerEvent.getY());
        });
        return null; // Delay until user picks from menu
    }

    @Override
    public Map<CardView, Integer> assignCombatDamage(final CardView attacker, final List<CardView> blockers,
            final int damage, final GameEntityView defender, final boolean overrideOrder, final boolean maySkip) {
        if (damage <= 0) return Collections.emptyMap();

        final CardView firstBlocker = blockers.get(0);
        if (!overrideOrder && !attacker.getCurrentState().hasDeathtouch()
                && firstBlocker.getLethalDamage() >= damage) {
            return ImmutableMap.of(firstBlocker, damage);
        }

        // Simple dialog-based damage assignment
        final AtomicReference<Map<CardView, Integer>> result = new AtomicReference<>();
        FThreads.invokeInEdtAndWait(() -> {
            final Map<CardView, Integer> damageMap = new LinkedHashMap<>();
            int remaining = damage;

            for (int i = 0; i < blockers.size(); i++) {
                final CardView blocker = blockers.get(i);
                final boolean isLast = (i == blockers.size() - 1);
                if (isLast) {
                    damageMap.put(blocker, remaining);
                } else {
                    final int lethal = blocker.getLethalDamage();
                    final String msg = "Assign damage to " + blocker.toString()
                            + " (lethal=" + lethal + ", remaining=" + remaining + "):";
                    final String input = JOptionPane.showInputDialog(frame, msg, String.valueOf(Math.min(lethal, remaining)));
                    int assigned;
                    try {
                        assigned = Integer.parseInt(input);
                        assigned = Math.max(0, Math.min(assigned, remaining));
                    } catch (final Exception ex) {
                        assigned = Math.min(lethal, remaining);
                    }
                    damageMap.put(blocker, assigned);
                    remaining -= assigned;
                }
            }
            result.set(damageMap);
        });
        return result.get();
    }

    @Override
    public Map<Object, Integer> assignGenericAmount(final CardView effectSource, final Map<Object, Integer> target,
            final int amount, final boolean atLeastOne, final String amountLabel) {
        if (amount <= 0) return Collections.emptyMap();

        final AtomicReference<Map<Object, Integer>> result = new AtomicReference<>();
        FThreads.invokeInEdtAndWait(() -> {
            final Map<Object, Integer> assignMap = new LinkedHashMap<>();
            int remaining = amount;
            final List<Object> targets = new ArrayList<>(target.keySet());

            for (int i = 0; i < targets.size(); i++) {
                final Object tgt = targets.get(i);
                final boolean isLast = (i == targets.size() - 1);
                if (isLast) {
                    assignMap.put(tgt, remaining);
                } else {
                    final String msg = "Assign " + amountLabel + " to " + tgt.toString()
                            + " (remaining=" + remaining + "):";
                    final String input = JOptionPane.showInputDialog(frame, msg, atLeastOne ? "1" : "0");
                    int assigned;
                    try {
                        assigned = Integer.parseInt(input);
                        assigned = Math.max(atLeastOne ? 1 : 0, Math.min(assigned, remaining));
                    } catch (final Exception ex) {
                        assigned = atLeastOne ? 1 : 0;
                    }
                    assignMap.put(tgt, assigned);
                    remaining -= assigned;
                }
            }
            result.set(assignMap);
        });
        return result.get();
    }

    // ==================== IGuiGame: Phase Skipping ====================

    @Override
    public boolean isUiSetToSkipPhase(final PlayerView playerTurn, final PhaseType phase) {
        return false; // Never skip phases for human commander players
    }

    @Override
    public GameState getGamestate() {
        return null;
    }

    // ==================== Private: UI Builders ====================

    /**
     * Rebuild the unified players bar.  Every player (including self) gets
     * the same box format.  The "you" box is highlighted with a green border.
     *
     * Each box shows:
     *   Row 1:  Name  |  Life: X
     *   Row 2:  Psn:X  Hand:X  BF:X  Lib:X  GY:X
     */
    private void rebuildAllPlayers() {
        pnlAllPlayers.removeAll();
        if (sortedPlayers == null) return;

        for (final PlayerView p : sortedPlayers) {
            final boolean isMe = p.equals(myPlayer);

            final JPanel box = new JPanel(new GridLayout(2, 1, 0, 1));
            if (isMe) {
                box.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ACTIVE_BORDER, 2),
                        new EmptyBorder(4, 8, 4, 8)));
                box.setBackground(new Color(230, 255, 230));
            } else {
                box.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(Color.GRAY),
                        new EmptyBorder(4, 8, 4, 8)));
            }

            // Row 1: Name | Life
            final String tag = isMe ? " (You)" : "";
            final JLabel row1 = new JLabel(p.getName() + tag + "  |  Life: " + p.getLife());
            row1.setFont(row1.getFont().deriveFont(Font.BOLD, 12f));
            box.add(row1);

            // Row 2: compact metadata
            final int poison = p.getCounters(forge.game.card.CounterEnumType.POISON);
            final FCollectionView<CardView> pHand = p.getCards(ZoneType.Hand);
            final int handCount = pHand != null ? pHand.size() : 0;
            final FCollectionView<CardView> pBF = p.getCards(ZoneType.Battlefield);
            final int bfCount = pBF != null ? pBF.size() : 0;
            final FCollectionView<CardView> pLib = p.getCards(ZoneType.Library);
            final int libCount = pLib != null ? pLib.size() : 0;
            final FCollectionView<CardView> pGY = p.getCards(ZoneType.Graveyard);
            final int gyCount = pGY != null ? pGY.size() : 0;

            final JLabel row2 = new JLabel(
                    "Psn:" + poison + "  Hand:" + handCount
                    + "  BF:" + bfCount + "  Lib:" + libCount + "  GY:" + gyCount);
            row2.setFont(row2.getFont().deriveFont(11f));
            row2.setForeground(Color.DARK_GRAY);
            box.add(row2);

            // Clickable for targeting
            box.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(final MouseEvent e) {
                    final IGameController gc = getGameController();
                    if (gc != null) {
                        gc.selectPlayer(p, new MouseTriggerEvent(e));
                    }
                }
            });
            box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            box.setOpaque(true);

            pnlAllPlayers.add(box);
        }
        pnlAllPlayers.revalidate();
        pnlAllPlayers.repaint();

        // Also update own mana in the info strip
        if (myPlayer != null) {
            lblMana.setText("Mana: " + describeMana(myPlayer));
        }
    }

    private void rebuildBattlefield() {
        pnlMyBattlefield.removeAll();
        // Remove old battlefield card labels from map
        cardPanelMap.entrySet().removeIf(e -> !e.getValue().isSmall() && e.getValue().getZoneType() == ZoneType.Battlefield);

        if (myPlayer == null) return;
        final FCollectionView<CardView> cards = myPlayer.getCards(ZoneType.Battlefield);
        if (cards == null) return;

        for (final CardView c : cards) {
            final CardLabel cardLbl = new CardLabel(c, false);
            cardLbl.setZoneType(ZoneType.Battlefield);
            pnlMyBattlefield.add(cardLbl);
            cardPanelMap.put(c.getId(), cardLbl);
        }
        pnlMyBattlefield.revalidate();
        pnlMyBattlefield.repaint();
    }

    private void rebuildHand() {
        pnlMyHand.removeAll();
        cardPanelMap.entrySet().removeIf(e -> e.getValue().getZoneType() == ZoneType.Hand);

        if (myPlayer == null) return;
        final FCollectionView<CardView> cards = myPlayer.getCards(ZoneType.Hand);
        if (cards == null) return;

        for (final CardView c : cards) {
            final CardLabel cardLbl = new CardLabel(c, false);
            cardLbl.setZoneType(ZoneType.Hand);
            pnlMyHand.add(cardLbl);
            cardPanelMap.put(c.getId(), cardLbl);
        }
        pnlMyHand.revalidate();
        pnlMyHand.repaint();
    }

    private void showZonePopup(final PlayerView player, final ZoneType zone) {
        final FCollectionView<CardView> cards = player.getCards(zone);
        if (cards == null || cards.isEmpty()) return;

        final JDialog dialog = new JDialog(frame, player.getName() + " - " + zone.name(), true);
        dialog.setLayout(new BorderLayout());
        final JPanel cardPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        for (final CardView c : cards) {
            final JLabel lbl = new JLabel(c.toString());
            lbl.setPreferredSize(new Dimension(150, 25));
            lbl.setBorder(new LineBorder(Color.GRAY));
            cardPanel.add(lbl);
        }
        dialog.add(new JScrollPane(cardPanel), BorderLayout.CENTER);
        final JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dialog.dispose());
        dialog.add(btnClose, BorderLayout.SOUTH);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private String describeMana(final PlayerView p) {
        if (p.getMana(0) == 0 && p.getMana(1) == 0 && p.getMana(2) == 0
                && p.getMana(3) == 0 && p.getMana(4) == 0 && p.getMana(5) == 0) {
            return "empty";
        }
        final StringBuilder sb = new StringBuilder();
        final String[] colors = {"W", "U", "B", "R", "G", "C"};
        for (int i = 0; i < 6; i++) {
            final int amt = p.getMana(i);
            if (amt > 0) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(colors[i]).append(":").append(amt);
            }
        }
        return sb.toString();
    }

    // ==================== Inner Class: CardLabel ====================

    /**
     * A JLabel that represents a card on the battlefield or in hand.
     * Clickable to interact with the game engine.
     */
    private class CardLabel extends JLabel {
        private final CardView cardView;
        private final boolean small;
        private ZoneType zoneType;
        private boolean selectable;
        private boolean highlighted;

        CardLabel(final CardView card, final boolean small) {
            this.cardView = card;
            this.small = small;

            final int w = small ? CARD_SMALL_WIDTH : CARD_WIDTH;
            final int h = small ? CARD_SMALL_HEIGHT : CARD_HEIGHT;
            setPreferredSize(new Dimension(w, h));
            setMinimumSize(new Dimension(w, h));
            setMaximumSize(new Dimension(w, h));
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);

            updateFromCard(card);

            setBorder(new LineBorder(Color.DARK_GRAY));
            setOpaque(true);
            setBackground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(final MouseEvent e) {
                    onCardClicked(e);
                }

                @Override
                public void mouseEntered(final MouseEvent e) {
                    setCard(cardView);
                }

                @Override
                public void mouseExited(final MouseEvent e) {
                    setCard(null); // hide overlay
                }
            });
        }

        void updateFromCard(final CardView card) {
            final int w = small ? CARD_SMALL_WIDTH - 4 : CARD_WIDTH - 4;
            final int h = small ? CARD_SMALL_HEIGHT - 4 : CARD_HEIGHT - 4;

            try {
                final BufferedImage img = FImageUtil.getImage(card.getCurrentState());
                if (img != null) {
                    setIcon(new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_FAST)));
                    setText("");
                } else {
                    setIcon(null);
                    setText("<html><center>" + card.toString() + "</center></html>");
                    setFont(getFont().deriveFont(small ? 9f : 11f));
                }
            } catch (final Exception ex) {
                setIcon(null);
                setText("<html><center>" + card.toString() + "</center></html>");
                setFont(getFont().deriveFont(small ? 9f : 11f));
            }

            // Show tapped state
            if (card.isTapped()) {
                setBackground(new Color(220, 220, 220));
            } else {
                setBackground(Color.WHITE);
            }

            // Update border for state
            updateBorder();

            setToolTipText(card.toString());
        }

        void setSelectable(final boolean sel) {
            this.selectable = sel;
            updateBorder();
        }

        void setHighlighted(final boolean hl) {
            this.highlighted = hl;
            updateBorder();
            if (hl) {
                setBackground(HIGHLIGHT_BG);
            }
        }

        private void updateBorder() {
            if (highlighted) {
                setBorder(BorderFactory.createLineBorder(ACTIVE_BORDER, 3));
            } else if (selectable) {
                setBorder(BorderFactory.createLineBorder(SELECTABLE_BORDER, 2));
            } else {
                setBorder(new LineBorder(Color.DARK_GRAY));
            }
        }

        boolean isSmall() { return small; }
        ZoneType getZoneType() { return zoneType; }
        void setZoneType(final ZoneType zt) { this.zoneType = zt; }

        private void onCardClicked(final MouseEvent e) {
            final IGameController gc = getGameController();
            if (gc != null) {
                gc.selectCard(cardView, null, new MouseTriggerEvent(e));
            }
        }
    }
}
