var m = ~model;
var synth;
var index =0;
var trig = false;
var notes = [12,11,9,7,12,11,9,7,14,12,11,9,7,5,4,2]+24;
var note = notes[0];
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.2;

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
	synth = Synth(\noise, [\frq, 1000]);
};
//------------------------------------------------------------
~deinit = ~deinit <> {
    synth.set(\gate, 0);
};

//------------------------------------------------------------
~onEvent = {|e|
};

//------------------------------------------------------------
~next = {|d|
	// var amp = d.sensors.velocity.sum.abs.lincurve(0,0.03,0.0,1.0,-2);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.0,0.3,-3);
    var ud = (d.sensors.gyroEvent.y / pi.half).linexp(-0.8,0.9,400,10000);
    
    if(amp<0.025,{
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
        synth.set(\lag,0.01);
    });
    synth.set(\amp, amp*1.5);
    synth.set(\frq, ud);
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
	[d.sensors.gyroEvent.y / pi.half];
	// [d.sensors.rotateEvent.x, d.sensors.rotateEvent.y, d.sensors.rotateEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z] * 4;
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;


};




