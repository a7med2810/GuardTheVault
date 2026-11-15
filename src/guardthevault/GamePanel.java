package guardthevault;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;

/**
 * Three-level game with Arduino controls, RGB feedback,
 * scoring, hiding zones and scalable graphics.
 *
 * All game logic uses a fixed 900 × 650 coordinate system.
 * Only rendering is scaled, so resizing does not change collisions.
 */
public class GamePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int WIDTH = 900;
    private static final int HEIGHT = 650;
    private static final double SPEED = 210;
    private static final double CRACK_SECONDS = 2.0;

    // Keep your previously working direction settings here.
    private static final boolean REVERSE_X = true;
    private static final boolean REVERSE_Y = true;

    private final SweepStrategy strategy;
    private final Runnable returnToMenu;
    private final Set<String> keys = new HashSet<>();

    private final Rectangle2D.Double player =
            new Rectangle2D.Double(60, 540, 24, 24);

    private final Rectangle vault =
            new Rectangle(780, 85, 76, 88);

    private Rectangle[] obstacles;
    private Rectangle[] hidingZones;

    private final Timer timer;
    private long lastTick;

    private int level;
    private int strikes;
    private int totalScore;
    private int runVersion;

    private double elapsed;
    private double sweepTime;
    private double sweepX;
    private double crackProgress;

    private boolean hidden;
    private boolean wasExposed;
    private boolean finished;
    private boolean dialogPending;
    private boolean previousArduinoStart;

    private String result = "";

    private char lastLedColour = '\0';
    private long redUntil;
    private long lastLedSend;

    public GamePanel(
            SweepStrategy strategy,
            Runnable returnToMenu) {

        this.strategy = strategy;
        this.returnToMenu = returnToMenu;

        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(12, 15, 21));
        setFocusable(true);

        bindHeldKey("LEFT");
        bindHeldKey("RIGHT");
        bindHeldKey("UP");
        bindHeldKey("DOWN");
        bindHeldKey("SPACE");

        bindPress("ENTER", () -> {
            if (finished && !dialogPending) {
                resetRound();
            }
        });

        bindPress("ESCAPE", () -> {
            if (!dialogPending) {
                runVersion++;
                stopTimer();
                returnToMenu.run();
            }
        });

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                keys.clear();
            }
        });

        timer = new Timer(16, event -> tick());
        resetRound();
    }

    private void bindHeldKey(String key) {
        InputMap input = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = getActionMap();

        input.put(
                KeyStroke.getKeyStroke("pressed " + key),
                key + "_ON");

        input.put(
                KeyStroke.getKeyStroke("released " + key),
                key + "_OFF");

        actions.put(key + "_ON", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                keys.add(key);
            }
        });

        actions.put(key + "_OFF", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                keys.remove(key);
            }
        });
    }

    private void bindPress(String key, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("pressed " + key),
                key + "_PRESS");

        getActionMap().put(key + "_PRESS", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void stopTimer() {
        timer.stop();
    }

    @Override
    public void addNotify() {
        super.addNotify();

        lastTick = System.nanoTime();
        lastLedColour = '\0';
        timer.start();

        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    @Override
    public void removeNotify() {
        timer.stop();
        keys.clear();

        ArduinoInput.INSTANCE.setColour('O');
        lastLedColour = '\0';

        super.removeNotify();
    }

    private void resetRound() {
        runVersion++;

        level = 1;
        totalScore = 0;
        finished = false;
        dialogPending = false;
        result = "";
        redUntil = 0;
        lastLedColour = '\0';

        loadLevel();
    }

    private void loadLevel() {
        strikes = 0;
        elapsed = 0;
        sweepTime = 0;
        crackProgress = 0;
        hidden = false;
        wasExposed = false;
        redUntil = 0;

        player.setRect(60, 540, 24, 24);
        keys.clear();

        if (level == 1) {
            obstacles = new Rectangle[] {
                    new Rectangle(220, 370, 120, 80),
                    new Rectangle(570, 230, 120, 80)
            };

            hidingZones = new Rectangle[] {
                    new Rectangle(230, 465, 100, 65),
                    new Rectangle(580, 325, 100, 65)
            };

        } else if (level == 2) {
            obstacles = new Rectangle[] {
                    new Rectangle(180, 390, 120, 70),
                    new Rectangle(380, 220, 90, 160),
                    new Rectangle(600, 400, 130, 70)
            };

            hidingZones = new Rectangle[] {
                    new Rectangle(190, 475, 100, 65),
                    new Rectangle(485, 255, 75, 100),
                    new Rectangle(615, 485, 100, 65)
            };

        } else {
            obstacles = new Rectangle[] {
                    new Rectangle(160, 420, 110, 80),
                    new Rectangle(350, 280, 100, 140),
                    new Rectangle(540, 410, 120, 75),
                    new Rectangle(620, 190, 110, 100)
            };

            hidingZones = new Rectangle[] {
                    new Rectangle(175, 515, 80, 65),
                    new Rectangle(365, 435, 75, 65),
                    new Rectangle(555, 500, 90, 65),
                    new Rectangle(635, 305, 80, 65)
            };
        }

        sweepX = sweepPosition(0);
        lastTick = System.nanoTime();

        updateLed();
        repaint();
    }

    private int maximumStrikes() {
        return 4 - level;
    }

    private double timeLimit() {
        return 70 - level * 10;
    }

    private int secondsLeft() {
        return Math.max(
                0,
                (int) Math.ceil(timeLimit() - elapsed));
    }

    private int levelPoints() {
        return Math.max(
                100,
                1000 + secondsLeft() * 20 - strikes * 150);
    }

    private double sweepPosition(double time) {
        double multiplier = 1.0 + (level - 1) * 0.3;

        return 25 + strategy.positionAt(time * multiplier, 850);
    }

    private double joystickAxis(int value, int centre) {
        int difference = value - centre;

        // Dead zone prevents movement from small centre fluctuations.
        if (Math.abs(difference) < 200) {
            return 0;
        }

        return difference > 0 ? 1 : -1;
    }

    private void updateLed() {
        long now = System.nanoTime();
        char colour;

        if (now < redUntil) {
            colour = 'R';
        } else if (finished || dialogPending) {
            colour = 'O';
        } else {
            colour = hidden ? 'G' : 'B';
        }

        // Refresh periodically in case Arduino reset after connection.
        if (colour != lastLedColour
                || now - lastLedSend >= 1_000_000_000L) {

            ArduinoInput.INSTANCE.setColour(colour);
            lastLedColour = colour;
            lastLedSend = now;
        }
    }

    private void tick() {
        long now = System.nanoTime();

        double dt = Math.min(
                0.05,
                (now - lastTick) / 1_000_000_000.0);

        lastTick = now;

        updateGame(dt);
        updateLed();
        repaint();
    }

    private void updateGame(double dt) {
        ArduinoInput.Reading controls =
                ArduinoInput.INSTANCE.read();

        boolean startPressed =
                controls != null && controls.start;

        boolean newStartPress =
                startPressed && !previousArduinoStart;

        previousArduinoStart = startPressed;

        if (dialogPending) {
            return;
        }

        if (finished) {
            if (newStartPress) {
                resetRound();
            }
            return;
        }

        double dx = 0;
        double dy = 0;

        if (keys.contains("LEFT")) dx--;
        if (keys.contains("RIGHT")) dx++;
        if (keys.contains("UP")) dy--;
        if (keys.contains("DOWN")) dy++;

        if (controls != null) {
            double jx = joystickAxis(controls.x, 503);
            double jy = joystickAxis(controls.y, 515);

            if (REVERSE_X) jx = -jx;
            if (REVERSE_Y) jy = -jy;

            dx = Math.max(-1, Math.min(1, dx + jx));
            dy = Math.max(-1, Math.min(1, dy + jy));
        }

        boolean movementRequested = dx != 0 || dy != 0;
        double length = Math.hypot(dx, dy);

        if (length > 0) {
            movePlayer(
                    dx / length * SPEED * dt,
                    dy / length * SPEED * dt);
        }

        // Entire player must be inside a hiding zone and stationary.
        hidden = false;

        if (!movementRequested) {
            for (Rectangle zone : hidingZones) {
                if (zone.contains(player)) {
                    hidden = true;
                    break;
                }
            }
        }

        elapsed += dt;

        double previousSweepX = sweepX;
        sweepTime += dt;
        sweepX = sweepPosition(sweepTime);

        Rectangle2D crossedArea = new Rectangle2D.Double(
                Math.min(previousSweepX, sweepX) - 10,
                75,
                Math.abs(sweepX - previousSweepX) + 20,
                520);

        boolean exposed =
                !hidden && crossedArea.intersects(player);

        if (exposed && !wasExposed) {
            strikes++;
            crackProgress = 0;

            redUntil = System.nanoTime() + 800_000_000L;

            if (strikes >= maximumStrikes()) {
                finishRun("GAME OVER — Security caught you!");
                return;
            }
        }

        wasExposed = exposed;

        boolean actionHeld = keys.contains("SPACE")
                || (controls != null && controls.action);

        if (nearVault()
                && actionHeld
                && !movementRequested
                && !exposed) {

            crackProgress += dt;

        } else {
            crackProgress = Math.max(
                    0,
                    crackProgress - dt * 0.35);
        }

        if (elapsed >= timeLimit()) {
            finishRun("GAME OVER — Time ran out!");
            return;
        }

        if (crackProgress >= CRACK_SECONDS) {
            completeLevel();
        }
    }

    private void movePlayer(double dx, double dy) {
        double oldX = player.x;

        player.x = Math.max(
                25,
                Math.min(875 - player.width, player.x + dx));

        if (blocked()) {
            player.x = oldX;
        }

        double oldY = player.y;

        player.y = Math.max(
                75,
                Math.min(595 - player.height, player.y + dy));

        if (blocked()) {
            player.y = oldY;
        }
    }

    private boolean blocked() {
        if (player.intersects(vault)) {
            return true;
        }

        for (Rectangle obstacle : obstacles) {
            if (player.intersects(obstacle)) {
                return true;
            }
        }

        return false;
    }

    private boolean nearVault() {
        Rectangle area = new Rectangle(vault);
        area.grow(32, 32);

        return area.intersects(player);
    }

    private void completeLevel() {
        int earned = levelPoints();
        totalScore += earned;

        dialogPending = true;
        keys.clear();

        updateLed();
        repaint();

        int version = runVersion;

        SwingUtilities.invokeLater(() -> {
            if (version != runVersion || !isDisplayable()) {
                return;
            }

            String breakdown =
                    "Level " + level + " completed!\n\n"
                    + "Base points: 1000\n"
                    + "Time bonus: " + secondsLeft() + " × 20\n"
                    + "Strike penalty: " + strikes + " × 150\n"
                    + "Level points: " + earned + "\n"
                    + "Total score: " + totalScore;

            if (level < 3) {
                int answer = JOptionPane.showConfirmDialog(
                        this,
                        breakdown
                        + "\n\nContinue to level " + (level + 1) + "?",
                        "VAULT CRACKED",
                        JOptionPane.YES_NO_OPTION);

                if (answer == JOptionPane.YES_OPTION) {
                    level++;
                    dialogPending = false;
                    loadLevel();
                    requestFocusInWindow();

                } else {
                    dialogPending = false;
                    finishRun("RUN COMPLETE");
                }

            } else {
                JOptionPane.showMessageDialog(
                        this,
                        breakdown,
                        "ALL VAULTS CRACKED!",
                        JOptionPane.INFORMATION_MESSAGE);

                dialogPending = false;
                finishRun("YOU WIN — All three vaults cracked!");
            }
        });
    }

    private void finishRun(String message) {
        if (finished) {
            return;
        }

        finished = true;
        result = message;
        dialogPending = true;
        keys.clear();

        updateLed();
        repaint();

        int version = runVersion;

        SwingUtilities.invokeLater(() -> {
            if (version != runVersion || !isDisplayable()) {
                return;
            }

            JOptionPane.showMessageDialog(
                    this,
                    message + "\n\nFinal score: " + totalScore,
                    "GUARD THE VAULT",
                    JOptionPane.INFORMATION_MESSAGE);

            if (totalScore > 0) {
                Leaderboard.submit(this, totalScore);
            }

            dialogPending = false;
            lastTick = System.nanoTime();

            requestFocusInWindow();
            updateLed();
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill the real panel, including any side borders.
        g.setColor(new Color(12, 15, 21));
        g.fillRect(0, 0, getWidth(), getHeight());

        // Enlarge and centre the game while preserving proportions.
        double scale = Math.min(
                getWidth() / (double) WIDTH,
                getHeight() / (double) HEIGHT);

        if (scale <= 0) {
            g.dispose();
            return;
        }

        double offsetX = (getWidth() - WIDTH * scale) / 2.0;
        double offsetY = (getHeight() - HEIGHT * scale) / 2.0;

        g.translate(offsetX, offsetY);
        g.scale(scale, scale);
        g.clipRect(0, 0, WIDTH, HEIGHT);

        drawGame(g);
        g.dispose();
    }

    private void drawGame(Graphics2D g) {
        Color[] floors = {
                new Color(25, 32, 42),
                new Color(31, 28, 43),
                new Color(42, 25, 29)
        };

        g.setColor(new Color(12, 15, 21));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setColor(floors[level - 1]);
        g.fillRoundRect(20, 70, 860, 530, 12, 12);

        // Floor tiles.
        g.setColor(new Color(255, 255, 255, 12));

        for (int x = 25; x < 880; x += 40) {
            g.drawLine(x, 75, x, 595);
        }

        for (int y = 75; y < 600; y += 40) {
            g.drawLine(25, y, 875, y);
        }

        // Hiding zones.
        for (Rectangle zone : hidingZones) {
            g.setColor(new Color(32, 140, 91, 85));

            g.fillRoundRect(
                    zone.x, zone.y,
                    zone.width, zone.height, 12, 12);

            g.setColor(new Color(75, 225, 145));

            g.drawRoundRect(
                    zone.x, zone.y,
                    zone.width, zone.height, 12, 12);

            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.drawString("HIDE", zone.x + 12, zone.y + 18);
        }

        // Crates.
        for (Rectangle obstacle : obstacles) {
            g.setColor(new Color(0, 0, 0, 90));

            g.fillRoundRect(
                    obstacle.x + 5,
                    obstacle.y + 6,
                    obstacle.width,
                    obstacle.height,
                    8, 8);

            g.setPaint(new GradientPaint(
                    obstacle.x,
                    obstacle.y,
                    new Color(123, 101, 70),
                    obstacle.x,
                    obstacle.y + obstacle.height,
                    new Color(63, 57, 48)));

            g.fillRoundRect(
                    obstacle.x,
                    obstacle.y,
                    obstacle.width,
                    obstacle.height,
                    8, 8);

            g.setColor(new Color(193, 164, 108));

            g.drawRoundRect(
                    obstacle.x,
                    obstacle.y,
                    obstacle.width,
                    obstacle.height,
                    8, 8);

            g.drawRect(
                    obstacle.x + 8,
                    obstacle.y + 8,
                    obstacle.width - 16,
                    obstacle.height - 16);

            g.drawLine(
                    obstacle.x + 8,
                    obstacle.y + 8,
                    obstacle.x + obstacle.width - 8,
                    obstacle.y + obstacle.height - 8);
        }

        // Vault.
        g.setPaint(new GradientPaint(
                vault.x,
                vault.y,
                new Color(250, 204, 91),
                vault.x + vault.width,
                vault.y + vault.height,
                new Color(143, 92, 24)));

        g.fillRoundRect(
                vault.x, vault.y,
                vault.width, vault.height, 12, 12);

        g.setColor(new Color(55, 42, 25));
        g.setStroke(new BasicStroke(3));

        g.drawRoundRect(
                vault.x + 6,
                vault.y + 6,
                vault.width - 12,
                vault.height - 12,
                8, 8);

        g.drawOval(vault.x + 20, vault.y + 25, 36, 36);

        g.drawLine(
                vault.x + 38, vault.y + 25,
                vault.x + 38, vault.y + 61);

        g.drawLine(
                vault.x + 20, vault.y + 43,
                vault.x + 56, vault.y + 43);

        // Security sweep.
        int beam = (int) Math.round(sweepX);

        for (int width = 40; width >= 20; width -= 4) {
            g.setColor(new Color(255, 45, 65, 14));
            g.fillRect(beam - width / 2, 75, width, 520);
        }

        g.setColor(new Color(255, 50, 65, 60));
        g.fillRect(beam - 10, 75, 20, 520);

        g.setColor(new Color(255, 95, 105));
        g.setStroke(new BasicStroke(2));
        g.drawLine(beam, 75, beam, 595);

        // Player.
        int px = (int) player.x;
        int py = (int) player.y;

        g.setColor(new Color(0, 0, 0, 100));
        g.fillOval(px - 3, py + 17, 30, 12);

        g.setColor(hidden
                ? new Color(75, 225, 145)
                : new Color(75, 196, 230));

        g.fillOval(px, py, 24, 24);

        g.setColor(new Color(22, 32, 43));
        g.fillRoundRect(px + 3, py + 7, 18, 10, 5, 5);

        g.setColor(Color.WHITE);
        g.fillRect(px + 6, py + 10, 3, 3);
        g.fillRect(px + 15, py + 10, 3, 3);

        if (hidden) {
            g.setColor(new Color(75, 225, 145));
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.drawString("HIDDEN", px - 11, py - 8);
        }

        // HUD.
        g.setColor(new Color(240, 185, 65));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("GUARD THE VAULT", 25, 30);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));

        g.drawString("LEVEL " + level + " / 3", 310, 28);
        g.drawString("TIME " + secondsLeft(), 435, 28);

        g.drawString(
                "STRIKES " + strikes + " / " + maximumStrikes(),
                545, 28);

        g.drawString("SCORE " + totalScore, 730, 28);

        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));

        g.drawString(
                "Green zones: stop to hide immediately",
                25, 53);

        g.drawString("LEVEL PTS: " + levelPoints(), 545, 53);

        g.drawString(
                ArduinoInput.INSTANCE.read() != null
                        ? "ARDUINO CONNECTED"
                        : "KEYBOARD MODE",
                730, 53);

        // Footer.
        g.setColor(new Color(235, 235, 240));
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));

        if (nearVault() && !finished) {
            g.drawString(
                    "Hold ACTION / SPACE to crack the vault",
                    25, 625);

            g.setColor(new Color(35, 38, 45));
            g.fillRoundRect(610, 608, 260, 20, 8, 8);

            g.setColor(new Color(240, 185, 65));

            g.fillRoundRect(
                    610,
                    608,
                    (int) (260 * Math.min(
                            1, crackProgress / CRACK_SECONDS)),
                    20,
                    8, 8);

        } else {
            g.drawString(
                    "Joystick / arrows: move    "
                    + "ACTION / SPACE: crack vault    ESC: menu",
                    25, 625);
        }

        // Strike alert.
        if (!finished && System.nanoTime() < redUntil) {
            g.setColor(new Color(255, 45, 65, 100));
            g.setStroke(new BasicStroke(5));
            g.drawRoundRect(22, 72, 856, 526, 12, 12);
        }

        // End screen.
        if (finished) {
            g.setColor(new Color(0, 0, 0, 205));
            g.fillRect(20, 70, 860, 530);

            g.setColor(new Color(240, 185, 65));
            g.setFont(new Font("SansSerif", Font.BOLD, 25));
            drawCentered(g, result, 295);

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));

            drawCentered(
                    g,
                    "Final score: " + totalScore,
                    340);

            g.setFont(new Font("SansSerif", Font.PLAIN, 16));

            drawCentered(
                    g,
                    "START / ENTER: play again     ESC: menu",
                    385);
        }
    }

    private void drawCentered(Graphics2D g, String text, int y) {
        int textWidth = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (WIDTH - textWidth) / 2, y);
    }
}