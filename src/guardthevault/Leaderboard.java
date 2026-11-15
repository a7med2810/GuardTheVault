package guardthevault;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;

public final class Leaderboard {

    private static final int LIMIT = 10;

    // Persistent local storage for this Mac user account.
    private static final Preferences STORAGE =
        Preferences.userRoot().node("guardthevault/leaderboard");

    private static final class Entry {
        private final String name;
        private final int score;

        private Entry(String name, int score) {
            this.name = name;
            this.score = score;
        }
    }

    private Leaderboard() {
    }

    private static List<Entry> load() {
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < LIMIT; i++) {
            String name = STORAGE.get("name" + i, "");
            int score = STORAGE.getInt("score" + i, 0);

            if (!name.isEmpty() && score > 0) {
                entries.add(new Entry(name, score));
            }
        }

        entries.sort(
            Comparator.comparingInt((Entry e) -> e.score).reversed()
        );

        return entries;
    }

    public static void submit(Component parent, int score) {
        if (score <= 0) {
            return;
        }

        try {
            List<Entry> entries = load();

            // When full, the score must beat the last entry.
            if (entries.size() >= LIMIT
                    && score <= entries.get(entries.size() - 1).score) {
                return;
            }

            String name = JOptionPane.showInputDialog(
                parent,
                "You earned " + score + " points!\n"
                + "Enter your name for the Top 10:",
                "HIGH SCORE",
                JOptionPane.PLAIN_MESSAGE
            );

            // Cancel skips registration.
            if (name == null) {
                return;
            }

            name = name.trim().replaceAll("\\s+", " ");

            if (name.isEmpty()) {
                name = "Player";
            }

            if (name.length() > 16) {
                name = name.substring(0, 16);
            }

            entries.add(new Entry(name, score));
            entries.sort(
                Comparator.comparingInt((Entry e) -> e.score).reversed()
            );

            while (entries.size() > LIMIT) {
                entries.remove(entries.size() - 1);
            }

            for (int i = 0; i < LIMIT; i++) {
                if (i < entries.size()) {
                    STORAGE.put("name" + i, entries.get(i).name);
                    STORAGE.putInt("score" + i, entries.get(i).score);
                } else {
                    STORAGE.remove("name" + i);
                    STORAGE.remove("score" + i);
                }
            }

            STORAGE.flush();
            show(parent);

        } catch (Exception error) {
            JOptionPane.showMessageDialog(
                parent,
                "Could not save rankings on this computer.\n"
                + error.getMessage(),
                "Leaderboard",
                JOptionPane.WARNING_MESSAGE
            );
        }
    }

    public static void show(Component parent) {
        try {
            List<Entry> entries = load();

            if (entries.isEmpty()) {
                JOptionPane.showMessageDialog(
                    parent,
                    "No scores yet. Complete a level to earn points!",
                    "TOP PLAYERS",
                    JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            String[] columns = {"Rank", "Player", "Score"};
            Object[][] rows = new Object[entries.size()][3];

            for (int i = 0; i < entries.size(); i++) {
                rows[i][0] = i + 1;
                rows[i][1] = entries.get(i).name;
                rows[i][2] = entries.get(i).score;
            }

            JTable table = new JTable(rows, columns) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            table.setRowHeight(28);
            table.setFillsViewportHeight(true);
            table.getTableHeader().setReorderingAllowed(false);

            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new java.awt.Dimension(420, 300));

            JOptionPane.showMessageDialog(
                parent, scroll, "TOP 10 PLAYERS",
                JOptionPane.PLAIN_MESSAGE
            );

        } catch (Exception error) {
            JOptionPane.showMessageDialog(
                parent,
                "Could not load rankings.",
                "Leaderboard",
                JOptionPane.WARNING_MESSAGE
            );
        }
    }
}