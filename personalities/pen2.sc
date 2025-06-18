var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.07;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;


//------------------------------------------------------------
SynthDef(\sheet1, { |out, frq=111, gate=0, amp = 0, pchx=0|
	var env = EnvGen.ar(Env.asr(0.3,1.0,1.03), gate, doneAction:Done.freeSelf);
	var follow = amp;//Amplitude.kr(amp, 0.3, 0.5);
	// var sig = Saw.ar(frq.lag(2),0.3 * env * amp.lag(1));
	var trig = PinkNoise.ar(0.01) * env * follow;
	var sig =  DynKlank.ar(`[([30,37,42,46,49]+pchx+3).midicps.lag(6), nil, [2, 1, 1, 1]], trig);
	var dly = DelayC.ar(sig,0.03,[0.02,0.027]);
	Out.ar(out, dly);
}).add;

~init = ~init <> {
	synth = Synth(\sheet1, [\frq, 140.rrand(80), \gate, 1]);
};

~deinit = ~deinit <> {
	// synth.free;
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|

	var a = m.accelMassFiltered * 0.5;
	var f = 50 + (m.accelMassFiltered * 100);
	var pchs = [0];
  // var oct = (d.sensors.gyroEvent.x/pi).linlin(-0.5,0.2,7.0,4.0); //up down
	// var oct = (d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,4.0,8.0); //left right

	var i = (d.sensors.gyroEvent.x/pi).linlin(-0.5,0.5,0.0,1.0) * (pchs.size);
	// pchs[i.floor].postln;
  var range =  d.sensors.gyroEvent.x/pi;

  // if(range > -0.52, {
  //   if(range < -0.42, {
    	if(a<0.03,{a=0});
	    if(a>0.9,{a=0.7});

  //   },{
  //     a = 0;
  //   })
  // },{
  //   a=0;
  // });

	synth.set(\amp, a * 0.1);
	synth.set(\pchx,pchs[i.floor]);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	[d.sensors.gyroEvent.x/pi];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
