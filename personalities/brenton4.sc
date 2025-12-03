var m = ~model;
var synth;
var buffer;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.08;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\bufGrain, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.8, release=5.2, gate=1,cutoff=20000, rq=1, rezf=200, lfof = 10|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:2);
	var sub = LFTri.ar(130/4,0,0.1).tanh;
	var sig = Splay.arFill(7,{|i|
		Warp1.ar(2, bufnum, start, rate * (i+1) * 0.5, 0.3, windowRandRatio:0.3)},
	1,1,0);
	var lfo = LFTri.ar(lfof);
    sig = RLPF.ar(sig, cutoff, rq) + sub;
	sig = Resonz.ar(sig, rezf.lag(0.4) + lfo.range(0,900), 0.5, 5) * lfo.range(0.2,1);
	sig = sig * env * amp.lag(0.9);
    // Out.ar(out, [((0)!0 ++ sig)]);
	Out.ar(out, ((0)!4 ++ sig ++ ((0)!2) ++ (sig*0.2)));
	

}).add;

//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/yourDNASamples/brenton/BrentonVoice_09.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\bufGrain,[\bufnum,buf, \rate, 1.1/2, \gate, 1 ]);
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
	var rezf = m.gyroYFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,130,260*3,0);
	var start = m.gyroZFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0.28,0.4333,0);
	var lfof = m.rrateMassFiltered.lincurve(0,0.5,0.3,15,-2);

	if(amp < 0.04, {amp = 0});

	synth.set(\rezf, rezf);
	synth.set(\start, start);
	synth.set(\amp, amp * 1.0);
	synth.set(\rate, 2.midiratio);
	synth.set(\lfof, lfof);

};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.rrateMassFiltered];
};
