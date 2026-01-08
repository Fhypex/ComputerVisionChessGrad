# MediaPipe hand-detection integration (local server)

This project adds a small local MediaPipe-based hand detection service and a Java HTTP client to integrate hand presence checks into the realtime chess app.

Files added
- [python/mediapipe_server.py](python/mediapipe_server.py) — FastAPI server using MediaPipe Hands, exposes `POST /detect` (multipart image)
- [python/requirements.txt](python/requirements.txt) — Python dependencies
- [src/main/java/com/chessgame/HttpHandDetector.java](src/main/java/com/chessgame/HttpHandDetector.java) — Java client that sends camera frames to the local server
- Updated [src/main/java/com/chessgame/GamePlay.java](src/main/java/com/chessgame/GamePlay.java) — performs a hand check and skips processing when a hand is present
- [scripts/run_mediapipe.bat](scripts/run_mediapipe.bat) — convenience script to start the server on Windows

Quick setup (Windows)

1. Install Python 3.8+ and ensure `python` is on PATH.
2. From the project root, install Python deps and start the server (PowerShell):

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r python/requirements.txt
python python/mediapipe_server.py
```

Or use the convenience batch (may install packages globally):

```powershell
.\scripts\run_mediapipe.bat
```

Server behavior
- `POST /detect` accepts multipart form image file with key `image` and returns JSON: `{ "hand": true|false, "confidence": 0.0-1.0 }`.

Run the Java app

Ensure the MediaPipe server is running on `http://127.0.0.1:8000` before starting the Java app.
Run from project root:

```powershell
.\gradlew.bat run
```

What to expect during testing
- When `GamePlay` detects a hand in the captured frame it appends a message to the log and temporarily skips board-change processing.
- If you connect your phone as a webcam or IP camera, point the application at the camera and perform calibration as usual. Place a hand over the board to verify the app skips processing.

Notes & next steps
- Adjust `min_detection_confidence` in `python/mediapipe_server.py` or the confidence threshold used by `HttpHandDetector` to tune false positives/negatives.
- For production or lower latency, consider integrating MediaPipe C++ with JNI or using a local ONNX hand detector.
