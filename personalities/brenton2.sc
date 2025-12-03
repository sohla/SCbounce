var m = ~model;
var synth;
var buffer;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.9;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\pullstretchMonoQBT, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.4 ,pan=0, ff=245, gate=1|
	var pos;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	var env = EnvGen.ar(Env.adsr(0.1,0.1,1.0,3.0), gate, doneAction:2);

	var sp = Splay.arFill(2,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.05,0.77), pch,splay, envbuf, 8, 0.3, 4)  },
			1,
			1,
			0
	) ;

	var mas = HPF.ar(sp,ff.lag(0.3));
	var sig = FreeVerb.ar(mas,0.5);
	sig = Pan2.ar(sig* amp.lag(1) ,pan)* env;
	// Out.ar(out, [((0)!0 ++ sig)]);
	Out.ar(out, ((0)!4 ++ sig));

}).add;
//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/yourDNASamples/brenton/BrentonVoice_05.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQBT,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 10]);
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
	var speed= m.accelMassFiltered.lincurve(0.5,2.5,0.01,4.0,-2);
	var ff = m.accelMassFiltered.lincurve(0.0,2.5,245,2450,-1);
	var rate = m.accelMassFiltered.linlin(0,1,0.9,1.4);
	var pch = m.gyroXFiltered.linlin(-1,1,0,1).asInteger ;
	var pan = m.gyroZFiltered.linlin(-1,1,-1,1);

	if(amp < 0.01, {amp = 0});

	synth.set(\pch, pch.midiratio);
	synth.set(\speed, speed);
	synth.set(\amp, amp * 4);
	synth.set(\pan, pan);
	synth.set(\ff, ff);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
