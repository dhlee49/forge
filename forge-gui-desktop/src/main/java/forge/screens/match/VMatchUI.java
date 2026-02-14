package forge.screens.match;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import forge.Singletons;
import forge.gui.framework.DragCell;
import forge.gui.framework.EDocID;
import forge.gui.framework.FScreen;
import forge.gui.framework.IVTopLevelUI;
import forge.gui.framework.RectangleOfDouble;
import forge.gui.framework.SRearrangingUtil;
import forge.gui.framework.SResizingUtil;
import forge.gui.framework.VEmptyDoc;
import forge.localinstance.properties.ForgePreferences;
import forge.screens.match.views.VDev;
import forge.screens.match.views.VField;
import forge.screens.match.views.VHand;
import forge.sound.MusicPlaylist;
import forge.sound.SoundSystem;
import forge.toolbox.FButton;
import forge.view.FView;

/** 
 * Top level view class for match UI drag layout.<br>
 * Has access methods for all draggable documents.<br>
 * Uses singleton pattern.<br>
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */
public class VMatchUI implements IVTopLevelUI {
    private List<VField> lstFields = new ArrayList<>();
    private List<VHand> lstHands = new ArrayList<>();

    // Other instantiations
    private final CMatchUI control;

    VMatchUI(final CMatchUI control) {
        this.control = control;
    }

    @Override
    public void instantiate() {
    }

