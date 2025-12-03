var m = ~model;
var synth;
var buffer;
var lastTime = 0;
// var notes = [3-12,-2,3-12-12,3]-12;
var notes = [-14,-9,-2,3,10];

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.9;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\pullstretchMonoQm2, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.4 ,pan=0, gate=1|
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1).range(0.0,0.7);
	var sp = Splay.arFill(4,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.05,0.95), pch * (0.25/2) * (2*(i+1)),splay, envbuf, 8, 0.1 * (i+1), 4)  },
			1,
			1,
			0
	) ;
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,2.0), gate, doneAction:2);
	var mas = HPF.ar(sp,45);
	var sig = Pan2.ar(mas,pan.lag(0.4))* amp.lag(1) * env;
	var fil = LPF.ar(sig,100,3).tanh;
	// Out.ar(out, ((0)!0 ++ sig));
	Out.ar(out, ((0)!0 ++ sig ++ ((0)!6) ++ fil));

}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/melSamples/mel_sing_dry-004.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQm2,[\buffer,buf,\pch,notes[0].midiratio, \amp,0.4, \div, 10]);
	});
};

~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
	});	
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMass.linlin(0,2,0.00001,1);
	var speed= m.accelMassFiltered.lincurve(0.5,2.5,0.005,0.05,-2);
	var rate = m.accelMassFiltered.linlin(0,1,0.9,1.4);
	var pch = m.gyroYFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,0,notes.size-1).round;
	var pan = m.gyroZFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-1,1);

	if(amp < 0.01, {amp = 0});

	// if(TempoClock.beats > (lastTime + 7),{
	// 	notes = notes.rotate(-1);
	// 	lastTime = TempoClock.beats;
	// });
		synth.set(\pch, notes[pch].midiratio);

	synth.set(\speed, speed);
	synth.set(\amp, amp * 1.0);
	synth.set(\pan, pan);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];

};
