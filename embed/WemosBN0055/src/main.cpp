#include <Wire.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_BNO055.h>
#include <utility/imumaths.h>

#include <ESP8266WiFi.h>
#include <WiFiUdp.h>

#include <OSCMessage.h>
#include <OSCData.h>

//--------------------------------------------------------------------------
extern "C" {
#include "user_interface.h"
  uint16 readvdd33(void);
  bool wifi_set_sleep_type(sleep_type_t);
  sleep_type_t wifi_get_sleep_type(void);
}

//--------------------------------------------------------------------------
#define BNO055_SAMPLERATE_DELAY_MS (50)

#define OUTPORT 57120 //port for outgoing osc (to supercollider)

//--------------------------------------------------------------------------

const char *ssid = "SOHLA2"; //LAN name
const char *password = "pleaseletmeinagain";  //LAN password
const IPAddress outIp(10,1,1,4);  //LAN address

//--------------------------------------------------------------------------
// Set your Static IP address
IPAddress local_IP(10,1,1,STATIP);
// Set your Gateway IP address
IPAddress gateway(10,1,1,254);

IPAddress subnet(255, 255, 255, 0);
IPAddress primaryDNS(8, 8, 8, 8);   //optional
IPAddress secondaryDNS(8, 8, 4, 4); //optional

//--------------------------------------------------------------------------
Adafruit_BNO055 bno = Adafruit_BNO055(55, 0x29);
WiFiUDP Udp;


//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------
void beginWifi() {

  wifi_set_sleep_type(NONE_SLEEP_T);
  
  //delay(200);

  Serial.print(F("Getting IP address..."));

  //delay(200);

  WiFi.mode(WIFI_STA);
  WiFi.hostname("esposc");

  // Configures static IP address
    if (!WiFi.config(local_IP, gateway, subnet, primaryDNS, secondaryDNS)) {
      Serial.println("STA Failed to configure");
    }
  
  //delay(100);
  
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(F("."));
  }
  
  //while (WiFi.waitForConnectResult() != WL_CONNECTED) {
  //   Serial.println("Connection Failed! Rebooting...");
  //   delay(5000);
  //   ESP.restart();
  // }
  // //setupOTA();

  Serial.print("MAC: ");
  Serial.println(WiFi.macAddress());

  Serial.println(F("WiFi connected"));
  Serial.print(F("IP address is "));
  Serial.println(WiFi.localIP());

  Serial.println("Starting UDP");
  Udp.begin(INPORT);
  Serial.print("Local port: ");
#ifdef ESP32
  Serial.println(INPORT);
#else
  Serial.println(Udp.localPort());
#endif

}

//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------
void updateUdpPackets(){

  OSCMessage msg;
  OSCErrorCode error;

  int size = Udp.parsePacket();

  if (size > 0) {
    while (size--) {
      msg.fill(Udp.read());
    }
    if (!msg.hasError()) {
      // msg.dispatch("/tobtt/lx", onMsg_lx);
      // msg.dispatch("/tobtt/configPins", configPins);
      // msg.dispatch("/tobtt/restartDevice", restartDevice);
      // msg.dispatch("/tobtt/supplyVoltage", supplyVoltage);

      // msg.dispatch("/tobtt/lxFade", lxFade);

    } else {
      error = msg.getError();
      Serial.print("error: ");
      Serial.println(error);
    }
  }
}

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
void beginSensors() {

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
void sendConnectMsg(){

  OSCMessage msg("/gyrosc/button");
  msg.add("1");
  Udp.beginPacket(outIp, OUTPORT);
  msg.send(Udp);
  Udp.endPacket();
  msg.empty();
}

//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------
void setup(void) {

  Serial.begin(115200);
  Serial.setDebugOutput(true);
    
  beginWifi();

  beginSensors();

  sendConnectMsg();

  Serial.println("standby.....");
  delay(500);
}

//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------

void loop(void){

  sensors_event_t event;
  bno.getEvent(&event, Adafruit_BNO055::VECTOR_GYROSCOPE);

  if ((event.type == SENSOR_TYPE_GYROSCOPE) || (event.type == SENSOR_TYPE_ROTATION_VECTOR)) {
  
    // Serial.print(": x= ");
    // Serial.print(event.gyro.x);
    // Serial.print(" | y= ");
    // Serial.print(event.gyro.y);
    // Serial.print(" | z= ");
    // Serial.print(event.gyro.z);
    // Serial.print(" :: ");
    ///gyrosc/quat
    
    OSCMessage msg("/gyrosc/rrate");
    msg.add(event.gyro.x / 60.0);
    msg.add(event.gyro.y / 60.0);
    msg.add(event.gyro.z / 60.0);
    Udp.beginPacket(outIp, OUTPORT);
    msg.send(Udp);
    Udp.endPacket();
    msg.empty();
  }

  bno.getEvent(&event, Adafruit_BNO055::VECTOR_ACCELEROMETER);

  if (event.type == SENSOR_TYPE_ACCELEROMETER){

    Serial.print(": x= ");
    Serial.print(event.acceleration.x);
    Serial.print(" | y= ");
    Serial.print(event.acceleration.y);
    Serial.print(" | z= ");
    Serial.print(event.acceleration.z);
    Serial.print(" :: ");

    OSCMessage msg("/gyrosc/accel");
    msg.add( (event.acceleration.x + 0.50) * 0.1);
    msg.add( (event.acceleration.y + 0.35) * 0.1);
    msg.add( (event.acceleration.z - 9.81) * 0.1);
    Udp.beginPacket(outIp, OUTPORT);
    msg.send(Udp);
    Udp.endPacket();
    msg.empty();
  }

  imu::Quaternion quat = bno.getQuat();

  // Serial.print("qW: ");
  // Serial.print(quat.w(), 4);
  // Serial.print(" qX: ");
  // Serial.print(quat.x(), 4);
  // Serial.print(" qY: ");
  // Serial.print(quat.y(), 4);
  // Serial.print(" qZ: ");
  // Serial.print(quat.z(), 4);
  // Serial.println("");

  OSCMessage msg("/gyrosc/quat");
  msg.add(quat.w());
  msg.add(quat.x());
  msg.add(quat.y());
  msg.add(quat.z());
  Udp.beginPacket(outIp, OUTPORT);
  msg.send(Udp);
  Udp.endPacket();
  msg.empty();

  updateUdpPackets();

  delay(BNO055_SAMPLERATE_DELAY_MS);


}