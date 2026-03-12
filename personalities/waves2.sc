var m = ~model;
var buffers;
var bi = 0;
var dur = 0.3;
var synthID;
var isLoaded = false;
var lastTime = 0;
var bgWaveBuffer1;
var bgWaveSynth1;
var bgWaveBuffer2;
var bgWaveSynth2;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\waveSampler, {|bufnum=0, out=0.5, amp=0.5, rate=1, start=0, pan=0, freq=440,
	attack=0.01, decay=0.1, sustain=0.3, release=5.2, gate=1,cutoff=20000, rq=0.9|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
	// sig = RLPF.ar(sig, cutoff, rq);// + osc;
	sig = Balance2.ar(sig[0], sig[1], pan, amp);
	sig = LeakDC.ar(sig * env);
	Out.ar(out, sig);
}).add;

SynthDef(\looper, {|bufnum=0, out=0, amp=1.0, rate=1, start=0, pan=0, freq=440,
	attack=9.1, decay=0.1, sustain=0.99, release=4.2, gate=1,cutoff=20000, rq=0.9|
	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 1);
	// sig = RLPF.ar(sig, cutoff, rq);// + osc;
	sig = Pan2.ar(sig, pan, amp);
	sig = LeakDC.ar(sig * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var folder = PathName("~/Downloads/waveSamples/oneshots");
	var bgWave1 = PathName("~/Downloads/waveSamples/bgs/wave_bg_16.wav");
	var bgWave2 = PathName("~/Downloads/waveSamples/bgs/wave_bg_splashes_16.wav");
	
	postf("loading samples : % \n", folder);

	buffers = folder.entries.collect({ |path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			buf.normalize;
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i,{   
				"samples loaded".postln;
				isLoaded = true;
			});
		});
	});

	bgWaveBuffer1 = Buffer.read(s, bgWave1.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		bgWaveSynth1 = Synth(\looper, [\bufnum, buf, \amp, 0.4]);
	});
	bgWaveBuffer2 = Buffer.read(s, bgWave2.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		bgWaveSynth2 = Synth(\looper, [\bufnum, buf, \amp, 0.3]);
	});

};

//------------------------------------------------------------
~deinit = ~deinit <> {

	bgWaveSynth1.onFree({
		postf("free synth [%] & buffer dealloc [%] \n", bgWaveSynth1, bgWaveBuffer1);
		bgWaveBuffer1.free;
	});
	bgWaveSynth1.set(\gate, 0);
	
	bgWaveSynth2.onFree({
		postf("free synth [%] & buffer dealloc [%] \n", bgWaveSynth2, bgWaveBuffer2);
		bgWaveBuffer2.free;
	});
	bgWaveSynth2.set(\gate, 0);

	{
		buffers.do({|buf|
			postf("free buffer [%] \n", buf);
			buf.free;
		});
	}.defer(4.5);//more than the release of the synth 
};

//------------------------------------------------------------
~next = {|d|
  var amp = m.accelMassFiltered.lincurve(0,2.0,0.1,1, 2);

	if(isLoaded==true,{
		if(TempoClock.beats > (lastTime + 0.2),{
			lastTime = TempoClock.beats;
			if(m.accelMass>0.1,{
				synthID = s.nextNodeID;
				s.sendMsg("/s_new", "waveSampler", synthID, 0, 1, \bufnum, buffers[0].bufnum, \amp, amp); // group 1
				s.sendBundle(0.2,["n_set", synthID, \gate, 0]);
				buffers = buffers.rotate(-1);
			});
		});
	});
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right

  [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
