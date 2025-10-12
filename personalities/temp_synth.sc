var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = SinOsc.ar(freq,0,0.5)!2;
    Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\simple);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|
  var notes = [28,35,40,47,52,56,59,63,64];
  var roots = [0 ,3,-4];
	var amp = m.gyroXFiltered.lincurve(-0.0,0.3,-90,-2,-9);
	var frq = m.gyroYFiltered.fold(-1,1).lincurve(-1,1,50,250,-4);
	var ni = m.gyroYFiltered.fold(-1,1).lincurve(-1,1,0,notes.size,-2);
	var ri = m.gyroZFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,roots.size,-2);

  synth.set(\amp, amp.dbamp);
  synth.set(\freq, (notes[ni.floor] + roots[ri.floor]).midicps);

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
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

  [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
