import java.util.Map;

/**
 * Plain config POJO mirroring cameras.json.
 * Top-level block = shared optics / output geometry.
 * cameras map = per-camera mounting extrinsics (yaw/pitch/roll).
 */
public class CameraConfig {
    public int inputWidth, inputHeight;
    public double hfovDeg, vfovDeg;
    public String fisheyeModel;            // "equidistant" | "equisolid"
    public int outputWidth, outputHeight;
    public double cylindricalFocalPx;
    // Optional circular-fisheye geometry. 0 = auto:
    //   radius -> min(inputWidth,inputHeight)/2 ; center -> frame center.
    public double fisheyeRadiusPx;
    public double fisheyeCxPx, fisheyeCyPx;
    public Map<String, Extrinsics> cameras;

    public static class Extrinsics {
        public double yawDeg, pitchDeg, rollDeg;

        public Extrinsics() {}
        public Extrinsics(double yawDeg, double pitchDeg, double rollDeg) {
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
        }
    }
}
