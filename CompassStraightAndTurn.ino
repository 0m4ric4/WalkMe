#include <Wire.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_LSM303_U.h>

Adafruit_LSM303_Mag_Unified mag = Adafruit_LSM303_Mag_Unified(12345);

// Motor pins
#define EN1 6 // Pin for run the left motor 
#define IN1 7 // Pin for control left motor direction

#define EN2 5 // Pin for run the right motor 
#define IN2 4 // Pin for control right motor direction

// Variables for direction control
float initial_heading;
float target_heading;
float diff_heading;
bool turn_init;
bool target_reached;

// Left & right
#define LEFT 0
#define RIGHT 1

float straightSpeed = 90;

void setup(void) 
{
  // Start Serial
  Serial.begin(9600);
  
  // Declare motor pins as outputs
  pinMode(5,OUTPUT);
  pinMode(6,OUTPUT);
  pinMode(7,OUTPUT);
  pinMode(4,OUTPUT); 

  
  Serial.println("Magnetometer Test"); Serial.println("");

   
  /* Initialise the sensor */
  if(!mag.begin())
  {
    /* There was a problem detecting the LSM303 ... check your connections */
    Serial.println("Ooops, no LSM303 detected ... Check your wiring!");
    while(1);
  }
 
  // Initialise direction variables
  turn_init = false;
  target_reached = false;


    driveStraight(20000);

}



int validateSpeed(double speed) {
  if(speed>130)
  return 130;
  else if(speed<60)
  return 60;
  else
  return speed;
}
void driveStraight(int duration) {

 float  target = get_heading();

  unsigned long destination = millis() + duration;

/*   send_motor_command(EN1,IN1,straightSpeed,1);
   send_motor_command(EN2,IN2,straightSpeed,1);
   delay(200);*/
  while (millis() < destination) 
  {

    int absolute = get_heading();
    double rightSpeed = validateSpeed((straightSpeed) - (( absolute - target )*(1)));
    double leftSpeed = validateSpeed((straightSpeed) + ((absolute - target)*(1)));
    
    Serial.print("Right Speed: ");
    Serial.println(rightSpeed);
    Serial.print("Left Speed: "); 
    Serial.println(leftSpeed); 

    send_motor_command(EN1,IN1,leftSpeed,1);
    send_motor_command(EN2,IN2,rightSpeed,1);
    
    
  }

  
      Serial.println("Turning off motor ...");

  analogWrite(EN1,0);
  analogWrite(EN2,0);
   
  
}
// Turn of a given angle
void turn(float angle, bool turn_direction) {
  
  // Get initial heading
  if (!turn_init) {
    Serial.println("Starting turn ...");
    initial_heading = get_heading();
    
    if (turn_direction) {
      target_heading = initial_heading + angle;
    }
    else {
      target_heading = initial_heading - angle;
    }
    
    Serial.print("Initial heading: ");
    Serial.println(initial_heading);
    Serial.print("Target heading: ");
    Serial.println(target_heading);
    
    turn_init = true;
    target_reached = false;
    diff_heading = 0;
  }
  
  float heading = get_heading();
  
  if (turn_direction) {
    diff_heading = target_heading - heading;
  }
  else {
    diff_heading = heading - target_heading;
  }
 
  while (diff_heading > 0 && !target_reached) {
    
    // Move & stop
    if (turn_direction) {
      right(90);
    }
    else {
      left(90);
    }
    delay(300);
    right(0);
    delay(300);
    
    // Measure heading again
    float heading = get_heading();
    
    if (turn_direction) {
      diff_heading = target_heading - heading;
    }
    else {
      diff_heading = heading - target_heading;
    }
    Serial.print("Difference heading (degrees): "); Serial.println(diff_heading);
    
    if (diff_heading < 0  && !target_reached) {
      target_reached = true;
      turn_init = false;
      diff_heading = 0;
    }
  }
  
  // Stop
  Serial.println("Stopping turn ...");
}

// Get heading from the compass
float get_heading() {

 sensors_event_t event; 
  mag.getEvent(&event);
  
  float Pi = 3.14159;
  
  // Calculate the angle of the vector y,x
  float heading = (atan2(event.magnetic.y,event.magnetic.x) * 180) / Pi;
  
  // Normalize to 0-360
  if (heading < 0)
  {
    heading = 360 + heading;
  }
  Serial.println(heading);
  return heading;
}

// Function to command a given motor of the robot
void send_motor_command(int speed_pin, int direction_pin, int pwm, boolean dir)
{
  analogWrite(speed_pin,pwm); // Set PWM control, 0 for stop, and 255 for maximum speed
  digitalWrite(direction_pin,dir);
}

// Turn right
void right(int motor_speed) {
  
  send_motor_command(EN1,IN1,motor_speed,0);
  send_motor_command(EN2,IN2,motor_speed,1);
  
}

// Turn left
void left(int motor_speed) {
  
  send_motor_command(EN1,IN1,motor_speed,1);
  send_motor_command(EN2,IN2,motor_speed,0);
  
}

void loop(void) 
{


}
