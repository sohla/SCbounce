(
o = OSCFunc({ |msg, time, addr, recvPort|
	[msg, time, addr, recvPort].postln;
},'/60:01:E2:E2:27:48/DigiIn');
)
o.free;


b = NetAddr.new("192.168.50.33", 8888);
b.sendMsg("/60:01:E2:E2:27:48/DigiOut", 1,0);


(
	b = NetAddr.new("192.168.50.33", 8888);
	Pdef(\test_pattern,
		Pbind(
		\dur, 0.1,
		\pin, Pseq([0,1], inf),
		\state, Pseq([0,1,1,0], inf),
		\func, Pfunc({|e|
			b.sendMsg("/60:01:E2:E2:27:48/DigiOut", e.pin, e.state);

			e.postln;
		});
		)
	);
)

Pdef(\test_pattern).play;
Pdef(\test_pattern).stop

(
SynthDef(\basicOsc, {
    |out=0, freq=1000,  amp=0.3|
	var sig = SinOsc.ar(freq);
    Out.ar(out, Pan2.ar(sig, 0, amp));
}).add;
)

(
o = OSCFunc({ |msg, time, addr, recvPort|
	[msg, time, addr, recvPort].postln;
},'/60:01:E2:E2:27:48/AnalogIn');
)
o.free;





OSCFunc.trace(true)
OSCFunc.trace(false)


(
var devices = Dictionary();
	var func = { |msg, time, addr|
	    if(msg[0] != '/status.reply') {
			if(msg[0].asString.split($/).last.contains("IMUFusedData"),{
			var macAddress= msg[0].asString.split($/)[1];
				"time: % sender: %\nmessage: %\n".postf(time, addr.port, macAddress);
			// thisProcess.removeOSCRecvFunc(func);
			// devices.put(macAddress,(\a:123));

			});
		};
	};
	thisProcess.addOSCRecvFunc(func);


);
​




(
l = 0;


f = { |msg, time, addr|
	    if(msg[0] != '/status.reply') {
			if(msg[0].asString.split($/).last.contains("IMUFusedData"),{
			var macAddress= msg[0].asString.split($/)[1];
			// "time: % sender: %\nmessage: %\n".postf(time, addr.port, macAddress);
			"time: % dif: %\n".postf(time, (time - l).round(1e-4));
			l = time;

			// thisProcess.removeOSCRecvFunc(func);
			// devices.put(macAddress,(\a:123));

			});
		};
	};
	thisProcess.addOSCRecvFunc(f);
)

 thisProcess.removeOSCRecvFunc(f);
