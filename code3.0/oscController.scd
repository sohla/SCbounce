// NetAddr.new("192.168.70.165", 3333).sendMsg(*msg); //connected to pi
// NetAddr.new("192.168.2.1", 3333).sendMsg(*msg); // Mac Mini connected to SensiLab (ethernet)
// NetAddr.new("192.168.70.232", 3333).sendMsg(*msg); // iPhone connected 
// NetAddr.new("192.168.70.48", 3333).sendMsg(*msg); // connected to Laptop


// var oscMessageTag  = "CombinedDataPacket";
var oscMessageTag  = "IMUFusedData"; // this is coming from AirSticks
var outAddr = NetAddr.new("192.168.70.232", 3333);
var airstickListeners = [];
var numAirwareVirtualDevices = 4;

//------------------------------------------------------------
// Models
//------------------------------------------------------------

var twoCh = (\x: 0, \y:0);
var threeCh = (\x: 0, \y:0, \z:0);
var fourCh = (\w: 0, \x: 0, \y:0, \z:0);

var listenersProto = (
    \airware:nil,
    \battery:nil,
    \inc:nil,
    \dec:nil,
    \digiIn:nil

);


var sensorsProto = (
    \gyroEvent: threeCh,
    \gyroMass: 0,
    \rrateEvent: threeCh,
    \rrateMass: 0,
    \accelEvent: threeCh,
    \accelMass: 1,
    \quatEvent: fourCh,
    \ampValue: 0,
    \rotateEvent: threeCh,
    \digiInEvent: Array.fill(4,0),
);

var dataSizeOptions = [100,200,300,400];

var deviceProto = (
    \name: "default",
    \ip: "127.0.0.1",
    \index: 0,
    \personality: 0,
    \port: 57120,
    \color: Color.red,
    \volts: 0.0,
    \charge: 0.0,
    \enabled: true, // are we running
    \dataSize: dataSizeOptions[0],
    \listeners: Event.new(proto:listenersProto),
    \env: nil,	// Environment for injected code
    \procRout: nil,	// Routine calls ~next every ~fps
    \sensors: Event.new(proto:sensorsProto),
    \sensorBus: Bus.control(s,7),
    \incPersonality: {},
    \decPersonality: {};

);

//------------------------------------------------------------
// 
//------------------------------------------------------------
var addOSCDeviceListeners = {|d|

    var na = NetAddr.new(d.ip, d.port);
    var patternBase = "/%/" ++ oscMessageTag;
    var batteryBase = "/%/" ++ "Battery";
    var digiInBase = "/%/" ++ "DigiIn";

    // listen to all the airware that are connected (1 ip/port)
    numAirwareVirtualDevices.do({|i|


        var address = NetAddr.new(d.ip, d.port - i);
        var pattern = patternBase.format(i+1);
        // var pattern = patternBase.format("/60:01:E2:E2:27:48/");
        var prev = fourCh;
        var angVel = threeCh;
        var rx,ry,rz,ox=0,oy=0,oz=0;

        d.listeners.battery = OSCFunc({ |msg, time, addr, recvPort|
            d.volts = msg[1].asFloat;
            d.charge = msg[2].asFloat;
        }, batteryBase.format(i+1), address);

        d.listeners.digiIn = OSCFunc({ |msg, time, addr, recvPort|
        if(~devices.at(addr.port+i) != nil,{
            // [msg[1].asInteger,msg[2].asInteger].postln;
            ~devices.at(addr.port+i).sensors.digiInEvent[msg[1].asInteger] = msg[2].asInteger;
            // devices.at(addr.port+i).sensors.digiInEvent.postln;
        });
        }, digiInBase.format(i+1), address);

        d.listeners.airware = OSCFunc({ |msg, time, addr, recvPort|
            var sx,sy,sz,qe,q,ss,r, rq, rr, rtr;
            var tr;



            if(~devices.at(addr.port+i) != nil,{
                var oq = ~devices.at(addr.port+i).sensors.quatEvent;
                var bus = ~devices.at(addr.port+i).sensorBus;


                bus.setn([
                    msg[1].asFloat * 0.1,
                    msg[2].asFloat * 0.1,
                    msg[3].asFloat * 0.1,
                    msg[7].asFloat, //w
                    msg[4].asFloat, //x
                    msg[5].asFloat, //y
                    msg[6].asFloat, //z
                ]);
                // bus.getn(7).postln;

                ~devices.at(addr.port+i).sensors.accelEvent = (
                    \x:msg[1].asFloat * 0.1,
                    \y:msg[2].asFloat * 0.1,
                    \z:msg[3].asFloat * 0.1
                );

                ~devices.at(addr.port+i).sensors.quatEvent = (
                    \w:msg[7].asFloat,
                    \x:msg[4].asFloat,
                    \y:msg[5].asFloat,
                    \z:msg[6].asFloat
                );


                //
                // Calculate others
                // take quaternion and convert to ueler angles
                qe = ~devices.at(addr.port+i).sensors.quatEvent;
                q = Quaternion.new(qe.w,qe.x,qe.y,qe.z);
                // q = Quaternion.new(qe.w,qe.x,qe.z,qe.y);
                // q = Quaternion.new(qe.w,qe.y,qe.z,qe.x);
                // q = Quaternion.new(qe.w,qe.y,qe.x,qe.z);
                // q = Quaternion.new(qe.w,qe.z,qe.x,qe.y);
                // q = Quaternion.new(qe.w,qe.z,qe.y,qe.y);
                r = q.asEuler;
                tr = [r[0],r[1],r[2]];

                ~devices.at(addr.port+i).sensors.gyroEvent = (
                    \x:tr[2].asFloat,
                    \y:tr[0].asFloat,
                    \z:tr[1].asFloat);

                // normalize gyro from 0 to pi
                rx = tr[2].asFloat;
                ry = tr[0].asFloat;
                rz = tr[1].asFloat * (pi.half + pi.half.half);

                if(rx <= 0, { rx = pi - (pi + rx)});
                if(ry <= 0, { ry = pi - (pi + ry)});
                if(rz <= 0, { rz = pi - (pi + rz)});

                // rx,ry,rz / pi = 0-1 ie. normalized ?? not sure this works
                ~devices.at(addr.port+i).sensors.rotateEvent = (
                    \x:rx/pi,
                    \y:ry/pi,
                    \z:rz/pi);

                // store rate of change
                ~devices.at(addr.port+i).sensors.rrateEvent = (
                    \x:rx - ox,
                    \y:ry - oy,
                    \z:rz - oz);

                ox = rx;
                oy = ry;
                oz = rz;

            });
        }, pattern, address);
    });
};


