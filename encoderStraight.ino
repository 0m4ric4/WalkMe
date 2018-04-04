const byte encoder0pinA = 2;//A pin -> the interrupt pin 2
const byte encoder0pinB = 8;//B pin -> the digital pin 4

const byte encoder1pinA = 3;//A pin -> the interrupt pin 2
const byte encoder1pinB = 10;//B pin -> the digital pin 4

byte encoder0PinALast;
byte encoder1PinALast;

volatile unsigned long duration0 = 0;//the number of the pulses
volatile unsigned long duration1 = 0;//the number of the pulses

volatile boolean direction0;//the rotation direction 
volatile boolean direction1;//the rotation direction 

double distance0 = 0;
double distance1 = 0;
//Standard PWM DC control
int E1 = 5;     //M1 Speed Control //left
int E2 = 6;     //M2 Speed Control
int M1 = 4;    //M1 Direction Control //right
int M2 = 7;    //M1 Direction Control

///For previous Romeo, please use these pins.
//int E1 = 6;     //M1 Speed Control
//int E2 = 9;     //M2 Speed Control
//int M1 = 7;    //M1 Direction Control
//int M2 = 8;    //M1 Direction Control


void stop(void)                    //Stop
{
  digitalWrite(E1,LOW);   
  digitalWrite(E2,LOW); 

}   
void advance(char a,char b)          //Move forward
{
  analogWrite (E1,a);      //PWM Speed Control
  digitalWrite(M1,HIGH);    
  analogWrite (E2,b);    
  digitalWrite(M2,HIGH);
}  
void back_off (char a,char b)          //Move backward
{
  analogWrite (E1,a);
  digitalWrite(M1,LOW);   
  analogWrite (E2,b);    
  digitalWrite(M2,LOW);
}
void turn_L (char a,char b)             //Turn Left
{
  analogWrite (E1,a);
  digitalWrite(M1,LOW);    
  analogWrite (E2,b);    
  digitalWrite(M2,HIGH);
}
void turn_R (char a,char b)             //Turn Right
{
  analogWrite (E1,a);
  digitalWrite(M1,HIGH);    
  analogWrite (E2,b);    
  digitalWrite(M2,LOW);
}

void setup(void) 
{ 

 /* pinMode(2, INPUT);
  pinMode(3, INPUT);
  pinMode(8, INPUT);
  pinMode(10, INPUT);
 */  
  int i;
  for(i=4;i<=7;i++)
    pinMode(i, OUTPUT);  
  Serial.begin(9600);      //Set Baud Rate
    EncoderInit();

  Serial.println("Run keyboard control");


  driveStraight(10000);
 
 
} 

  
void EncoderInit()
{
  direction0 = false ;//default -> Forward  
  direction1 = true ;//default -> Forward  

  pinMode(encoder0pinB,INPUT);  
  pinMode(encoder1pinB,INPUT);  
  pinMode(2, INPUT_PULLUP);
  pinMode(3, INPUT_PULLUP);


  attachInterrupt(digitalPinToInterrupt(2), wheel0Speed, CHANGE);//int.0 
  attachInterrupt(digitalPinToInterrupt(3), wheel1Speed, CHANGE);//int.0 

}
  
void wheel0Speed()
{
  int Lstate = digitalRead(encoder0pinA);
  if((encoder0PinALast == LOW) && Lstate==HIGH)
  {
    int val = digitalRead(encoder0pinB);
    if(val == LOW && direction0)
    {
      direction0 = true; //Reverse
    }
    else if(val == HIGH && !direction0)
    {
      direction0 = false;  //Forward
    }
  }
  encoder0PinALast = Lstate;
  
  if(!direction0)  duration0++;
  else  duration0--;
}
void wheel1Speed()
{
  int Lstate = digitalRead(encoder1pinA);
  if((encoder1PinALast == LOW) && Lstate==HIGH)
  {
    int val = digitalRead(encoder1pinB);
    if(val == LOW && direction1)
    {
      direction1 = false; //Reverse
    }
    else if(val == HIGH && !direction1)
    {
      direction1 = true;  //Forward
    }
  }
  encoder1PinALast = Lstate;
  
  if(!direction1)  duration1++;
  else  duration1--;
}

int masterPower = 60;
int slavePower = 60;
int error = 0;
int kp = 1;

int validateSpeed(int speed){

  if (speed<60)
  return 60;
  else if (speed>80)
  return 80;
}

void driveStraight(int duration)
{
 unsigned long target = millis()+ duration;
  while ( millis() < target)
  {
    analogWrite (E2,validateSpeed(slavePower));    
    digitalWrite(M2,LOW);
    analogWrite (E1,masterPower);    
    digitalWrite(M1,LOW);


    error = duration0-duration1;
    slavePower += error / kp;

    duration0 = 0;
    duration1 = 0;

    


    delay(100);
    
  
  /*  Serial.print("Left Motor  Distance moved: ");
    Serial.print(distance0);
    Serial.println(" meters");
    Serial.print("Motor A Direction ");
    Serial.println(direction0);
    Serial.println();
  
    distance1 = (0.13*duration1)/(663*2);

    Serial.print("Right Motor  Distance moved: ");
    Serial.print(distance1);
    Serial.println(" meters");
    Serial.print("Motor B Direction ");
    Serial.println(direction1);
    Serial.println();
    
    Serial.print("Pulse A:");
    Serial.println(duration0);
    Serial.print("Pulse B:");
    Serial.println(duration1);
    Serial.println(); */
  
  }
   digitalWrite(E1,LOW);   
   digitalWrite(E2,LOW); 
  
}
void loop(void) 
{

   
  //1327


 

/*
  if(Serial.available()){
    char val = Serial.read();
    if(val != -1)
    {
      switch(val)
      {
      case 'w'://Move Forward
        advance (255,255);   //move forward in max speed
        break;
      case 's'://Move Backward
        back_off (255,255);   //move back in max speed
        break;
      case 'a'://Turn Left
        turn_L (100,100);
        break;       
      case 'd'://Turn Right
        turn_R (100,100);
        break;
      case 'z':
        Serial.println("Hello");
        break;
      case 'x':
        stop();
        break;
      }
    }
    else stop();  
  }
  /*if(duration >=662 && duration <=664)
  {
    duration = 0;
    digitalWrite(E2,LOW); 
    delay(5000);
    
  }
  
  analogWrite (E2,55);    
  digitalWrite (M2,LOW);
  */

 
/*
    for(int i = 100 ; i < 255 ; i++ )
    {
      analogWrite (E2,i);    
      digitalWrite(M2,HIGH);
           Serial.println(i);
      Serial.print("Pulse:");
       Serial.println(duration);
       duration = 0;
       delay(100);
    }
      for(int i = 255 ; i >100 ; i-- )
    {
     analogWrite (E2,i);    
      digitalWrite(M2,HIGH);
           Serial.println(i);
      Serial.print("Pulse:");
       Serial.println(duration);
       duration = 0;
       delay(100);
      
     
    }
*/
}
