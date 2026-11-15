package guardthevault;

public class SteadySweep implements SweepStrategy {

    @Override
    public double positionAt(double seconds, double width) {
        double phase = seconds % 8.0;

        // Begin on the right, travel left, then return.
        if (phase <= 4.0) {
            return width * (1.0 - phase / 4.0);
        }

        return width * (phase - 4.0) / 4.0;
    }
}