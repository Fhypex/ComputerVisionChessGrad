from flask import Flask, request, jsonify
from flask_cors import CORS
import tensorflow as tf
import numpy as np
import base64
import cv2

app = Flask(__name__)
CORS(app)  # Allow cross-origin requests from Java

# Load the model once at startup
print("Loading model...")
model = tf.keras.models.load_model('detection_model.h5')
print("Model loaded successfully!")

CLASS_NAMES = [
    "black_bishop",       # 0
    "black_king",         # 1
    "black_knight",       # 2
    "black_pawn",         # 3
    "black_queen",        # 4
    "black_rook",         # 5
    "empty",              # 6
    "half_empty_square",  # 7
    "white_bishop",       # 8
    "white_king",         # 9
    "white_knight",       # 10
    "white_pawn",         # 11
    "white_queen",        # 12
    "white_rook"          # 13
]

@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.json
        
        # Expect flat array [150528 values] for 224x224x3
        image_data = data['image']
        
        # Reshape to (1, 224, 224, 3) and convert to numpy array
        img_array = np.array(image_data, dtype=np.float32).reshape(1, 224, 224, 3)
        
        # Make prediction
        predictions = model.predict(img_array, verbose=0)
        
        # Get class with highest probability
        predicted_class = int(np.argmax(predictions[0]))
        confidence = float(np.max(predictions[0]))
        
        # Get all probabilities
        all_probs = predictions[0].tolist()
        
        return jsonify({
            'success': True,
            'class_index': predicted_class,
            'class_name': CLASS_NAMES[predicted_class],
            'confidence': confidence,
            'probabilities': all_probs
        })
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 400

@app.route('/predict_base64', methods=['POST'])
def predict_base64():
    """
    Alternative endpoint that accepts base64-encoded image
    Useful if you want to send raw image bytes
    """
    try:
        data = request.json
        
        # Decode base64 image
        image_b64 = data['image_base64']
        image_bytes = base64.b64decode(image_b64)
        
        # Convert to numpy array
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        # Resize to 224x224
        img = cv2.resize(img, (224, 224))
        
        # Convert BGR to RGB
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        
        # Normalize to 0-1
        img = img.astype(np.float32) / 255.0
        
        # Add batch dimension
        img = np.expand_dims(img, axis=0)
        
        # Predict
        predictions = model.predict(img, verbose=0)
        predicted_class = int(np.argmax(predictions[0]))
        confidence = float(np.max(predictions[0]))
        
        return jsonify({
            'success': True,
            'class_index': predicted_class,
            'class_name': CLASS_NAMES[predicted_class],
            'confidence': confidence
        })
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 400

@app.route('/health', methods=['GET'])
def health():
    """Check if server is running"""
    return jsonify({
        'status': 'healthy',
        'model_loaded': model is not None,
        'num_classes': len(CLASS_NAMES)
    })

if __name__ == '__main__':
    print("\n" + "="*50)
    print("Chess Piece Classification Server")
    print("="*50)
    print(f"Model classes: {len(CLASS_NAMES)}")
    print("Endpoints:")
    print("  POST /predict - Accept flat float array")
    print("  POST /predict_base64 - Accept base64 image")
    print("  GET  /health - Health check")
    print("="*50 + "\n")
    
    # Run on port 5000
    app.run(host='0.0.0.0', port=5000, debug=False)