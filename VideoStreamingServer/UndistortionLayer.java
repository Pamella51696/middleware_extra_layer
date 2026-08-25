import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Public API of the undistortion stage.
 *
 * Build once (remap tables are computed per camera at construction), then call
 * undistort(rawFrame, cameraId) inside the per-frame loop.
 *
 * Thread-safety: each camera owns its own dst buffer, so undistort() for
 * DIFFERENT camera ids can run in parallel. Do NOT call undistort() for the
 * SAME id from two threads at once (the dst buffer is reused).
 */
public class UndistortionLayer {

    private final Map<String, FisheyeCylindricalMapper> mappers = new HashMap<>();
    private final Map<String, Mat> dstBuffers = new HashMap<>();

    // Phase 2 debug preview: dump the first undistorted frame per camera to disk.
    private final boolean debugPreview;
    private final String previewDir;
    private final Set<String> previewed = new HashSet<>();

    public UndistortionLayer(String configPath) throws IOException {
        this(configPath, false, ".");
    }

    public UndistortionLayer(String configPath, boolean debugPreview, String previewDir) throws IOException {
        this.debugPreview = debugPreview;
        this.previewDir = previewDir;

        CameraConfig cfg = UndistortionConfigLoader.load(configPath);
        for (Map.Entry<String, CameraConfig.Extrinsics> entry : cfg.cameras.entrySet()) {
            mappers.put(entry.getKey(), new FisheyeCylindricalMapper(cfg, entry.getValue()));
            dstBuffers.put(entry.getKey(),
                    new Mat(cfg.outputHeight, cfg.outputWidth, CvType.CV_8UC3));
        }
        System.out.println("[UndistortionLayer] ready for cameras " + mappers.keySet()
                + " -> " + cfg.outputWidth + "x" + cfg.outputHeight
                + " (" + cfg.fisheyeModel + (debugPreview ? ", preview ON" : "") + ")");
    }

    public boolean hasCamera(String cameraId) {
        return mappers.containsKey(cameraId);
    }

    /**
     * Undistort a raw fisheye frame to cylindrical projection.
     * Returns the internal per-camera buffer — do NOT free it. Copy if you store it.
     */
    public Mat undistort(Mat rawFrame, String cameraId) {
        FisheyeCylindricalMapper mapper = mappers.get(cameraId);
        if (mapper == null) {
            throw new IllegalArgumentException("Unknown cameraId: " + cameraId);
        }
        Mat dst = dstBuffers.get(cameraId);
        mapper.undistort(rawFrame, dst);

        if (debugPreview && previewed.add(cameraId)) {
            String file = previewDir + "/preview_" + cameraId + ".jpg";
            Imgcodecs.imwrite(file, dst);
            System.out.println("[UndistortionLayer] wrote debug preview " + file);
        }
        return dst;
    }
}
