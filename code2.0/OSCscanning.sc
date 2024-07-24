(
o = OSCFunc({ |msg, time, addr, recvPort|
	[msg, time, addr, recvPort].postln;
});
)
o.free;


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
