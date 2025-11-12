var m = ~model;
var synth;
var buffer;
var lastTime = 0;
var notes = [0,-5];
m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\pullstretchMonoQBBB, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.4 ,pan=0, ff = 100|
	var pos;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	var sp = Splay.arFill(4,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.11,0.25), pch * (0.25 * (i+1)),splay, envbuf, 8, 0.3, 4)  },
			1,
			1,
			0
	) ;
	var mas = LPF.ar(sp,ff);
	var sig = FreeVerb.ar(mas,0.5);
	sig = Pan2.ar(sig[0],pan)* amp.lag(1);
	Out.ar(out, ((0)!0 ++ sig));
}).add;
//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/yourDNASamples/brenton/BrentonVoice_06.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQBBB,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 10]);
	});
};

~deinit = ~deinit <> {
	synth.free;
	buffer.free;
};


//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMassFiltered.linlin(0,2,0.00001,1);
	var speed= m.accelMassFiltered.lincurve(0.5,2.5,0.01,1,-2);
	var rate = m.accelMassFiltered.linlin(0,1,0.9,1.4);
	var ff= m.accelMassFiltered.lincurve(0.0,2.0,10,7900,1);

	if(amp < 0.03, {
		amp = 0;
		if(TempoClock.beats > (lastTime + 0.1),{
			notes = notes.rotate(-1);
			lastTime = TempoClock.beats;
		});
	});


	synth.set(\pch, notes[0].midiratio);
	synth.set(\speed, speed);
	synth.set(\amp, amp * 1);
	synth.set(\ff, ff);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	[m.gyroZFiltered.fold(-0.5,0.5).linlin(-0.5,0.5,-1,1)];
};
