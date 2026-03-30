var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.3;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\inabottle, { |out, frq=111, gate=0, amp = 0, dust=100, tone = 0.8, bits = 0.01|
	var env = EnvGen.ar(Env.asr(1.3,1.0,4.0), gate, doneAction:Done.freeSelf);
  var sig = GVerb.ar(
		PitchShift.ar(
			Splay.ar({Dust.ar(dust)}!10)
			,0.1
			,bits
			,mul:2
			),
		1.65,//pitch
		2,
		tone
	);
	Out.ar(out, sig * amp * env);
}).add;



//------------------------------------------------------------
~init = ~init <> {
	synth = Synth(\inabottle , [\gate, 1]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|
  var amp = m.accelMassFiltered.lincurve(0.0,2.4,-13,-4,-7);
  var pos = (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2;
  var tone = pos.lincurve(-1,1,0.1,0.91,-3);
  var dust = m.rrateMassFiltered.lincurve(0,0.2,1,100,-3);
  var bits = m.accelMassFiltered.lincurve(0.0,1.1,0.01,0.03,-3);

synth.set(\amp, amp.dbamp);
synth.set(\tone, tone);
synth.set(\dust, dust);
synth.set(\bits, bits);


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
