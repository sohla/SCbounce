var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\fm_grain_drone, { |out=0, gate = 1, amp = 1, envbuf, bf=400, df = 2, imp=4|
    var freqdev = WhiteNoise.kr(df);

    var env = EnvGen.kr(
        Env([0, 1, 0], [1, 1], \sin, 1),
        gate,
        doneAction: Done.freeSelf);
	var sig = GrainFM.ar(2, Impulse.kr(imp), 0.8, bf + freqdev, bf * LFNoise1.ar(1).range(0.992,1.008), 1,
			WhiteNoise.ar(), envbuf);
	var verb = Greyhole.ar(sig, 0.2, 0.3, 0.4, 0.2, 0.3);
	sig = sig.blend(verb,0.2);
	Out.ar(out, sig * env * 0.1 * amp);
}).add;

SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = SinOsc.ar(freq,0,0.5)!2;
    Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\fm_grain_drone, [\grainBuf, -1, \bf,120, \imp, 20, \df,10]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|
  	var notes = [40,47,52,56,59,63,64];
  // var notes = [40,45,52];
	var roots = [0,24];
	var amp = m.accelMassFiltered.lincurve(0.0,0.3,-50,-2,-3);
	var ni = m.gyroYFiltered.fold(-1,1).lincurve(-0.3,0.3,0,notes.size-1,-2);
	var ri = (m.gyroZFiltered.fold(-0.5,0.5) * 2).lincurve(-1.0,1.0,0,roots.size,0);
	var imp = m.accelMassFiltered.lincurve(0.0,2.3,4,40,-1);

  	synth.set(\imp, imp);
  	synth.set(\amp, amp.dbamp);
  	synth.set(\bf, (notes[ni.floor] + roots[ri.floor]).midicps);

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
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs *  m.accelMassFiltered] * 0.5;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	[(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
