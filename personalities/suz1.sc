var m = ~model;
var bl=false;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.7;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\suz1, {|out=0, amp=0.8, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var li = LocalIn.ar(2);
	var sig = SinOsc.ar(freq * [1,1.007], li ,0.5);
	LocalOut.ar(sig);
    Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
  Pdef(m.ptn,
    Pbind(
      \instrument, \suz1,
      \note, Pseq([0,-2,4,5,10,12,7,5,2], inf),
	  \octave, Pseq([4,3,4,5].stutter(4), inf),
	  \root, 0,
      \decay, 0.1,
      \sustain,0.1,
	  \amp,0.8,
      \args, #[],
    )
  );
  Pdef(m.ptn).play(quant:0.2);  
};

//------------------------------------------------------------
~deinit = ~deinit <> {
  Pdef(m.ptn).remove;

};
//subgraph starting at SuperCollider timed out (subgraph_wait_fd=10, status = 0, state = Running, pollret = 0 revents = 0x0)
//------------------------------------------------------------
~next = {|d|

  var dur = m.accelMassFiltered.lincurve(0,1.0,0.3,0.02,-3);
  var roots = [0,3,5,-2];
  var ri = (d.sensors.gyroEvent.z / pi.half).lincurve(-1,1,0,roots.size).floor;
  var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1,1,3,6).floor;
  var atk = m.accelMassFiltered.lincurve(0,2.0,0.008,0.002,-1);
  var rel = m.accelMassFiltered.lincurve(0,1.0,1.8,0.002,-1);

  	Pdef(m.ptn).set(\dur, dur);
	// Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\attack, atk);
	Pdef(m.ptn).set(\release, rel);
  	Pdef(m.ptn).set(\root, 0);

 	if(m.accelMassFiltered > 0.015,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:dur);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});

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
