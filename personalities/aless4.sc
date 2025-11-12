var m = ~model;
var synth;
var buffer;

var index =0;
var trig = false;
var notes = [2];//[12,11,9,7,12,11,9,7,14,12,11,9,7,5,4,2]-15;
var note = notes[0];
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

SynthDef(\pullstretchMonoQ, {|out, amp = 1, buffer = 0, envbuf = -1, pch = 1, div=1, speed = 0.008, splay = 0.3,pan=0, gate=1, delta=0, lag=0.05, ffo=10, rfo=1|
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1).range(0.0,0.99);
  var afo = LFCub.ar(ffo,0,rfo).range(1.0 - rfo,2.0 - rfo);
	var sp = Splay.arFill(8,
		{ |i| Warp1.ar(1, buffer, lfo.linlin(0,1,0.01,0.99), pch * (1 / ((i*delta)+1)) * [1] ,splay, envbuf, 8, 0.1 * (i+1), 4)  },
			1,
			1,
			0
	) ;
	var env = EnvGen.ar(Env.adsr(0.4,0.1,0.9,2.0), gate, doneAction:2);
	var mas = HPF.ar(sp,45).tanh * amp.lag(lag) * afo;
		mas = Compander.ar(mas, mas,
						thresh: -32.dbamp,
						slopeBelow: 1,
						slopeAbove: 0.5,
						clampTime:  0.02,
						relaxTime:  0.01
				);


	Out.ar(out,Pan2.ar(mas[0],pan) * env);
}).add;
//------------------------------------------------------------
SynthDef(\noise, { |out=0, frq=1000, gate=1, amp = 0.0, atk=0.02, sus=0.8, rel=1.3, lag=0.05, pch=1|
	var env = EnvGen.ar(Env.adsr(atk,0.3,sus,rel), gate, doneAction:Done.freeSelf) * 0.5;
    var sig = DynKlank.ar(`[[50,100,200,400] * pch, [1,0.4,0.2,0.1], [1, 0.6, 0.3, 0.1]], WhiteNoise.ar(0.1));
    // var sig = WhiteNoise.ar(4);
    sig = LPF.ar(sig, frq.lag(0.3)) * env * amp.lag(lag);
	Out.ar(out, sig.tanh!2);
}).add;

//------------------------------------------------------------
~init = ~init <> {
		var path = PathName("~/Downloads/alessioSamples/ararrrr2.wav");

	// var path = PathName("~/Downloads/yourDNASamples/HK lots of teddies.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		synth = Synth(\pullstretchMonoQ,[\buffer,buf,\pch,0.midiratio, \amp,0.0, \div, 10]);
	});


};
//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.onFree({
		postf("buffer dealloc [%] \n", buffer);
		buffer.free;
	});	
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~onEvent = {|e|
};

//------------------------------------------------------------
~next = {|d|
	// var amp = d.sensors.velocity.sum.abs.lincurve(0,0.03,0.0,1.0,-2);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.0,1,-3);
	var rfo = m.accelMassFiltered.lincurve(0,2.5,0.0,1,-3);
  var ffo = m.gyroYFiltered.lincurve(-1.0,1,1,18,-3);
    
    if(amp<0.015,{
        amp=0;
        synth.set(\lag,0.8);
        if(trig, {
            trig = false;
        });
    },{
        if(trig.not, {
            trig = true;
            index = index + 1;
            notes = notes.rotate(-1);
            note = notes[0];
        });
        synth.set(\pch, note.midiratio);
        synth.set(\lag,0.1);
    });

    synth.set(\amp, amp*4);
    synth.set(\ffo, ffo);
    synth.set(\rfo, rfo);
};

//------------------------------------------------------------
~plotMin = -1;  
~plotMax = 1;
~plot = { |d,p|
	//[0.2,0.4,0.6];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.5;
	// [m.accelMass.abs - m.accelMass, m.accelMass - m.accelMass.abs];
	// [d.sensors.velocity.sum.abs * 30 ,m.accelMass];// compare these values we can get direction?

	// [d.sensors.velocity.sum.abs.lincurve(0,0.03,0,1,-2)];


	// // [((m.accelMassFiltered - m.accelMassFiltered.abs)-(m.accelMassFiltered.abs - m.accelMassFiltered)).abs, m.accelMassFiltered.abs];
	// [m.rrateMassFiltered];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[m.gyroYFiltered];
	// [d.sensors.rotateEvent.x, d.sensors.rotateEvent.y, d.sensors.rotateEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z] * 4;
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;


};




