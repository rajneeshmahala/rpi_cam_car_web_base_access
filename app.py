# ================================================
#   RPi Zero Car — L293D Motor Driver + IP Camera
#   GPIO Pins: IN1=5, IN2=13, IN3=19, IN4=26
#   Motor A (Left wheel):  IN1=5,  IN2=13
#   Motor B (Right wheel): IN3=19, IN4=26
# ================================================

from gpiozero import Device
from gpiozero.pins.lgpio import LGPIOFactory
Device.pin_factory = LGPIOFactory()

from flask import Flask, Response, render_template, request, jsonify
from gpiozero import DigitalOutputDevice
import cv2, time, threading

app = Flask(__name__)

# ---- L293D MOTOR PINS (BCM) ----
IN1 = DigitalOutputDevice(5)   # Motor A +
IN2 = DigitalOutputDevice(13)  # Motor A -
IN3 = DigitalOutputDevice(19)  # Motor B +
IN4 = DigitalOutputDevice(26)  # Motor B -

# ---- IP CAMERA ----
IP_CAMERA_URL = "http://192.168.1.2:8080/video"
camera_lock = threading.Lock()

class LiveCamera:
    """Background thread camera — always holds the latest frame, no buffer lag."""
    def __init__(self, url):
        self.url = url
        self.frame = None
        self.ok = False
        self._t = threading.Thread(target=self._reader, daemon=True)
        self._t.start()

    def _reader(self):
        while True:
            cap = cv2.VideoCapture(self.url)
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
            if not cap.isOpened():
                time.sleep(1)
                continue
            self.ok = True
            while True:
                ret, frame = cap.read()
                if not ret:
                    self.ok = False
                    break
                with camera_lock:
                    self.frame = frame
            cap.release()
            time.sleep(1)  # retry on disconnect

    def read(self):
        with camera_lock:
            return self.frame is not None, (self.frame.copy() if self.frame is not None else None)

    def isOpened(self):
        return self.ok

camera = LiveCamera(IP_CAMERA_URL)

# No auto-stop timer — car runs as long as joystick is held

# ---- MOTOR FUNCTIONS ----
def stop():
    IN1.off(); IN2.off()
    IN3.off(); IN4.off()

def forward():
    IN1.on();  IN2.off()   # Motor A forward
    IN3.on();  IN4.off()   # Motor B forward

def backward():
    IN1.off(); IN2.on()    # Motor A backward
    IN3.off(); IN4.on()    # Motor B backward

def turn_left():
    # Motor A STOP, Motor B FORWARD → pivots left
    IN1.off(); IN2.off()   # Motor A stop
    IN3.on();  IN4.off()   # Motor B forward only
    print("[MOTOR] LEFT")

def turn_right():
    # Motor A FORWARD, Motor B STOP → pivots right
    IN1.on();  IN2.off()   # Motor A forward only
    IN3.off(); IN4.off()   # Motor B stop
    print("[MOTOR] RIGHT")

# ---- VIDEO STREAM ----
def generate_frames():
    while True:
        success, frame = camera.read()
        if not success:
            time.sleep(0.1)
            continue
        _, buffer = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 70])
        yield (b'--frame\r\n'
               b'Content-Type: image/jpeg\r\n\r\n' + buffer.tobytes() + b'\r\n')

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
    action = request.json.get("action", "stop")
    actions = {
        "forward":  forward,
        "backward": backward,
        "left":     turn_left,
        "right":    turn_right,
        "stop":     stop,
    }
    fn = actions.get(action)
    if fn:
        fn()

    return jsonify({"status": "ok", "action": action})

@app.route('/status')
def status():
    return jsonify({"camera": camera.isOpened(), "status": "running"})

if __name__ == '__main__':
    try:
        print("🚗 RPi Car Server on http://0.0.0.0:5000")
        app.run(host='0.0.0.0', port=5000, threaded=True)
    finally:
        stop()
        camera.release()
