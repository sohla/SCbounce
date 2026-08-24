var m = ~model;
var synth;
var index =0;
var trig = false;
var notes = [0,7,16,0,7,16,0,7,16,0,7,16,0,7,17,0,7,17,0,7,17,0,7,17];
// var notes = [-5] + 24;
var note = notes[0];
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.04;
m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.02;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\noise, { |out=0, frq=10000, gate=0, amp = 0, atk=0.02, sus=0.9, rel=1.4, lag=0.05, pch =1|
	// var env = EnvGen.ar(Env.asr(atk,sus,rel), gate, doneAction:Done.freeSelf);
	var env = EnvGen.ar(Env.adsr(atk,0.03,sus,rel), gate, doneAction:Done.freeSelf);
   var sig = DynKlank.ar(`[[50,100,200,400] * pch, [1,0.4,0.2,0.1], [1, 0.6, 0.3, 0.1]], WhiteNoise.ar(0.1));
    // var sig = WhiteNoise.ar(0.5);
    var tone = SinOsc.ar(100 * pch,0,2).tanh;

    sig = LPF.ar(sig + tone, frq.lag(0.3)) * env * amp.lagud(0.007,0.01);
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

    // var amp = m.gyroYFiltered.lincurve(-1.0,1.0,0.0,0.02,-2);
    // var wob = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0,1.0,0.01,14000.0,-2);

	var amp = m.accelMassFiltered.lincurve(0,0.1,0.0,0.1,-3);
    // var ud = m.gyroYFiltered.linexp(-0.8,0.9,100,400);
	var ud = m.accelMassFiltered.lincurve(0,0.1,800,11000,-2);
    var dur = m.gyroYFiltered.lincurve(-1.0,1.0,0.5,0.075);
	var notes = [0,4,7,11,14,17];
	var ni = ((d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2.0).lincurve(-1.0,1.0,0,notes.size-1,-2).floor;


    if(amp<0.001,{
        amp=0;
        synth.set(\lag,0.1);
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
        synth.set(\lag,0.3);
    });

    synth.set(\amp, amp);
    synth.set(\frq, ud);
    notes[ni].midiratio.postln;
    synth.set(\pch, notes[ni].midiratio);
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




