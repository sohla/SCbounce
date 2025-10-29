var m = ~model;
var synth;
var buffer;

m.accelMassFilteredAttack = 0.94;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


//------------------------------------------------------------
SynthDef(\monoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.0, release=2.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan.lag(2),  env);
		sig = Compander.ar(sig, sig,
						thresh: -32.dbamp,
						slopeBelow: 1,
						slopeAbove: 0.5,
						clampTime:  0.02,
						relaxTime:  0.01
				);
    Out.ar(out, sig * amp);
}).add;

SynthDef(\pullstretchMonoQ, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.003, splay = 0.4 ,pan=0, rate=1, dp=1, gate=1|
	var pos;
	// var mx,my;
	var sp;
	var mas;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,4.0), gate, doneAction:2);

	sp = Splay.arFill(3,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.05,0.95), rate * (1/(i+dp)) * 0.5,splay, envbuf, 4, 0.1, 4, 1) },
			1,
			1,
			0
	) ;


	mas = HPF.ar(sp * 2,45).distort.tanh;
  mas = FreeVerb.ar(mas,0.5);

	Out.ar(out,Pan2.ar(mas[0],pan)* amp.lag(0.3) * env);
}).add;
//------------------------------------------------------------
~init = ~init <> {
	// var path = PathName("~/Downloads/yourDNASamples/HK laughing2-glued.wav");
	// var path = PathName("~/Downloads/yourDNASamples/violin/Violin_04.wav");
		var path = PathName("~/Downloads/alessioSamples/andHeLikesBandToys.wav");

	// var path = PathName("~/Downloads/yourDNASamples/HK lots of teddies.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQ,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 10]);
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
	var speed= m.accelMassFiltered.lincurve(0.5,2.5,0.01,2,-2);
	var rate = m.gyroZFiltered.linlin(-1,1,1,2).round;
	var pan = m.gyroZFiltered.linlin(-1,1,-1,1);
	// var dp = m.gyroXFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,1,2,-1);

	if(amp < 0.01, {amp = 0});

	synth.set(\rate, rate);
	// synth.set(\dp, dp);
	synth.set(\amp, amp * 4);
	synth.set(\pan, pan);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.gyroZFiltered];
};
