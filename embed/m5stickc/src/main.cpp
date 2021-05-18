#include <M5StickC.h>

#include <WiFi.h>
#include <WiFiUdp.h>

#include <OSCMessage.h>
#include <OSCData.h>

//--------------------------------------------------------------------------

#define OUTPORT 57120 //port for outgoing osc (to supercollider)
#define BLACK 0x000000
#define GREEN 0xFF0000
#define RED 0x00FF00
#define BLUE 0x0000FF

//--------------------------------------------------------------------------

const char *ssid = "SOHLA3"; //LAN name
const char *password = "sohla3letmein";  //LAN password
const IPAddress outIp(192,168,20,11);  //LAN address

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
void loop()
{

    if (IMU6886Flag == true)
    {
        M5.IMU.getGyroData(&gyroX, &gyroY, &gyroZ);
        M5.IMU.getAccelData(&accX, &accY, &accZ);

        OSCMessage msgA("/gyrosc/rrate");
        msgA.add(gyroX / 60.0);
        msgA.add(gyroY / 60.0);
        msgA.add(gyroZ / 60.0);
        Udp.beginPacket(outIp, OUTPORT);
        msgA.send(Udp);
        Udp.endPacket();
        msgA.empty();

        OSCMessage msgB("/gyrosc/accel");
        msgB.add( (accX + 0.50) * 0.1);
        msgB.add( (accY + 0.35) * 0.1);
        msgB.add( (accZ - 9.81) * 0.1);
        Udp.beginPacket(outIp, OUTPORT);
        msgB.send(Udp);
        Udp.endPacket();
        msgB.empty();

        w = cos((gyroX + gyroY + gyroZ) * 0.5);
        x = sin(gyroZ * 0.5);
        y = sin(gyroX * 0.5);
        z = sin(gyroY * 0.5);

        OSCMessage msgC("/gyrosc/quat");
        msgC.add(w);
        msgC.add(x);
        msgC.add(y);
        msgC.add(z);
        Udp.beginPacket(outIp, OUTPORT);
        msgC.send(Udp);
        Udp.endPacket();
        msgC.empty();


        // M5.IMU.getTempData(&temp);
        // M5.IMU.getAhrsData(&pitch, &roll, &yaw);
        // Serial.printf("%.2f,%.2f,%.2f,%.2f  \r\n", w,x,y,z);
        // Serial.printf("%.2f,%.2f,%.2f  \r\n", pitch, roll, yaw);
        // Serial.printf("%.2f,%.2f,%.2f o/s \r\n", gyroX, gyroY, gyroZ);
        //Serial.printf("%.2f,%.2f,%.2f mg\r\n", accX * 1000, accY * 1000, accZ * 1000);
        //Serial.printf("Temperature : %.2f C \r\n", temp);
    }
    delay(50);
    M5.update();
}

/*


t_quaternion qg;
    qg.w = cos((gyro->x + gyro->y + gyro->z) * 0.5);
    qg.x = sin(gyro->z * 0.5);
    qg.y = sin(gyro->x * 0.5);
    qg.z = sin(gyro->y * 0.5);

*/