import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads cameras.json into a CameraConfig using org.json.
 * The single tuning surface for the whole undistortion layer.
 */
public class UndistortionConfigLoader {

    public static CameraConfig load(String path) throws IOException {
        String text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        JSONObject j = new JSONObject(text);

        CameraConfig c = new CameraConfig();
        c.inputWidth         = j.getInt("input_width");
        c.inputHeight        = j.getInt("input_height");
        c.hfovDeg            = j.getDouble("hfov_deg");
        c.vfovDeg            = j.getDouble("vfov_deg");
        c.fisheyeModel       = j.optString("fisheye_model", "equidistant");
        c.outputWidth        = j.getInt("output_width");
        c.outputHeight       = j.getInt("output_height");
        c.cylindricalFocalPx = j.getDouble("cylindrical_focal_px");
        c.fisheyeRadiusPx    = j.optDouble("fisheye_radius_px", 0);
        c.fisheyeCxPx        = j.optDouble("fisheye_cx_px", 0);
        c.fisheyeCyPx        = j.optDouble("fisheye_cy_px", 0);

        c.cameras = new LinkedHashMap<>();
        JSONObject cams = j.getJSONObject("cameras");
        for (String id : cams.keySet()) {
            JSONObject e = cams.getJSONObject(id);
            c.cameras.put(id, new CameraConfig.Extrinsics(
                    e.getDouble("yaw_deg"),
                    e.getDouble("pitch_deg"),
                    e.getDouble("roll_deg")));
        }

        if (c.cameras.isEmpty()) {
            throw new IOException("cameras.json defines no cameras");
        }
        return c;
    }

    private UndistortionConfigLoader() {}
}
