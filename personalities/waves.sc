var m = ~model;
var synth;
var trig = false;
var lastTime = 0;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.2;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\waves22, { |out=0, gate=1, amp = 0.0, lag=1,ff=200|
    var env = EnvGen.ar(Env.asr(1.3,1.0,8 ), gate, doneAction:2);

	var spd = 0.03.rrand(0.09);//MouseY.kr(0.03,0.09);
	// var nsa = RHPF.ar(BrownNoise.ar(0.1), LFSaw.ar(spd*2,1).linexp(-1,1,200,19000));
	var nsa = RHPF.ar(BrownNoise.ar(0.3), ff.lag(lag));
	var sig = SinOsc.ar(spd,[0,1], nsa);
	Out.ar(out, sig * env * amp.lag(lag));
}).add;

//------------------------------------------------------------
~init = ~init <> {
};
//------------------------------------------------------------
~deinit = ~deinit <> {
  if(synth.isRunning,{
    synth.set(\gate, 0);
  });
};

//------------------------------------------------------------
~onEvent = {|e|
};

//------------------------------------------------------------
~next = {|d|
	// var amp = d.sensors.velocity.sum.abs.lincurve(0,0.03,0.0,1.0,-2);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.0,1.0,-3);
  var ff = m.gyroYFiltered.linexp(-1.0,1.0,200,19000);
    
    if(TempoClock.beats > (lastTime + 1),{
			lastTime = TempoClock.beats;

      if(amp<0.025,{
          // amp=0;
          // synth.set(\lag,0.8);
          if(trig, {
                trig = false;
                synth.set(\gate, 0);
          });
      },{
          if(trig.not, {
              trig = true;
              synth = Synth(\waves22);
              NodeWatcher.register(synth);
              "next".postln;
              //next
          });
      });
    });

    synth.set(\amp, amp);
    synth.set(\ff, ff);
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




