# Fisheye → Cylindrical Undistortion Layer

Drop-in undistortion stage for `VideoStreamingServer`. Ingests raw fisheye `Mat`
frames, undistorts each to a cylindrical projection, then feeds the existing
`featherStitch` blender — unchanged.

## Files (all flat, no package — matches the repo)
- `CameraConfig.java`             — config POJO
- `UndistortionConfigLoader.java` — JSON loader (org.json)
- `FisheyeCylindricalMapper.java` — builds mapX/mapY remap tables once per camera
- `UndistortionLayer.java`        — public API: `undistort(Mat raw, String camId)`
- `cameras.json`                  — the single tuning surface
- `VideoStreamingServer.java`     — integrated (undistort inserted before resize)

## Build — one classpath change
The loader uses **org.json**, so add its jar to the classpath. In the `Makefile`:

```make
JSON_JAR := /path/to/json-20240303.jar        # https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar

# then append it to CP:
CP := .:$(OPENCV_JAR):$(JSON_JAR)             # Linux/Mac
CP := .;$(OPENCV_JAR);$(JSON_JAR)             # Windows
```

`make all` compiles the new classes automatically (they build with `%.class: %.java`).
Copy `cameras.json` next to the `.class` files (it's read from the working dir).

## How it's wired
In `main`, after `System.loadLibrary(...)`:
```java
UndistortionLayer undistortionLayer =
    new UndistortionLayer("cameras.json", /*debugPreview=*/true, ".");
```
Camera-id mapping matches the existing `videos[]` order `{right, rear, left, front}`:
```java
String[] camIds = { "right", "rear", "left", "front" };
```
Inside the frame loop (per camera `i`):
```java
Mat undistorted = undistortionLayer.undistort(frames[i], camIds[i]);
Imgproc.resize(undistorted, resized[i], new Size(TARGET_WIDTH, TARGET_HEIGHT));
```

## Debug preview (Phase 2)
With `debugPreview=true`, the first undistorted frame per camera is written to
`preview_<id>.jpg` in the given dir. Set the flag to `false` in
`VideoStreamingServer.UNDISTORT_PREVIEW` for production.

## Tuning (edit only `cameras.json`)
| To change… | Edit |
|---|---|
| Add a camera (same optics) | add a `{ yaw_deg:0, pitch_deg, roll_deg }` entry |
| Different FOV | `hfov_deg` / `vfov_deg` |
| Wrong image-circle size/center | `fisheye_radius_px`, `fisheye_cx_px`, `fisheye_cy_px` |
| Wider/narrower output | `cylindrical_focal_px`, `output_width`, `output_height` |
| Fix a seam | nudge that camera's `yaw_deg` by ±2° |
| Correct mounting tilt | `pitch_deg` (these feeds use -8°) |
| Switch model | `"fisheye_model": "equisolid"` (mapper already supports it) |

### Optics notes (calibrated against the supplied feeds)
- Feeds are **1280×720 circular fisheye**: the imaged disc is centered and its
  radius (~360 px) fills the frame **height**, with black bars left/right.
  `f` is therefore derived from `fisheye_radius_px`, **not** the sensor width.
- **`yaw_deg` is 0 for all four cameras.** `featherStitch` composites tiles by
  fixed left→right order + overlap, so each fisheye is undistorted about its own
  optical axis. `yaw` is left only as a small ±2° seam-alignment knob — do **not**
  set it to 90/180/-90 for this stitcher (that rolls the whole tile).

## Verified
Compiled against OpenCV 4.9.0 + org.json 20240303 (JDK 17). Ran the layer on the
**actual** front/right/left/rear feeds: fisheye barrel distortion removed
(buildings upright, poles vertical, curbs/lane-lines straight), vignette gone,
full 1280×720 edge-to-edge fill, previews dumped, unknown-camera guard throws.
