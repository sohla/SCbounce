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
    |out=0, freq=111,  amp=0.3|
	var sig = SinOsc.ar(freq);
    Out.ar(out, Pan2.ar(sig, 0, amp));
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
	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	var state = false;
	var synth;
	o = OSCFunc({ |msg, time, addr, recvPort|
	var val = msg[2];
	if(val >= 150,{
		if(state == false,{
			state = true;
			synth = Synth(\pluckedString, [\freq, 150.rrand(50), \gate, 1]);
		},{
			synth.set(\filterFreq,val.linexp(150,3500,60,7000));
			[msg, time, addr, recvPort].postln;
			// state.postln;

		});
	});
	if(val < 150, {
		if(state == true, {
			state = false;
			synth.set(\gate,0);
		});
	});
	// a.set(\freq,msg[2].linexp(1,3300,100,800));


},'/60:01:E2:E2:27:48/AnalogIn');




)
(
o.free;
a.free;
)




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
