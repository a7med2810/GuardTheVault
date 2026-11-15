package guardthevault;

public class PauseAndSweep implements SweepStrategy {

    @Override
    public double positionAt(double seconds, double width) {
        double phase = seconds % 10.0;

        // Travel right to left.
        if (phase < 4.0) {
            return width * (1.0 - phase / 4.0);
        }

        // Pause at the left.
        if (phase < 5.0) {
            return 0;
        }

        // Return to the right.
        if (phase < 9.0) {
            return width * (phase - 5.0) / 4.0;
        }

        // Pause at the right.
        return width;
    }
}