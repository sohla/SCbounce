(
SynthDef(\mapexample,{arg freq=440;
	Out.ar(0,SinOsc.ar(freq,0,0.1)!2)
}).add;

SynthDef(\moda,{|bus|
	Out.kr(bus,SinOsc.ar(550,0,100,1000))
}).add;


SynthDef(\modb,{|bus|
	Out.kr(bus,SinOsc.ar(5,0,50,100))
}).add;


SynthDef(\thru,{|bus|
	Out.kr(bus,In.kr(bus).linlin(0,1000,100,600))
}).add;

)

(
	x = Synth(\mapexample);
	b = Bus.control(s);
	g = OSCFunc({ arg msg, time, addr, recvPort;
		b.set(400 + (msg[1].asFloat*300));
	}, '/gyrosc/gyro');
	x.map(\freq, b);
)


j = Synth(\thru,[\bus,b]);
j.free;

k = Synth(\modb,[\bus,b]);
k.free;


(
	g.free;
	b.free;
	x.free;
) 