var addDevice = { |ip,port,index|

    var d = Event.new(proto:deviceProto);

    // FIX : need a better way to do this
    var configPattern = "/%/Config".format(index);
    var configListener = OSCFunc({ |msg2, time2, addr2, recvPort2|
        d.color = Color.fromArray(msg2.at([15,16,17])++[1.0]);
        "CONFIG % %".format(index,d.color).postln;
        {
            var n = NetAddr.new("127.0.0.1", 57120);
            n.sendMsg("/airkit/addDevice", port);
        }.defer;
    }, configPattern).oneShot;

    // hack in : set name of device that is something from the list for now!
    // d.name = ~loadPersonalityList.(id-1)[id-1];
    // d.name = defaultPersonalities[id-1];

    d.ip = ip;
    d.port = port;
    d.index = index;

    d.listeners = Event.new(proto:listenersProto);
    d.sensors =  Event.new(proto:sensorsProto);

    // global storage : port is the key
    ~devices.put(port,d);
	NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", port, 0);

    // ~addDeviceView.(d,id);

    addOSCDeviceListeners.(d);

    // Use from AirStick 
    // NetAddr.new(ip,port-index+1).sendMsg("/Config/GetConfig", 57120);
    
    // Use from Airware
	NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/addDevice", port);

    d // return the device
};
//------------------------------------------------------------




//------------------------------------------------------------
// 
//------------------------------------------------------------

var startOSCListening = {

    var patternBase = "/%/" ++ oscMessageTag;

    // listen for data and if found, add airware virtual device and stop listening
    numAirwareVirtualDevices.do({|i|
        var pattern = patternBase.format(i+1);
        // var pattern = patternBase.format("60:01:E2:E2:27:48");

        airstickListeners = airstickListeners.add( OSCFunc({ |msg, time, addr, recvPort|
            {
                // outAddr.sendMsg(*msg); // iPhone connected 

                if(~devices.at(addr.port+i) == nil,{
                    var d = addDevice.(addr.ip,addr.port+i,i+1);
                    postf("device auto detected : % \n", d.index);
                });
            }.defer;
        }, pattern));
    });
};

var stopOSCListening = {
    airstickListeners.do({|obj|
        postf("free : %\n", obj);
        obj.free;
    });
};

~devices = Dictionary();

OSCFunc({ |msg, time, addr, recvPort|
    startOSCListening.();
},'/airkit/startOSCListening', NetAddr("127.0.0.1", NetAddr.langPort)).oneShot;

OSCFunc({ |msg, time, addr, recvPort|
    stopOSCListening.();
},'/airkit/stopOSCListening', NetAddr("127.0.0.1", NetAddr.langPort)).oneShot;
