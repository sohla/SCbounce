var m = ~model;
var synth;
var synth2;

var lastTime=0;
var notes = [0,0,7,0,0,0,7,0,7,5,0,0,0,7,0,0,7,5,0,0,0,-2,7,-2,7,5,-2,-2,7,5,-2,-2,-2,-4,7,5,-4,-4,7,5,-2,-2,7,5,-2,7,5];
//[1,1,1,2].dup(4).flatten
var roots = [0,0,0,0,-12];//.dup(8).flatten ++ [12,12,12,12].dup(8).flatten;
var currentNote = notes[0];
var currentRoot = roots[0];
notes.size.postln;
m.accelMassFilteredAttack = 0.2;
m.accelMassFilteredDecay = 0.99;

//------------------------------------------------------------
SynthDef(\timWind1, { |out, freq=111, gate=0, amp = 0.3, pchx=0|
	var env = EnvGen.ar(Env.asr(4,1.0,7.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.3, 0.99);
	// var sig = Saw.ar(frq.lag(2),0.3 * env * amp.lag(1));
	var trig = PinkNoise.ar(0.01) * env * follow.lag(2);
	var sig =  DynKlank.ar(`[[freq, freq*2].lag(3), [1,0.4,0.3], [2, 1, 1, 1]], trig);
  var tone = SinOsc.ar([freq * 4, freq * 0.5], LFNoise2.ar(1,10,10),[0.1,0.5 ]* env);
	var dly = DelayC.ar(sig + tone,0.03,[0.02,0.027]);
	Out.ar(out, dly);
}).add;


~init = ~init <> {
};

~deinit = ~deinit <> {
    synth.free;
};

//------------------------------------------------------------
~next = {|d|

	var move = m.accelMassFiltered.linlin(0,3,0,1.5);
  var a = m.accelMassFiltered * 0.5;
  
	if(a<0.02,{a=0});
	if(a>0.9,{a=1.5});
	if(move > 0.02, {
    synth.set(\amp, a * 0.4);
  	if(TempoClock.beats > (lastTime + 0.35),{
			lastTime = TempoClock.beats;
			notes = notes.rotate(-1);
			currentNote = notes[0];
			roots = roots.rotate(-1);
			currentRoot = roots[0];
			m.com.root = currentRoot;
      // synth.set(\freq, (36 + currentNote).midicps); 
			synth = Synth(\timWind1, [
				\freq, (36 + currentNote + currentRoot).midicps,
				\gate, 1,
				\amp, 0.7,
			]);
			synth.server.sendBundle(0.3,[\n_set, synth.nodeID, \gate, 0]);
		});
	});  
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered,d.sensors.gyroEvent.y * 0.1];
	[d.sensors.rotateEvent.y];
	// [d.sensors.rotateEvent.x, d.sensors.rotateEvent.y, d.sensors.rotateEvent.z];
	
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
