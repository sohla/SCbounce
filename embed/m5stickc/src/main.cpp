#include <M5StickC.h>

#include <WiFi.h>
#include <WiFiUdp.h>

#include <OSCMessage.h>
#include <OSCData.h>

//--------------------------------------------------------------------------

#define OUTPORT 57120 //port for outgoing osc (to supercollider)
// #define BLACK 0x000000
// #define GREEN 0xFF0000
// #define RED 0x00FF00
// #define BLUE 0x0000FF

//--------------------------------------------------------------------------

const char *ssid = "SOHLA3"; //LAN name
const char *password = "sohla3letmein";  //LAN password
const IPAddress outIp(192,168,20,11);  //LAN address
//--------------------------------------------------------------------------

const int button = 39;//37,39

//--------------------------------------------------------------------------
int lastValue = 0;
int curValue = 0;
//--------------------------------------------------------------------------

WiFiUDP Udp;

float accX = 0, accY = 0, accZ = 0;
float gyroX = 0, gyroY = 0, gyroZ = 0;
float pitch, roll, yaw = 0;
float w,x,y,z = 0;
float temp = 0;

bool IMU6886Flag = false;

//--------------------------------------------------------------------------
// Set your Static IP address
IPAddress local_IP(192,168,20,STATIP);
// Set your Gateway IP address
IPAddress gateway(192,168,20,254);

IPAddress subnet(255, 255, 255, 0);
IPAddress primaryDNS(8, 8, 8, 8); //optional
IPAddress secondaryDNS(8, 8, 4, 4); //optional



//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------


//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------
void beginWifi() {

//   wifi_set_sleep_type(NONE_SLEEP_T);
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
    delay(250);
    Serial.print(F("."));
    delay(250);
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

    delay(500);

}
//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------
void beginSensors(){

  if (M5.IMU.Init() != 0)
      IMU6886Flag = false;
  else
      IMU6886Flag = true;

  pinMode(button, INPUT);
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
void setup()
{
    M5.begin();
    delay(50);

    beginWifi();

    beginSensors();

    sendConnectMsg();

    Serial.println("standby.....");
  
}

//--------------------------------------------------------------------------
//
//--------------------------------------------------------------------------
void loop(){

  if (IMU6886Flag == true){

      M5.IMU.getGyroData(&gyroX, &gyroY, &gyroZ);
      M5.IMU.getAccelData(&accX, &accY, &accZ);
      M5.IMU.getAhrsData(&pitch, &roll, &yaw);

      OSCMessage msgA("/gyrosc/rrate");
      msgA.add(gyroX / 60.0);
      msgA.add(gyroY / 60.0);
      msgA.add(gyroZ / 60.0);
      Udp.beginPacket(outIp, OUTPORT);
      msgA.send(Udp);
      Udp.endPacket();
      msgA.empty();

      OSCMessage msgB("/gyrosc/accel");
      msgB.add((accX * 1));
      msgB.add((accY * 1));
      msgB.add((accZ * 1) - 1.047119140625);
      Udp.beginPacket(outIp, OUTPORT);
      msgB.send(Udp);
      Udp.endPacket();
      msgB.empty();

      OSCMessage msgC("/gyrosc/gyro");
      msgC.add(pitch / 60.0);
      msgC.add(roll / 60.0);
      msgC.add(yaw / 60.0);
      Udp.beginPacket(outIp, OUTPORT);
      msgC.send(Udp);
      Udp.endPacket();
      msgC.empty();


      // w = cos((pitch + roll + yaw) * 0.5);
      // x = sin(yaw * 0.5);
      // y = sin(pitch * 0.5);
      // z = sin(roll * 0.5);

      // OSCMessage msgC("/gyrosc/quat");
      // msgC.add(w);
      // msgC.add(x);
      // msgC.add(y);
      // msgC.add(z);
      // Udp.beginPacket(outIp, OUTPORT);
      // msgC.send(Udp);
      // Udp.endPacket();
      // msgC.empty();


  }
  delay(50);

  curValue = digitalRead(button);
  
  if(curValue != lastValue){
    sendConnectMsg();
    lastValue = curValue;
  }

  M5.update();
}
