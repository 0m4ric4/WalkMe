import pyrebase
import time
import RPi.GPIO as GPIO
import _thread

config = {
    "apiKey": "AIzaSyDtpOZTI-MaUO3kgNLL405Lx3IQTIJcbnc",
    "authDomain": "walkme-38a8a.firebaseapp.com",
    "databaseURL": "https://walkme-38a8a.firebaseio.com",
    "storageBucket": "walkme-38a8a.appspot.com"
}

isMovingRight = False
isMovingLeft = False
isMovingBackward = False
isMovingForward = False

GPIO.setmode(GPIO.BCM)

motorABackward = 26
motorAForward = 19
motorBForward = 6
motorBBackward = 5

motorAPWM = 21
motorBPWM = 20

encoder0pinA = 2
encoder0pinB = 3

encoder0PinALast = 2

duration0 = 0
duration1 = 0 

direction0 = 0
direction1 = 0



firebase = pyrebase.initialize_app(config)
db = firebase.database()

GPIO.setup(motorABackward,GPIO.OUT)
GPIO.setup(motorAForward,GPIO.OUT)
GPIO.setup(motorBForward,GPIO.OUT)
GPIO.setup(motorBBackward,GPIO.OUT)
GPIO.setup(motorAPWM,GPIO.OUT)
GPIO.setup(motorBPWM,GPIO.OUT)

pA = GPIO.PWM(motorAPWM,50)
pB = GPIO.PWM(motorBPWM,50)

pA.start(50)
pB.start(50)



time.sleep(0.1)


def encoderInit():
    GPIO.setup(encoder0pinA,GPIO.IN)
    GPIO.setup(encoder0pinB,GPIO.IN, pull_up_down = GPIO.PUD_DOWN)
    direction0 = False
    direction1 = True
    encoder0PinALast = 2


def wheel0Speed(channel):

    global encoder0PinALast
    global direction0
    global duration0
    
    Lstate = GPIO.input(encoder0pinA)

    if(encoder0PinALast == 0 and Lstate== 1):
        val = GPIO.input(encoder0pinB)

        if(val == 0 and direction0 ):
            direction0 = True
        elif(val == 1 and ~direction0):
            direction0 = False
    encoder0PinALast = Lstate
    if(~direction0):
        duration0 = duration0+1
    else:
        duration0 = duration0-1
    
def moveMotorAForward():
    GPIO.output(motorAForward,GPIO.HIGH)
    GPIO.output(motorABackward,GPIO.LOW)
def moveMotorABackward():
    GPIO.output(motorABackward,GPIO.HIGH)
    GPIO.output(motorAForward,GPIO.LOW)
def moveMotorBForward():
    GPIO.output(motorBForward,GPIO.HIGH)
    GPIO.output(motorBBackward,GPIO.LOW)
def moveMotorBBackward():
    GPIO.output(motorBBackward,GPIO.HIGH)
    GPIO.output(motorBForward,GPIO.LOW)

def resetMotorPins():
    GPIO.output(motorAForward,GPIO.LOW)
    GPIO.output(motorABackward,GPIO.LOW)
    GPIO.output(motorBForward,GPIO.LOW)
    GPIO.output(motorBBackward,GPIO.LOW)

encoderInit()
GPIO.add_event_detect(encoder0pinB,GPIO.FALLING, callback = wheel0Speed)
try:
    while(True):
        moveMotorAForward()
        moveMotorBForward()
        distance0 = (0.13*duration0)/663;
        print(distance0)
        
except KeyboardInterrupt:
    resetMotorPins()
    pA.stop()
    pB.stop()
    GPIO.cleanup()
   

