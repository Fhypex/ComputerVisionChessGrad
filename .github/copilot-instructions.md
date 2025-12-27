## Purpose
Provide immediate, actionable context for AI coding agents working on this repository.

## Big picture (what this project does)
- JavaFX application that uses OpenCV + ONNX to detect chessboard state from a camera and track moves in realtime.
- High-level flow: camera capture -> board detection/calibration -> warp to standardized view -> per-square change detection -> piece classification (ONNX) -> game logic via tracker -> UI update.

## Major components & where to look
- UI / entry: [src/main/java/com/chessgame/GamePlay.java](src/main/java/com/chessgame/GamePlay.java) (JavaFX lifecycle, calibration, game loop, threading pattern).
- Board detection / calibration: [src/main/java/com/chessgame/BoardDetector.java](src/main/java/com/chessgame/BoardDetector.java)
- Core CV helpers: [src/main/java/com/chessgame/ChessMoveLogic.java](src/main/java/com/chessgame/ChessMoveLogic.java) (warping + change detection)
- Square extraction / preprocessing: [src/main/java/com/chessgame/ChessSquareExtractor.java](src/main/java/com/chessgame/ChessSquareExtractor.java)
- Classification model: [src/main/java/com/chessgame/PieceClassifier.java](src/main/java/com/chessgame/PieceClassifier.java) (uses `models/best.onnx`)
- Game rules / move validation: [src/main/java/com/chessgame/ChessGameTracker.java](src/main/java/com/chessgame/ChessGameTracker.java)
- Camera abstraction: [src/main/java/com/chessgame/CameraViewer.java](src/main/java/com/chessgame/CameraViewer.java)
- UI board rendering: [src/main/java/com/chessgame/ChessBoard.java](src/main/java/com/chessgame/ChessBoard.java)

## Important, discoverable behaviors and conventions
- The tracker API: `tracker.processChangedSquares(List<String>)` returns a move `String` on success or `null` for illegal/noisy detections. Calling code (see GamePlay) treats `null` as an illegal move and does NOT update the reference image.
- Calibration result stored as `Point[] boardCorners` and used for all subsequent warps: `ChessMoveLogic.warpBoardStandardized(frame, boardCorners)`.
- UI thread rules: long-running CV work runs off the JavaFX thread; any UI updates use `Platform.runLater(...)` (see GamePlay.startCalibrationSequence and the game loop).
- Game loop uses a single-threaded ScheduledExecutorService (periodic polling every ~1s). Avoid blocking that thread for long operations.

## Build / run / common tasks
- Build and run (Windows):

  .\gradlew.bat clean build --refresh-dependencies
  .\gradlew.bat run

- Other useful Gradle tasks (declared in [build.gradle](build.gradle)):
  - `runTestYolo` → runs `com.chessgame.ChessBoardDebug`
  - `runTestDetect` → runs `com.chessgame.MoveDetector`
  - `runDetectTest` → runs `com.chessgame.ChessMoveDetector`
  - `detectBoard` → runs `com.chessgame.BoardDetect2`
  - `runtimePipeline` → runs `com.chessgame.ChessMoveDetectorTest`

- Native libs: Gradle adds `-Djava.library.path=${buildDir}/libs` for JavaExec tasks. Ensure native ONNX/OpenCV libs are available in `build/libs` if you run tasks that need them.

## Debugging tips / hotspots
- If camera frames are empty or OpenCV types fail, inspect `CameraViewer.java` and confirm native OpenCV binaries are present.
- If classification seems wrong, check `models/best.onnx` (repo root `models/`) and `PieceClassifier.java` input preprocessing in `ChessSquareExtractor.java`.
- For board detection and manual adjustments, see `BoardDetector.pickCornersManually(...)` which opens a blocking JavaFX dialog—note threading interactions in `GamePlay`.

## Project layout & resources
- Source: [src/main/java/com/chessgame/](src/main/java/com/chessgame/)
- Resources (piece images, test images): [src/main/resources/](src/main/resources/)
- Trained model: [models/best.onnx](models/best.onnx)
- Output/debug data: `output/` (contains `squares/`, `special_debug/`)

## Code change conventions for agents
- Keep code inside `package com.chessgame`. Avoid renaming packages or moving public APIs without a test/update.
- Follow existing naming patterns: CV helpers end with `*Logic`/`*Detector`; UI classes are `*Board` or `*Viewer`.
- Preserve threading semantics: long-running CV work off the FX thread; UI updates via `Platform.runLater`.

If any of these points are unclear or you'd like the instructions to emphasize different files or workflows, tell me which areas to expand.