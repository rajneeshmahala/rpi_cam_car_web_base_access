# ---- FORCE LGPIO BACKEND ----
from gpiozero import Device
from gpiozero.pins.lgpio import LGPIOFactory
Device.pin_factory = LGPIOFactory()

from flask import Flask, Response, render_template, request, jsonify
from gpiozero import DigitalOutputDevice
import cv2

app = Flask(__name__)

# ---- MOTOR PINS ----
IN1 = DigitalOutputDevice(17)
IN2 = DigitalOutputDevice(27)
IN3 = DigitalOutputDevice(22)
IN4 = DigitalOutputDevice(23)

# ---- CAMERA ----
camera = cv2.VideoCapture(0)
camera.set(3, 320)
camera.set(4, 240)

# ---- MOTOR FUNCTIONS ----
def stop():
    IN1.off(); IN2.off(); IN3.off(); IN4.off()

def forward():
    IN1.on(); IN2.off()
    IN3.on(); IN4.off()

def backward():
    IN1.off(); IN2.on()
    IN3.off(); IN4.on()

def left():
    IN1.off(); IN2.on()
    IN3.on(); IN4.off()

def right():
    IN1.on(); IN2.off()
    IN3.off(); IN4.on()

# ---- VIDEO STREAM ----
def generate_frames():
    while True:
        success, frame = camera.read()
        if not success:
            break
        else:
            _, buffer = cv2.imencode('.jpg', frame)
            frame = buffer.tobytes()
            yield (b'--frame\r\n'
                   b'Content-Type: image/jpeg\r\n\r\n' + frame + b'\r\n')

# ---- ROUTES ----
@app.route('/')
def index():
    return render_template('index.html')

@app.route('/video')
def video():
    return Response(generate_frames(),
                    mimetype='multipart/x-mixed-replace; boundary=frame')

@app.route('/control', methods=['POST'])
def control():
    action = request.json.get("action")

    if action == "forward":
        forward()
    elif action == "backward":
        backward()
    elif action == "left":
        left()
    elif action == "right":
        right()
    elif action == "stop":
        stop()

    return jsonify({"status": "ok"})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
