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

SynthDef(\pullstretchMonoQAA, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.003, splay = 0.4 ,pan=0, rate=1, dp=1, gate=1|
	var pos;
	// var mx,my;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,4.0), gate, doneAction:2);
	var sp = Splay.arFill(3,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.05,0.95), rate  * 0.25,splay, envbuf, 4, 0.1, 4, 1) },
			1,
			1,
			0
	) ;
	var mas = HPF.ar(sp * 2,25).distort.tanh;
 	var sig = FreeVerb.ar(mas,0.5);
	sig = Pan2.ar(sig,pan)* amp.lag(0.3) * env;
	Out.ar(out,((0)!0 ++ sig));
}).add;
//------------------------------------------------------------
~init = ~init <> {
	var path = PathName("~/Downloads/alessioSamples/andHeLikesBandToys.wav");
	postf("loading sample : % \n", path.fileName);
	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQAA,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 10]);
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

	synth.set(\rate, rate * 1.214);
	// synth.set(\dp, dp);
	synth.set(\amp, amp * 4);
	// synth.set(\pan, pan);
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.gyroZFiltered];
};
