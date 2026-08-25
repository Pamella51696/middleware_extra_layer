import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Heart of the layer. Builds two remap tables (mapX, mapY) ONCE per camera at
 * construction. Per-frame cost is a single Imgproc.remap call.
 *
 * Mapping direction: for every CYLINDRICAL output pixel we compute the source
 * FISHEYE pixel it samples from (backward map), which is exactly what remap needs.
 */
public class FisheyeCylindricalMapper {

    private final Mat mapX, mapY;   // pre-computed, reused every frame
    private final int outW, outH;

    public FisheyeCylindricalMapper(CameraConfig cfg, CameraConfig.Extrinsics ext) {
        this.outW = cfg.outputWidth;
        this.outH = cfg.outputHeight;
        this.mapX = new Mat(outH, outW, CvType.CV_32FC1);
        this.mapY = new Mat(outH, outW, CvType.CV_32FC1);

        // --- Fisheye intrinsics derived from FOV + image-circle geometry ---
        // These feeds are CIRCULAR fisheye: the imaged disc is centered and its
        // radius fills the shorter frame dimension (not the full sensor width).
        // So f must come from the image-circle RADIUS, not the sensor width:
        //   horizontal disc extent (2R) spans HFOV  ->  fx = R / (hfov/2)
        //   vertical   disc extent (2R) spans VFOV  ->  fy = R / (vfov/2)
        // fx != fy captures the anamorphic HFOV/VFOV difference on a round disc.
        double hfovRad = Math.toRadians(cfg.hfovDeg);
        double vfovRad = Math.toRadians(cfg.vfovDeg);
        double radius  = cfg.fisheyeRadiusPx > 0
                ? cfg.fisheyeRadiusPx
                : Math.min(cfg.inputWidth, cfg.inputHeight) / 2.0;
        double fx = radius / (hfovRad / 2.0);   // px per rad, horizontal
        double fy = radius / (vfovRad / 2.0);   // px per rad, vertical
        double cx = cfg.fisheyeCxPx > 0 ? cfg.fisheyeCxPx : cfg.inputWidth  / 2.0;
        double cy = cfg.fisheyeCyPx > 0 ? cfg.fisheyeCyPx : cfg.inputHeight / 2.0;

        // --- Cylindrical (output) intrinsics ---
        double fc  = cfg.cylindricalFocalPx;
        double cxC = outW / 2.0;
        double cyC = outH / 2.0;

        boolean equisolid = "equisolid".equalsIgnoreCase(cfg.fisheyeModel);

        // Rotation from mounting extrinsics (yaw/pitch/roll)
        double[][] R = eulerToRot(ext.yawDeg, ext.pitchDeg, ext.rollDeg);

        float[] rowX = new float[outW];
        float[] rowY = new float[outW];

        for (int v = 0; v < outH; v++) {
            for (int u = 0; u < outW; u++) {
                // 1. Cylindrical output pixel -> 3D ray on unit cylinder
                double theta = (u - cxC) / fc;   // horizontal angle around cylinder
                double h     = (v - cyC) / fc;   // vertical offset up the cylinder
                double X = Math.sin(theta);
                double Y = h;
                double Z = Math.cos(theta);

                // 2. Apply camera mounting rotation
                double[] p = rotate(R, X, Y, Z);
                X = p[0]; Y = p[1]; Z = p[2];

                double r3d = Math.sqrt(X * X + Y * Y + Z * Z);

                // Reject rays pointing behind the lens (avoids fold-over artifacts)
                if (Z <= 0) {
                    rowX[u] = -1f;
                    rowY[u] = -1f;
                    continue;
                }

                // 3. Project 3D ray -> fisheye pixel
                double thetaA = Math.acos(Z / r3d);   // angle from optical axis
                double phi    = Math.atan2(Y, X);

                double rPx;
                if (equisolid) {
                    rPx = 2.0 * fx * Math.sin(thetaA / 2.0);   // r = 2f*sin(theta/2)
                } else {
                    rPx = fx * thetaA;                         // r = f*theta (equidistant)
                }

                double uF = cx + rPx * Math.cos(phi);
                double vF = cy + rPx * Math.sin(phi) * (fy / fx); // aspect scale

                rowX[u] = (float) uF;
                rowY[u] = (float) vF;
            }
            mapX.put(v, 0, rowX);
            mapY.put(v, 0, rowY);
        }
    }

    /** One remap per frame. dst must be pre-allocated (outH x outW, CV_8UC3). */
    public void undistort(Mat src, Mat dst) {
        Imgproc.remap(src, dst, mapX, mapY,
                Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar.all(0));
    }

    public int outputWidth()  { return outW; }
    public int outputHeight() { return outH; }

    // yaw (Y) * pitch (X) * roll (Z) intrinsic rotation.
    private static double[][] eulerToRot(double yawDeg, double pitchDeg, double rollDeg) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg), r = Math.toRadians(rollDeg);
        double cy = Math.cos(y), sy = Math.sin(y);
        double cp = Math.cos(p), sp = Math.sin(p);
        double cr = Math.cos(r), sr = Math.sin(r);
        return new double[][]{
            { cy * cp,  cy * sp * sr - sy * cr,  cy * sp * cr + sy * sr },
            { sy * cp,  sy * sp * sr + cy * cr,  sy * sp * cr - cy * sr },
            { -sp,      cp * sr,                 cp * cr                }
        };
    }

    private static double[] rotate(double[][] R, double x, double y, double z) {
        return new double[]{
            R[0][0] * x + R[0][1] * y + R[0][2] * z,
            R[1][0] * x + R[1][1] * y + R[1][2] * z,
            R[2][0] * x + R[2][1] * y + R[2][2] * z
        };
    }
}
