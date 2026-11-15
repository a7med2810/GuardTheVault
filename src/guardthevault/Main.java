package guardthevault;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Creates the main window and menu.
 * Selects the security sweep Strategy used by GamePanel.
 */
public class Main {

    private static JFrame window;
    private static JPanel menu;
    private static JButton start;
    private static JComboBox<String> sweepChoice;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createWindow);
    }

    private static void createWindow() {
        boolean connected = ArduinoInput.INSTANCE.connect();

        window = new JFrame("Guard the Vault");
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setResizable(true);
        window.setMinimumSize(new Dimension(700, 520));

        menu = new JPanel(new GridBagLayout());
        menu.setBackground(new Color(16, 19, 25));
        menu.setPreferredSize(new Dimension(900, 650));

        GridBagConstraints layout = new GridBagConstraints();
        layout.gridx = 0;
        layout.fill = GridBagConstraints.HORIZONTAL;
        layout.insets = new Insets(10, 20, 10, 20);

        JLabel title = new JLabel(
                "GUARD THE VAULT",
                SwingConstants.CENTER);

        title.setFont(new Font("SansSerif", Font.BOLD, 42));
        title.setForeground(new Color(240, 185, 65));

        layout.gridy = 0;
        menu.add(title, layout);

        JLabel subtitle = new JLabel(
                "Hide from security. Crack the vault. Climb the rankings.",
                SwingConstants.CENTER);

        subtitle.setForeground(Color.LIGHT_GRAY);

        layout.gridy = 1;
        menu.add(subtitle, layout);

        sweepChoice = new JComboBox<>(new String[] {
                "Steady sweep",
                "Pause and sweep"
        });

        sweepChoice.setFont(
                new Font("SansSerif", Font.PLAIN, 17));

        layout.gridy = 2;
        menu.add(sweepChoice, layout);

        start = createButton("START");
        JButton instructions = createButton("INSTRUCTIONS");
        JButton rankings = createButton("TOP PLAYERS");
        JButton exit = createButton("EXIT");

        layout.gridy = 3;
        menu.add(start, layout);

        layout.gridy = 4;
        menu.add(instructions, layout);

        layout.gridy = 5;
        menu.add(rankings, layout);

        layout.gridy = 6;
        menu.add(exit, layout);

        start.addActionListener(event -> startGame());

        instructions.addActionListener(event ->
                JOptionPane.showMessageDialog(
                        window,
                        "Move: Joystick or arrow keys\n"
                        + "Start / restart: Start button or Enter\n"
                        + "Crack vault: Hold Action or Space for 2 seconds\n"
                        + "Return to menu: Escape\n\n"
                        + "Stand fully inside a green zone and stop\n"
                        + "moving to hide immediately.\n"
                        + "Crates block movement, not the security sweep.\n\n"
                        + "Level 1: 2 obstacles, 3 strikes, 60 seconds\n"
                        + "Level 2: 3 obstacles, 2 strikes, 50 seconds\n"
                        + "Level 3: 4 obstacles, 1 strike, 40 seconds\n\n"
                        + "Completed-level score:\n"
                        + "1000 + remaining seconds × 20 − strikes × 150\n"
                        + "Minimum completed-level score: 100\n\n"
                        + "RGB LED:\n"
                        + "Blue = playing\n"
                        + "Green = hidden\n"
                        + "Red = received a strike\n\n"
                        + "Use the mouse for dialogs and the keyboard\n"
                        + "to enter your leaderboard name.",
                        "HOW TO PLAY",
                        JOptionPane.INFORMATION_MESSAGE));

        rankings.addActionListener(
                event -> Leaderboard.show(window));

        exit.addActionListener(event -> window.dispose());

        // Start button works from the menu without flooding Swing events.
        Timer menuTimer = new Timer(30, null);

        menuTimer.addActionListener(new ActionListener() {
            private boolean previousStart;

            @Override
            public void actionPerformed(ActionEvent event) {
                ArduinoInput.Reading reading =
                        ArduinoInput.INSTANCE.read();

                boolean pressed = reading != null && reading.start;
                boolean newPress = pressed && !previousStart;
                previousStart = pressed;

                boolean mainWindowActive =
                        KeyboardFocusManager
                                .getCurrentKeyboardFocusManager()
                                .getActiveWindow() == window;

                if (newPress
                        && window.getContentPane() == menu
                        && mainWindowActive) {
                    start.doClick(0);
                }
            }
        });

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                menuTimer.stop();

                // Removing GamePanel stops its game timer.
                window.setContentPane(new JPanel());
                ArduinoInput.INSTANCE.close();
            }
        });

        showMenu();

        // Size only once. Changing screens must not shrink the window.
        window.pack();
        window.setLocationRelativeTo(null);
        window.setExtendedState(JFrame.MAXIMIZED_BOTH);
        window.setVisible(true);

        menuTimer.start();

        if (!connected) {
            JOptionPane.showMessageDialog(
                    window,
                    "Arduino could not connect.\n"
                    + "Close Serial Monitor and SerialTest.\n"
                    + "Check the port in ArduinoInput.java.\n\n"
                    + "Keyboard controls are still available.");
        }
    }

    private static void startGame() {
        // Strategy Pattern: choose one interchangeable sweep behaviour.
        SweepStrategy strategy =
                sweepChoice.getSelectedIndex() == 0
                        ? new SteadySweep()
                        : new PauseAndSweep();

        GamePanel game =
                new GamePanel(strategy, Main::showMenu);

        window.getRootPane().setDefaultButton(null);
        window.setContentPane(game);
        window.revalidate();
        window.repaint();

        SwingUtilities.invokeLater(game::requestFocusInWindow);
    }

    private static void showMenu() {
        window.setContentPane(menu);
        window.getRootPane().setDefaultButton(start);

        ArduinoInput.INSTANCE.setColour('O');

        window.revalidate();
        window.repaint();

        SwingUtilities.invokeLater(start::requestFocusInWindow);
    }

    private static JButton createButton(String text) {
        JButton button = new JButton(text);

        button.setFont(
                new Font("SansSerif", Font.BOLD, 19));

        button.setPreferredSize(new Dimension(440, 44));
        button.setFocusPainted(false);

        return button;
    }
}