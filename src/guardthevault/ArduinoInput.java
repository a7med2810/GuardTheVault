package guardthevault;

import java.util.Scanner;
import com.fazecast.jSerialComm.SerialPort;

public final class ArduinoInput {

    public static final ArduinoInput INSTANCE = new ArduinoInput();

    private static final String PORT =
            "/dev/cu.usbserial-BG00H1WW";

    public static final class Reading {
        public final int x;
        public final int y;
        public final boolean start;
        public final boolean action;
        private final long receivedAt;

        private Reading(int x, int y,
                        boolean start, boolean action) {
            this.x = x;
            this.y = y;
            this.start = start;
            this.action = action;
            receivedAt = System.nanoTime();
        }
    }

    private volatile Reading latest;
    private SerialPort port;

    private ArduinoInput() {
    }

    public synchronized boolean connect() {
        if (port != null && port.isOpen()) {
            return true;
        }

        latest = null;

        SerialPort opened = SerialPort.getCommPort(PORT);

        opened.setComPortParameters(
                9600,
                8,
                SerialPort.ONE_STOP_BIT,
                SerialPort.NO_PARITY);

        opened.setFlowControl(
                SerialPort.FLOW_CONTROL_DISABLED);

        // Avoid the buffering delay experienced with full blocking.
        opened.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                0,
                0);

        if (!opened.openPort()) {
            return false;
        }

        port = opened;

        Thread reader = new Thread(() -> {
            try (Scanner scanner = new Scanner(
                    opened.getInputStream(), "US-ASCII")) {

                while (opened.isOpen() && scanner.hasNextLine()) {
                    String[] fields =
                            scanner.nextLine().trim().split(",");

                    // Arduino sends: I,x,y,start,action
                    if (fields.length != 5
                            || !"I".equals(fields[0])) {
                        continue;
                    }

                    try {
                        int x = Integer.parseInt(fields[1]);
                        int y = Integer.parseInt(fields[2]);
                        int start = Integer.parseInt(fields[3]);
                        int action = Integer.parseInt(fields[4]);

                        if (x >= 0 && x <= 1023
                                && y >= 0 && y <= 1023
                                && start >= 0 && start <= 1
                                && action >= 0 && action <= 1) {

                            latest = new Reading(
                                    x,
                                    y,
                                    start == 1,
                                    action == 1);
                        }

                    } catch (NumberFormatException ignored) {
                        // Ignore incomplete or invalid messages.
                    }
                }

            } catch (Exception ignored) {
                // Allow the game to continue if Arduino disconnects.
            } finally {
                opened.closePort();
            }
        }, "ArduinoGameReader");

        reader.setDaemon(true);
        reader.start();

        return true;
    }

    public Reading read() {
        Reading reading = latest;

        // Release controls when input becomes stale.
        if (reading == null
                || System.nanoTime() - reading.receivedAt
                        > 500_000_000L) {
            return null;
        }

        return reading;
    }

    /**
     * Sends an RGB command:
     * R = red, G = green, B = blue, O = off.
     */
    public synchronized void setColour(char colour) {
        if (colour != 'R' && colour != 'G'
                && colour != 'B' && colour != 'O') {
            return;
        }

        if (port == null || !port.isOpen()) {
            return;
        }

        byte[] message = { (byte) colour };
        port.writeBytes(message, message.length);
    }

    public synchronized void close() {
        if (port != null && port.isOpen()) {
            setColour('O');
            port.closePort();
        }

        latest = null;
    }
}