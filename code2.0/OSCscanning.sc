(
o = OSCFunc({ |msg, time, addr, recvPort|
	// [msg, time, addr, recvPort].postln;
	msg[8].postln;
},'/60:01:E2:E2:27:48/I2C');
)
o.free
(
o = OSCFunc({ |msg, time, addr, recvPort|
	[msg, time, addr, recvPort].postln;
},'/60:01:E2:E2:27:48/IMUFusedData');
)
o.free;
o.free;

(
o = OSCFunc({ |msg, time, addr, recvPort|
	[msg, time, addr, recvPort].postln;
},'/60:01:E2:E2:27:48/DigiIn');
)
o.free;


b = NetAddr.new("192.168.50.33", 8888);
b.sendMsg("/60:01:E2:E2:27:48/DigiOut", 0,0);


(
	b = NetAddr.new("192.168.50.33", 8888);
	Pdef(\test_pattern,
		Pbind(
		\dur, 0.1,
		\pin, Pseq([0,1], inf),
		\state, Pseq([0,1,1,0], inf),
		\func, Pfunc({|e|
			b.sendMsg("/60:01:E2:E2:27:48/DigiOut", e.pin, e.state);
			// e.postln;
		});
		)
	);
)

Pdef(\test_pattern).play;
Pdef(\test_pattern).stop

(

	SynthDef(\pluckedString, {
	    |freq=440, amp=1, attack=0.001, decay=1, sustain = 1, release = 1, pan=0, filterFreq=2000, filterRes=0.05, phs=50, gate=1|
	    var sig, exciter, delay, env, tone;

		exciter = Impulse.ar(1, SinOsc.ar(phs).range(50,600));
	    delay = freq.reciprocal;
	    sig = CombL.ar(exciter, delay, delay, decay);
	    sig = sig + (SinOsc.ar(freq * 2) * 0.05);
	    sig = sig + (SinOsc.ar(freq * 3) * 0.03);
	    sig = RLPF.ar(sig, filterFreq, filterRes);
	    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
		tone = LFTri.ar([freq,freq * 1.007], 0,0.2);
		sig = (sig + tone) * env * amp;
		sig = LeakDC.ar(sig);
	    Out.ar(0, Pan2.ar(sig, pan));
	}).add;
)




(
	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	var state = 0;
	var synth;
	o = OSCFunc({ |msg, time, addr, recvPort|

	var val = msg[2];

	if(msg[1] == 3,{
	if(val >= 200,{
		if(state == 0,{
			state = 1;
			synth = Synth(\pluckedString, [\freq, 150.rrand(50), \gate, 1]);
		},{
			synth.set(\filterFreq,val.linexp(150,3500,60,500));
			[state,msg[2], time, addr, recvPort].postln;

		});
	},{
			// val.postln;
		if(state == 1, {
			state = 0;
			synth.set(\gate,0);
		});

	});
	});
	// a.set(\freq,msg[2].linexp(1,3300,100,800));


},'/60:01:E2:E2:27:48/AnalogIn');



)
(
o.free;
)

(
o = OSCFunc({ |msg, time, addr, recvPort|
	[msg, time, addr, recvPort].postln;
},'/60:01:E2:E2:27:48/AnalogIn');
)
o.free;


(

	SynthDef(\simpleSin, {
	    |freq=40, amp=1, attack=0.001, decay=1, sustain = 1, release = 1, pan=0, gate=1|
		var in = LocalIn.ar(1);
	var sig = SinOsc.ar(freq.lag(0.2), in *6,0.2);
	    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
		LocalOut.ar(sig);
	    Out.ar(0, Pan2.ar(sig, pan, env));
	}).add;

	SynthDef(\pluckedString, {
	    |freq=440, amp=1, attack=0.001, decay=1, sustain = 1, release = 1, pan=0, filterFreq=2000, filterRes=0.05, phs=50, gate=1|
	    var sig, exciter, delay, env, tone;

		exciter = Impulse.ar(1, SinOsc.ar(phs).range(50,600));
	    delay = freq.reciprocal;
	    sig = CombL.ar(exciter, delay, delay, decay);
	    sig = sig + (SinOsc.ar(freq * 2) * 0.05);
	    sig = sig + (SinOsc.ar(freq * 3) * 0.03);
	    sig = RLPF.ar(sig, filterFreq, filterRes);
	    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
		tone = LFTri.ar([freq,freq * 1.007], 0,0.2);
		sig = (sig + tone) * env * amp;
		sig = LeakDC.ar(sig);
	    Out.ar(0, Pan2.ar(sig, pan));
	}).add;
)


(
var synth = Synth(\simpleSin);
var psynth;
var 	b = NetAddr.new("192.168.50.33", 8888);

o = OSCFunc({ |msg, time, addr, recvPort|
	// [msg, time, addr, recvPort].postln;
	var val = msg[8];

	if(val > 5,{
		if(val < 128,{
			val.postln;
			synth.set(\freq, val.linexp(1,127,40,800));
		});
	});
},'/60:01:E2:E2:27:48/I2C');



p = OSCFunc({ |msg, time, addr, recvPort|
	[msg, time, addr, recvPort].postln;
	if(msg[1] == 2,{
		if(msg[2] == 1,{
			psynth = Synth(\pluckedString, [\freq, 150.rrand(50), \gate, 1]);
			msg.postln;
		});
		if(msg[2] == 0,{
			psynth.set(\gate,0);
		});
	});
	if(msg[1] == 3,{
		if(msg[2] == 1,{
			psynth = Synth(\pluckedString, [\freq, 450.rrand(250), \gate, 1]);
		});
		if(msg[2] == 0,{
			psynth.set(\gate,0);
		});
	});

},'/60:01:E2:E2:27:48/DigiIn');
	Pdef(\test_pattern,
		Pbind(
		\dur, 0.1,
		\amp, 0,
		\pin, Pseq([0,1], inf),
		\state, Pseq([0,1,1,0], inf),
		\func, Pfunc({|e|
			b.sendMsg("/60:01:E2:E2:27:48/DigiOut", e.pin, e.state);
			// e.postln;
		});
		)
	).play;

)
o.free














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
			var timeDif = (time - l).round(1e-4) * 1000;

			//"time: % dif: % : %\n".postf(time, (time - l).round(1e-4) * 1000, msg);
			if(timeDif > 20.0, { "time: % dif: % : %\n".postf(time, (time - l).round(1e-4) * 1000, msg) });

			l = time;

			// thisProcess.removeOSCRecvFunc(func);
			// devices.put(macAddress,(\a:123));

			});
		};
	};
	thisProcess.addOSCRecvFunc(f);
)
thisProcess.removeOSCRecvFunc(f);