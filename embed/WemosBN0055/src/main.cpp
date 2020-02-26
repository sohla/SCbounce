#include <Wire.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_BNO055.h>
#include <utility/imumaths.h>

//--------------------------------------------------------------------------
#define BNO055_SAMPLERATE_DELAY_MS (100)

//--------------------------------------------------------------------------
Adafruit_BNO055 bno = Adafruit_BNO055(55, 0x29);

//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------
void displaySensorDetails(void){

  sensor_t sensor;
  bno.getSensor(&sensor);
  Serial.println("------------------------------------");
  Serial.print  ("Sensor:       "); Serial.println(sensor.name);
  Serial.print  ("Driver Ver:   "); Serial.println(sensor.version);
  Serial.print  ("Unique ID:    "); Serial.println(sensor.sensor_id);
  Serial.print  ("Max Value:    "); Serial.print(sensor.max_value); Serial.println(" xxx");
  Serial.print  ("Min Value:    "); Serial.print(sensor.min_value); Serial.println(" xxx");
  Serial.print  ("Resolution:   "); Serial.print(sensor.resolution); Serial.println(" xxx");
  Serial.println("------------------------------------");
  Serial.println("");
  delay(500);
}

//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------
void setup(void) {

  Serial.begin(115200);




  // init BNO055
  if(!bno.begin()) {
    Serial.print("Ooops, no BNO055 detected ... Check your wiring or I2C ADDR!");
    while(1);
  }
   
  delay(1000);
  bno.setExtCrystalUse(true);
  displaySensorDetails();

}

//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------

void loop(void){

  sensors_event_t event;
  bno.getEvent(&event, Adafruit_BNO055::VECTOR_GYROSCOPE);

  if ((event.type == SENSOR_TYPE_GYROSCOPE) || (event.type == SENSOR_TYPE_ROTATION_VECTOR)) {
  
    Serial.print(": x= ");
    Serial.print(event.gyro.x);
    Serial.print(" | y= ");
    Serial.print(event.gyro.y);
    Serial.print(" | z= ");
    Serial.print(event.gyro.z);
    Serial.print(" :: ");
  
  }
  
  imu::Quaternion quat = bno.getQuat();
  Serial.print("qW: ");
  Serial.print(quat.w(), 4);
  Serial.print(" qX: ");
  Serial.print(quat.x(), 4);
  Serial.print(" qY: ");
  Serial.print(quat.y(), 4);
  Serial.print(" qZ: ");
  Serial.print(quat.z(), 4);
  Serial.println("");

  delay(BNO055_SAMPLERATE_DELAY_MS);
}