// configure to send
b = NetAddr.new("192.168.50.170", 8888);
// check we are sedning
b.sendMsg("/Config/GetConfig");
b.free

// set ID / OSC path
b.sendMsg("/Config/SetID","4");

// set diestination IP and Port
b.sendMsg("/Config/RequestStream",192,168,50,177,57120);

// check data is coming thru
OSCFunc.trace(true)
OSCFunc.trace(false)

// set LED
b.sendMsg("/Config/SetLED",1,1,0,255);



n = NetAddr.new("192.168.50.177", 57120);
o = OSCFunc({ arg msg, time, addr, recvPort; [msg, time, addr, recvPort].postln; }, '/4/Config');
o.free;


