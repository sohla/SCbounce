// sanity check
NetAddr.localAddr

// configure to send
b = NetAddr.new("192.168.50.94", 8888);

// set LED
b.sendMsg("/Config/SetLED",1,1,0,255);

// set ID / OSC path
b.sendMsg("/Config/SetID","1");

// set diestination IP and Port
b.sendMsg("/Config/RequestStream",192,168,50,48,57120);

// check data is coming thru
OSCFunc.trace(true)
OSCFunc.trace(false)


// check we are sedning
b.sendMsg("/Config/GetConfig");
b.free
n = NetAddr.new("192.168.50.177", 57120);
o = OSCFunc({ arg msg, time, addr, recvPort; [msg, time, addr, recvPort].postln; }, '/4/Config');
o.free;