    @Override
    public void populate() {
        // Dev mode disabled? Remove from parent cell if exists.
        final VDev vDev = getControl().getCDev().getView();
        if (!ForgePreferences.DEV_MODE) {
            if (vDev.getParentCell() != null) {
                final DragCell parent = vDev.getParentCell();
                parent.removeDoc(vDev);
                vDev.setParentCell(null);

                // If dev mode was first tab, the new first tab needs re-selecting.
                if (parent.getDocs().size() > 0) {
                    parent.setSelected(parent.getDocs().get(0));
                }
            }
        } else if (vDev.getParentCell() == null) {
            // Dev mode enabled? May already by added, or put in message cell by default.
            getControl().getCPrompt().getView().getParentCell().addDoc(vDev);
        }

        //focus first enabled Prompt button if returning to match screen
        if (getBtnOK().isEnabled()) {
            getBtnOK().requestFocusInWindow();
        } else if (getBtnCancel().isEnabled()) {
            getBtnCancel().requestFocusInWindow();
        }

        // Add extra players to field panels.
        final boolean is4PlayerGrid = (lstFields.size() == 4);
        if (is4PlayerGrid) {
            // 4-player Commander layout: 2x2 grid.
            // Default match.xml has FIELD_1 (opponent, top) and FIELD_0 (you, bottom)
            // stacked in a single column.  We rearrange into a 2x2 grid:
            //   Top-left:  FIELD_1   Top-right:  FIELD_3
            //   Bot-left:  FIELD_0   Bot-right:  FIELD_2
            //
            // We also shrink the side panels to give the 4 battlefields more room.
            final DragCell cell0 = lstFields.get(0).getParentCell(); // FIELD_0
            final DragCell cell1 = lstFields.get(1).getParentCell(); // FIELD_1

            if (cell0 != null && cell1 != null) {
                // Shrink side panels to make room for the 2x2 grid.
                // Default: left=0.2, fields=0.6, right=0.2
                // Commander: left=0.1, fields=0.8, right=0.1
                final double sideW = 0.1;
                final double gridX = sideW;
                final double gridW = 1.0 - sideW * 2.0; // 0.8

                // Resize left side panels (stack/log and message/dock)
                for (final DragCell cell : FView.SINGLETON_INSTANCE.getDragCells()) {
                    RectangleOfDouble cb = cell.getRoughBounds();
                    // Left column panels (x near 0, small width)
                    if (cb.getX() < 0.01 && cb.getW() < 0.3) {
                        cell.setRoughBounds(new RectangleOfDouble(0.0, cb.getY(), sideW, cb.getH()));
                    }
                    // Right column panels (x near 0.8, small width)
                    else if (cb.getX() > 0.7 && cb.getW() < 0.3) {
                        cell.setRoughBounds(new RectangleOfDouble(1.0 - sideW, cb.getY(), sideW, cb.getH()));
                    }
                }

                // Remove hand panels from the main board — hands are shown
                // in each human player's popup window, so we reclaim that
                // vertical space for the 2x2 battlefield grid.
                // We must remove the cell immediately (not deferred) so that
                // resizeWindow() doesn't lay it out in the now-reclaimed space.
                for (final EDocID handId : EDocID.Hands) {
                    final DragCell handCell = handId.getDoc().getParentCell();
                    if (handCell != null) {
                        handCell.removeDoc(handId.getDoc());
                        handId.setDoc(new VEmptyDoc(handId));
                        // Remove the now-empty cell immediately
                        if (handCell.getDocs().isEmpty()) {
                            FView.SINGLETON_INSTANCE.removeDragCell(handCell);
                        }
                    }
                }

                // Force the 2x2 grid to span the full window height (y=0 to y=1).
                // Don't compute from original cell bounds — a saved layout may have
                // different values, leaving an empty gap at the bottom.
                final double fieldY = 0.0;
                final double fieldH = 1.0;

                final double halfW = gridW / 2.0;  // 0.4 each
                final double halfH = fieldH / 2.0; // 0.5 each

                // Top-left: FIELD_1 (opponent 1)
                cell1.setRoughBounds(new RectangleOfDouble(gridX, fieldY, halfW, halfH));
                // Bottom-left: FIELD_0 (you)
                cell0.setRoughBounds(new RectangleOfDouble(gridX, fieldY + halfH, halfW, halfH));

                // Top-right: FIELD_3 (opponent 3)
                VField vField3 = lstFields.get(3);
                if (vField3.getParentCell() == null) {
                    DragCell newCell3 = new DragCell();
                    newCell3.setRoughBounds(new RectangleOfDouble(gridX + halfW, fieldY, halfW, halfH));
                    newCell3.addDoc(vField3);
                    FView.SINGLETON_INSTANCE.addDragCell(newCell3);
                }

                // Bottom-right: FIELD_2 (opponent 2)
                VField vField2 = lstFields.get(2);
                if (vField2.getParentCell() == null) {
                    DragCell newCell2 = new DragCell();
                    newCell2.setRoughBounds(new RectangleOfDouble(gridX + halfW, fieldY + halfH, halfW, halfH));
                    newCell2.addDoc(vField2);
                    FView.SINGLETON_INSTANCE.addDragCell(newCell2);
                }

                SResizingUtil.resizeWindow();
            }
        } else {
            // Default: add extra players as tabs in alternating cells.
            for (int i = 2; i < lstFields.size(); i++) {
                VField vField = lstFields.get(i);
                if (vField.getParentCell() == null) {
                    lstFields.get(i % 2).getParentCell().addDoc(vField);
                }
            }
        }

        // Set up hand panels (skip in 4-player mode — hands live in popups)
        if (!is4PlayerGrid) {
            // Determine (an) existing hand panel
            DragCell cellWithHands = null;
            for (final EDocID handId : EDocID.Hands) {
                cellWithHands = handId.getDoc().getParentCell();
                if (cellWithHands != null && cellWithHands.isShowing()) {
                    break;
                }
                cellWithHands = null;
            }
            if (cellWithHands == null) {
                // Default to a cell we know exists
                cellWithHands = EDocID.REPORT_LOG.getDoc().getParentCell();
            }
            for (int iHandId = 0; iHandId < EDocID.Hands.length; iHandId++) {
                final EDocID handId = EDocID.Hands[iHandId];
                final DragCell parentCell = handId.getDoc().getParentCell();
                VHand myVHand = null;
                for (final VHand vHand : lstHands) {
                    if (handId.equals(vHand.getDocumentID())) {
                        myVHand = vHand;
                        break;
                    }
                }

                if (myVHand == null) {
                    // Hand not present, remove cell if necessary
                    if (parentCell != null) {
                        parentCell.removeDoc(handId.getDoc());
                        handId.setDoc(new VEmptyDoc(handId));
                    }
                } else {
                    // Hand present, add it if necessary (check isShowing for stale references)
                    if (parentCell == null || !parentCell.isShowing()) {
                        final EDocID fieldDoc = EDocID.Fields[iHandId];
                        DragCell fieldCell = fieldDoc.getDoc().getParentCell();
                        if (fieldCell != null && fieldCell.isShowing()) {
                            fieldCell.addDoc(myVHand);
                            continue;
                        }
                        cellWithHands.addDoc(myVHand);
                    }
                }
            }
        }

        // Fill in gaps
        SwingUtilities.invokeLater(() -> {
            for (final DragCell c : FView.SINGLETON_INSTANCE.getDragCells()) {
                if (c.getDocs().isEmpty()) {
                    if (!is4PlayerGrid) {
                        // In the standard 2-player layout, use the normal gap-fill
                        // algorithm which expands neighboring cells.
                        // In the 4-player Commander grid the cell boundaries don't
                        // form a standard tiling, so fillGap() would throw.  We
                        // simply remove the empty cell instead.
                        try {
                            SRearrangingUtil.fillGap(c);
                        } catch (final UnsupportedOperationException ignored) {
                            // Layout was custom; gap can't be filled normally
                        }
                    }
                    FView.SINGLETON_INSTANCE.removeDragCell(c);
                }
            }
        });
    }

    public CMatchUI getControl() {
        return this.control;
    }

    public void setFieldViews(final List<VField> lst0) {
        this.lstFields = lst0;
    }

    public List<VField> getFieldViews() {
        return lstFields;
    }

    public void setHandViews(final List<VHand> lst0) {
        this.lstHands = lst0;
    }

    public FButton getBtnCancel() {
        return getControl().getCPrompt().getView().getBtnCancel();
    }

    public FButton getBtnOK() {
        return getControl().getCPrompt().getView().getBtnOK();
    }

    public List<VHand> getHands() {
        return lstHands;
    }

    @Override
    public boolean onSwitching(final FScreen fromScreen, final FScreen toScreen) {
        return true;
    }

    @Override
    public boolean onClosing(final FScreen screen) {
        if (!Singletons.getControl().getCurrentScreen().equals(screen)) {
            // Switch to this screen if not already showing
            Singletons.getControl().setCurrentScreen(screen);
        }

        if (control.concede()) {
            //switch back to menus music when closing screen
            SoundSystem.instance.setBackgroundMusic(MusicPlaylist.MENUS);
            return true;
        }

        return false;
    }
}
