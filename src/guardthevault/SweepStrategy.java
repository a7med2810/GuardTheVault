package guardthevault;

// Strategy interface: each implementation calculates sweep position.
public interface SweepStrategy {

    double positionAt(double seconds, double width);
}