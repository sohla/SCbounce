// sanity check
NetAddr.localAddr

// configure to send
b = NetAddr.new("192.168.50.78", 8888);
b = NetAddr.new("10.1.1.3", 8888);


// set LED
b.sendMsg("/Config/SetLED",4,0,2,255);

// set ID / OSC path
b.sendMsg("/Config/SetID","4");

// set diestination IP and Port
b.sendMsg("/Config/RequestStream",192,168,50,1,57120);

// check data is coming thru
OSCFunc.trace(true)

OSCFunc.trace(false)


// check we are sedning
b.sendMsg("/Config/GetConfig", 57120);
b.free
n = NetAddr.new("192.168.50.177", 57120);
o = OSCFunc({ arg msg, time, addr, recvPort; [msg, time, addr, recvPort].postln; }, '/5/Config');
o.free;




OSCFunc.trace(true)
OSCFunc.trace(false)

o = OSCFunc({ arg msg, time, addr, recvPort; [msg, time, addr, recvPort].postln; }, '/1/AnalogIn');
o.free;

