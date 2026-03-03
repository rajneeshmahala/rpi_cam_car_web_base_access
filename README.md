# 🚗 Raspberry Pi Zero 2 W WiFi Car with Live Streaming

## 📌 Project Overview

This project allows you to control a car using a web browser over WiFi.
Features: - Live camera streaming - Web-based motor control -
Mobile-friendly interface - GPIO control using lgpio backend - Designed
for Raspberry Pi Zero 2 W

------------------------------------------------------------------------

# 🧰 Hardware Required

-   Raspberry Pi Zero 2 W
-   L293D Motor Driver
-   2 DC Motors
-   USB Webcam
-   External Motor Battery (7--12V)
-   Jumper Wires
-   Car Chassis

------------------------------------------------------------------------

# 🔌 GPIO Wiring (BCM Numbering)

## ✅ GPIO Pins Used

  Function   BCM GPIO   Physical Pin
  ---------- ---------- --------------
  IN1        GPIO5      Pin 29
  IN2        GPIO13     Pin 33
  IN3        GPIO19     Pin 35
  IN4        GPIO26     Pin 37
  GND        GND        Pin 6

⚠ IMPORTANT: - Do NOT use GPIO6. - Motor battery GND must connect to
Raspberry Pi GND. - Do NOT power motors from Pi 5V.

------------------------------------------------------------------------

# 📂 Project Structure

    car/
    │
    ├── app.py
    ├── requirements.txt
    └── templates/
        └── index.html

------------------------------------------------------------------------

# 💻 Raspberry Pi Setup (Fresh OS)

## 1️⃣ Update System

    sudo apt update
    sudo apt upgrade -y

## 2️⃣ Install Required System Packages

    sudo apt install python3-full python3-lgpio -y

------------------------------------------------------------------------

# 🐍 Python Environment Setup

    cd ~
    mkdir car
    cd car

    python3 -m venv venv --system-site-packages
    source venv/bin/activate

    pip install flask opencv-python-headless

------------------------------------------------------------------------

# 📦 requirements.txt

    flask
    opencv-python-headless

Install using:

    pip install -r requirements.txt

------------------------------------------------------------------------

# 🧠 app.py Code

``` python
# ---- FORCE LGPIO BACKEND ----
from gpiozero import Device
from gpiozero.pins.lgpio import LGPIOFactory
Device.pin_factory = LGPIOFactory()

from flask import Flask, Response, render_template, request, jsonify
from gpiozero import DigitalOutputDevice
import cv2

app = Flask(__name__)

IN1 = DigitalOutputDevice(5)
IN2 = DigitalOutputDevice(13)
IN3 = DigitalOutputDevice(19)
IN4 = DigitalOutputDevice(26)

camera = cv2.VideoCapture(0)
camera.set(3, 320)
camera.set(4, 240)

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
```

------------------------------------------------------------------------

# 🌐 Run Application

    source venv/bin/activate
    python app.py

Open browser:

    http://YOUR_PI_IP:5000

------------------------------------------------------------------------

# ⚙️ Optional Auto-Start via .bashrc

Edit:

    nano ~/.bashrc

Add:

    cd ~/car
    source venv/bin/activate
    python app.py

------------------------------------------------------------------------

# 🚀 Future Improvements

-   Joystick control
-   PWM speed control
-   Auto-start via systemd
-   Docker deployment
-   Secure login authentication
-   Cloud remote access

------------------------------------------------------------------------

# 🎉 Done!

Your Raspberry Pi WiFi Car is ready 🚗🔥
