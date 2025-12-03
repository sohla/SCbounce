var m = ~model;
var synth;
var buffer;
var lastTime = 0;
var notes = [2,0,4,-3,-5,0,4,-3] - 0.23;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.8;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\bufGrain, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=5.2, gate=1,cutoff=20000, rq=0.8, rezf=200|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sub = LFTri.ar(66*rate*4,0,0.1);
	var sig = Splay.arFill(4,{|i|
			Warp1.ar(2, bufnum, start, rate * (i+1) , 0.5, windowRandRatio:0.3)
		},1,1,0);
    sig = RLPF.ar(sig, rezf.lag(1), rq) + sub.tanh;
		sig = LPF.ar(sig, 1000);
		sig = sig * env * amp.lag(2);
    // Out.ar(out, ((0)!0 ++ sig));
	Out.ar(out, ((0)!0 ++ sig ++ ((0)!6) ++ sig));

}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/melSamples/mel_sing_dry-005.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\bufGrain,[\bufnum,buf, \rate, 0.25, \gate, 1 ]);
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
	
	var amp = m.accelMassFiltered.linlin(0,2,0.00001,1);
	var start = m.gyroYFiltered.lincurve(-1.0,1.0,0.0,1.0,0);
	var rezf = m.gyroZFiltered.lincurve(-1.0,1.0,150,11600,0);

	if(amp < 0.001, {
		amp = 0;		
	});
	if(TempoClock.beats > (lastTime + 7),{
		notes = notes.rotate(-1);
		lastTime = TempoClock.beats;
	});


	synth.set(\rezf, rezf);
	synth.set(\start, start);
	synth.set(\amp, amp * 1.0);
	synth.set(\rate, 0.25 * (notes[0].midiratio));

};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.x/pi];
};
