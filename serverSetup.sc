 (

//[ Built-in Microph, Built-in Output, QU-24 Audio, Soundflower (2ch), Soundflower (64ch), Multi-Output Device, CARehearsal ]

ServerOptions.devices
ServerOptions.devices.indexOfEqual("QU-24 Audio");

o = Server.local.options;
o.inDevice = ServerOptions.devices[2];
o.outDevice = ServerOptions.devices[2];
o.numOutputBusChannels = 32;
o.numInputBusChannels = 32;
s.reboot	
s.quit
s.boot
)

ServerOptions.devices.indexOfEqual("MOTU UltraLite");
(
o = Server.local.options;
o.inDevice = ServerOptions.devices[6];
o.outDevice = ServerOptions.devices[6];
o.numOutputBusChannels = 8;
o.numInputBusChannels = 8;
o.sampleRate = nil;
s.reboot

ServerMeter.new(s, 24, 24);

(0..23)++25


(

ServerOptions.devices

o = Server.local.options;

o.inDevice = ServerOptions.devices[ServerOptions.devices.indexOfEqual("Soundflower (2ch)")];
o.numInputBusChannels = 2;

o.outDevice = ServerOptions.devices[ServerOptions.devices.indexOfEqual("MOTU UltraLite")];
o.numOutputBusChannels = 2;

Server.default.options.memSize = 8192;//2 ** 19;
s.reboot	
s.quit
s.boot



)

(

o = Server.local.options;
[o.inDevice,o.numInputBusChannels].postln;
[o.outDevice,o.numOutputBusChannels].postln;
o.sampleRate;
o.memSize = 8192 * 4;



s.latency = 0.3
s.reboot	
s.options.protocol 
s.ping(10); 

s.queryAllNodes
)


(
//Soundflower (2ch)
)