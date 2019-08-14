b = NetAddr("127.0.0.1",2345);
b.sendMsg("/acc", 200,0,0);    

o = OSCFunc({ arg msg, time, addr, recvPort;
	([msg, time, addr, recvPort]).postln;
}, '/gyrosc/rrate');

o.free;


// sending to Protopixel
( 

	var i=0;
	var delta = 1;
	var oscOut = NetAddr("127.0.0.1",2345);
	var oscIn = OSCFunc({ arg msg, time, addr, recvPort;
		([msg, time, addr, recvPort]).postln;
		delta = msg[1].asFloat;
	}, '/id/rate');

	Routine { 
	    loop {

			var r = (1.5 + sin(i * 2pi)) * 64;
			var g = (1 + cos(i * 2pi * 0.5)) * 128;
			var b = (1.5 - sin(i * 2pi)) * 64;
			var t = (i * 820).mod(250);

			i = i - 0.002;


			oscOut.sendMsg("/acc", r,g,b);
			oscOut.sendMsg("/rot", t);

			if(delta == 0, {delta = 1}); // just in case
	        (0.005 * delta).yield;

	    }
	}.play;

	CmdPeriod.doOnce({
		oscIn.free;
	});
)
 
b = NetAddr("127.0.0.1",57120);
b.sendMsg("/id/rate", 2);



NetAddr.localAddr