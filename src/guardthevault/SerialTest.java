package guardthevault;

import java.awt.*;
import java.awt.event.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

import com.fazecast.jSerialComm.SerialPort;

public class SerialTest {

    private static final AtomicReference<Reading> latest =
        new AtomicReference<>();

    private static class Reading {
        final int x;
        final int y;
        final boolean start;
        final boolean action;
        final long receivedAt;

        Reading(int x, int y, boolean start, boolean action) {
            this.x = x;
            this.y = y;
            this.start = start;
            this.action = action;
            receivedAt = System.nanoTime();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SerialTest::openWindow);
    }

    private static void openWindow() {
        latest.set(null);

        SerialPort port = SerialPort.getCommPort(
            "/dev/cu.usbserial-BG00H1WW"
        );

        port.setComPortParameters(
            9600,
            8,
            SerialPort.ONE_STOP_BIT,
            SerialPort.NO_PARITY
        );

        port.setFlowControl(
            SerialPort.FLOW_CONTROL_DISABLED
        );

        // Return when data becomes available instead of waiting
        // for Scanner's entire requested buffer to fill.
        port.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
            0,
            0
        );

        if (!port.openPort()) {
            JOptionPane.showMessageDialog(
                null,
                "Cannot open Arduino.\n"
                + "Close Arduino Serial Monitor and other "
                + "SerialTest programs.",
                "Connection Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        JFrame window = new JFrame("Arduino Live Test");
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(
            new GridLayout(4, 1, 10, 10)
        );
        panel.setBorder(
            BorderFactory.createEmptyBorder(20, 25, 20, 25)
        );

        JLabel axes = new JLabel("Waiting for Arduino...");
        JLabel start = new JLabel("START: —");
        JLabel action = new JLabel("ACTION: —");
        JLabel status = new JLabel("Keep the board connected.");

        for (JLabel label : new JLabel[]{
                axes, start, action, status}) {
            label.setFont(
                new Font("SansSerif", Font.BOLD, 20)
            );
            panel.add(label);
        }

        window.setContentPane(panel);
        window.setSize(560, 300);
        window.setLocationRelativeTo(null);

        // Receive data separately from the Swing interface.
        Thread reader = new Thread(() -> {
            try (Scanner scanner = new Scanner(
                    port.getInputStream(), "US-ASCII")) {

                while (port.isOpen() && scanner.hasNextLine()) {
                    String[] fields = scanner.nextLine()
                        .trim()
                        .split(",");

                    if (fields.length != 5
                            || !fields[0].equals("I")) {
                        continue;
                    }

                    try {
                        int x = Integer.parseInt(fields[1]);
                        int y = Integer.parseInt(fields[2]);
                        int s = Integer.parseInt(fields[3]);
                        int a = Integer.parseInt(fields[4]);

                        if (x < 0 || x > 1023
                                || y < 0 || y > 1023
                                || s < 0 || s > 1
                                || a < 0 || a > 1) {
                            continue;
                        }

                        // Keep only the newest complete reading.
                        latest.set(
                            new Reading(x, y, s == 1, a == 1)
                        );

                    } catch (NumberFormatException ignored) {
                        // Ignore malformed messages.
                    }
                }

            } catch (Exception ignored) {
                // The display reports when readings become stale.
            } finally {
                port.closePort();
            }
        }, "ArduinoReader");

        reader.setDaemon(true);

        // Update the window without flooding Eclipse's Console.
        Timer display = new Timer(30, event -> {
            Reading reading = latest.get();

            if (reading == null) {
                return;
            }

            long ageMs = (
                System.nanoTime() - reading.receivedAt
            ) / 1_000_000;

            if (ageMs >= 500) {
                axes.setText("Waiting for fresh readings...");
                start.setText("START: —");
                action.setText("ACTION: —");
                start.setForeground(Color.DARK_GRAY);
                action.setForeground(Color.DARK_GRAY);
                status.setText("No recent data — check connection");
                return;
            }

            axes.setText(
                "X: " + reading.x + "     Y: " + reading.y
            );

            start.setText(
                "START: "
                + (reading.start ? "PRESSED" : "released")
            );

            action.setText(
                "ACTION: "
                + (reading.action ? "PRESSED" : "released")
            );

            start.setForeground(
                reading.start
                    ? new Color(0, 150, 70)
                    : Color.DARK_GRAY
            );

            action.setForeground(
                reading.action
                    ? new Color(0, 150, 70)
                    : Color.DARK_GRAY
            );

            status.setText("Receiving live data");
        });

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                display.stop();
                port.closePort();
            }
        });

        window.setVisible(true);
        reader.start();
        display.start();
    }
}