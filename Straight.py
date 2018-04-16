
import pyrebase
import time
import RPi.GPIO as GPIO
import _thread
import time

straight_speed = 50

LEFT = 0
RIGHT = 1

turn_init = False
target_reached = False
initial_heading = None
target_heading = None
currentDegree = None

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

#A is right
#B is left
motorABackward = 26
motorAForward = 19
motorBForward = 6
motorBBackward = 5

motorAPWM = 21
motorBPWM = 20

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

pA.start(straight_speed)
pB.start(straight_speed)

runScript = None

time.sleep(0.1)


def encoderInit():

    GPIO.setup(motorABackward,GPIO.OUT)
    GPIO.setup(motorAForward,GPIO.OUT)
    
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


def stream_handler(message):
    # GPIO sync
    
    if message['path'] == '/currentDegree':
        global currentDegree
        currentDegree = message['data']
        #print(currentDegree)
    elif message['path'] == '/runScript':
        global runScript
        runScript = message['data']
        if(runScript):
            print("Running Script")
            testMotor()
            
        
        


my_stream = db.stream(stream_handler)

def get_heading():
    global currentDegree
    return currentDegree

def validate_speed(speed):

    if speed > 100:
        return 100
    elif speed < 10:
        return 10
    else:
        return speed

def drive_straight(duration):
    global straight_speed
    target = get_heading()
    destination = time.time() + duration

    moveMotorAForward()
    moveMotorBForward()
    while time.time() < destination:
    
        absolute = get_heading()
        print("Target: "+str(target)+", Absolute: "+ str(absolute))
        right_speed = validate_speed(straight_speed - (absolute - target))
        left_speed = validate_speed(straight_speed + (absolute - target))
        # Might affect reactivity
        print("Right Speed: "+str(right_speed))
        print("Left Speed: "+str(left_speed))
        
        # Adjust PWM
        pA.ChangeDutyCycle(right_speed)
        pB.ChangeDutyCycle(left_speed)
      

    # Turn Off Motors
    resetMotorPins()
    print("Turning off motor ...")

def turn(angle, turn_direction):

    global initial_heading,target_heading,turn_init,target_reachedglobal,diff_heading
    if ~turn_init:

        print("Starting turn...")
        initial_heading = get_heading()

        if turn_direction:
            target_heading = initial_heading + angle
        else:
            target_heading = initial_heading - angle

        print("Initial heading: ")
        print(initial_heading)
        print("Target heading: ")
        print(target_heading)

        turn_init = True
        target_reached = False
        diff_heading = 0

    heading = get_heading()
    
    if turn_direction:
        
        diff_heading = target_heading - heading
    else:
        diff_heading = heading - target_heading

    while diff_heading > 0 and ~target_reached:

        if turn_direction:
            right(30)
        else:
            left(30)

        time.sleep(0.3)
        right(0)
        time.sleep(0.3)

        heading = get_heading()

        if turn_direction:
            diff_heading = target_heading - heading
        else:
            diff_heading = heading - target_heading

        print("Difference heading (degrees): "+ diff_heading)


        if diff_heading < 0 and ~target_reached:
            target_reached = True
            turn_init = False
            diff_heading = 0



def right(motor_speed):
    moveMotorABackward()
    moveMotorBForward()
    

def left(motor_speed):
     moveMotorBBackward()
     moveMotorAForward()



def testMotor():
    try:
        drive_straight(10)
    
    #while(True):
        
        
    except KeyboardInterrupt:
        resetMotorPins()
        pA.stop()
        pB.stop()
        GPIO.cleanup()

