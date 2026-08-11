var m = ~model;
var bl=false;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.7;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.99;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\raindrop, {
    |out=0, freq=1000, amp=0.8, pan=0, gate=1, attack=0.001, decay=0.05,
	filterFreq=3000, filterRQ=1, wobble=10,
 	reverbMix=0.2, reverbRoom=0.83, reverbDamp=0.5|

	  var sig, env, verb;
    env = EnvGen.ar(Env.perc(attack, decay), gate);
    sig = SinOsc.ar(freq * LFPar.ar(wobble,pi/2, 0.2,1)) * env;
    sig = BPF.ar(sig, filterFreq, filterRQ);
    verb = FreeVerb.ar(sig, reverbMix, reverbRoom, reverbDamp);
  	DetectSilence.ar(verb, doneAction: 2);
    Out.ar(out, PanAz.ar(2, verb, pan, 1, 2, 0.5) * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
  Pdef(m.ptn,
    Pbind(
      \instrument, \raindrop,
      \octave, 4,
      \note, Pwhite(2,40,inf),
      \attack, 0.001,
      \filterFreq, Pexprand(90, 2000, inf) * 2,  
      \filterRQ, Pwhite(0.5, 1.5, inf),
      \pan, Pwhite(-1.0, 1.0, inf),
    )
  );
  Pdef(m.ptn).play(quant:0.2);  
};

//------------------------------------------------------------
~deinit = ~deinit <> {
  Pdef(m.ptn).remove;

};

//------------------------------------------------------------
~next = {|d|

  var dur = m.gyroYFiltered.lincurve(-1.0,1.0,0.5,0.075);
  var amp = m.gyroYFiltered.lincurve(-1.0,1.0,0.0,1,-2);
  var wob = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0,1.0,0.01,14000.0,-2);
  var side  = ([d.sensors.accelEvent.y.abs + d.sensors.accelEvent.z.abs] * 0.1).lincurve(0,1.0,1.0,wob,-2);
  var dcy  = ([d.sensors.accelEvent.y.abs + d.sensors.accelEvent.z.abs] * 0.1).lincurve(0,1.0,0.05,3.0,-1);
  var verb = m.accelMassFiltered.lincurve(0,2.5,0.53,10.0,2);

  if(amp < 0.21, {amp = 0});
  Pdef(m.ptn).set(\dur, dur);
  Pdef(m.ptn).set(\amp, amp);
  Pdef(m.ptn).set(\wobble, side);
  Pdef(m.ptn).set(\decay, dcy);
  Pdef(m.ptn).set(\reverbRoom, verb);

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
// [d.sensors.accelEvent.y.abs + d.sensors.accelEvent.z.abs] * 0.1;
	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	[(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2,m.gyroYFiltered,(d.sensors.accelEvent.y.abs + d.sensors.accelEvent.z.abs) * 0.1];
  // [m.gyroYFiltered.lincurve(-1.0,1.0,0,0.4)];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
