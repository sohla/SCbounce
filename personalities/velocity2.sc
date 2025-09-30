var m = ~model;
var synth;
var index =0;
var trig = false;
var notes = [0,7,16,0,7,16,0,7,16,0,7,16,0,7,17,0,7,17,0,7,17,0,7,17];
var note = notes[0];
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.3;
m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.2;

//------------------------------------------------------------
SynthDef(\noise, { |out=0, frq=10000, gate=0, amp = 0, atk=0.02, sus=0.9, rel=0.9, lag=0.05, pch =1|
	// var env = EnvGen.ar(Env.asr(atk,sus,rel), gate, doneAction:Done.freeSelf);
	var env = EnvGen.ar(Env.adsr(atk,0.03,sus,rel), gate, doneAction:Done.freeSelf);
    var sig = DynKlank.ar(`[[50,100,200,400] * pch, [1,0.4,0.2,0.1], [1, 0.6, 0.3, 0.1]], WhiteNoise.ar(0.1));

    sig = LPF.ar(sig, frq.lag(0.3)) * env * amp.lag(lag);
	Out.ar(out, sig!2);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\noise, [\frq, 1000, \gate, 1]);

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
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.0,0.2,-3);
    var ud = (d.sensors.gyroEvent.z / pi).lincurve(-0.8,0.5,13000,400,-2);
    

    if(amp<0.03,{
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
            synth.set(\pch, note.midiratio);
        });
        synth.set(\lag,0.01);
    });

    synth.set(\amp, amp);
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
	[d.sensors.gyroEvent.z] / pi;
	// [d.sensors.rotateEvent.x, d.sensors.rotateEvent.y, d.sensors.rotateEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z] * 4;
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;


};




