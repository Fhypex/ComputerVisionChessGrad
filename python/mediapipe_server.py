from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
import uvicorn
import numpy as np
import cv2

app = FastAPI()

# Import MediaPipe hands with fallback import styles and helpful error message
try:
    import mediapipe as mp
    mp_hands = mp.solutions.hands
except Exception:
    try:
        from mediapipe import solutions as mp_solutions
        mp_hands = mp_solutions.hands
    except Exception as e:
        raise ImportError(
            "Could not import MediaPipe 'solutions'.\n"
            "Ensure you installed the official mediapipe package (e.g. `pip install mediapipe`).\n"
            f"Original error: {e}"
        )

# Use a persistent Hands object for performance
hands = mp_hands.Hands(static_image_mode=False,
                      max_num_hands=2,
                      min_detection_confidence=0.5,
                      min_tracking_confidence=0.5)


@app.post('/detect')
async def detect(image: UploadFile = File(...)):
    try:
        data = await image.read()
        arr = np.frombuffer(data, np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if img is None:
            return JSONResponse(status_code=400, content={"error": "invalid image"})

        img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        results = hands.process(img_rgb)

        hand_present = results.multi_hand_landmarks is not None
        # We don't have a single confidence score from MediaPipe hands; approximate
        confidence = 1.0 if hand_present else 0.0

        return {"hand": bool(hand_present), "confidence": float(confidence)}

    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


if __name__ == '__main__':
    uvicorn.run(app, host='127.0.0.1', port=8000)
