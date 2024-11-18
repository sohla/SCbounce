var m = ~model;
var synth;
var state = false;
var first = true;
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.99;


SynthDef(\simpleSynth, { 
  |out=0, amp=0.5, att=0.1, dcy=0.1, sus=0.7, rel=1.0, gate=1, freq=111|
  var env = EnvGen.ar(Env.adsr(att, dcy, sus, rel), gate, doneAction: 2);
  var sig = SinOsc.ar(freq, LFNoise2.ar([100,90],0.7),1);
  Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------

//------------------------------------------------------------
~init = ~init <> {

  Ndef(m.ptn, Pbindef(\ap,
    \instrument, \simpleSynth,
    \dur, Pseq([0.2, 0.1, 0.1, 0.2, Rest(0.1), 0.1] * 4, inf) ,
    \note, Pseq([0,3,8,7,12,5,2,0,3], inf),
    \octave, Pseq([4,6,5,7].stutter(1), inf),
    \root, Pseq([0,3,1,-2].stutter(36), inf),
    \amp, 0.2,
    \att, Pwhite(0.001,0.01, inf),
    \dcy, 0.01,
    \sus, 0.2,
    \rel, Pwhite(1,3,inf),
    // \func, Pfunc({|e| ~onEvent.(e)})
  ).quant_(0.1));
  Ndef(m.ptn).fadeTime = 0;

};
//------------------------------------------------------------
~deinit = ~deinit <> {
  Ndef(m.ptn).clear(4);// removes everything
};

//------------------------------------------------------------
~onEvent = {|e|
  e.postln;
};

//------------------------------------------------------------
~next = {|d|
	var dur = m.accelMassFiltered.linexp(0,2.5,0.5,0.05);
  if(m.accelMassFiltered > 0.15,{
		if( state == false,{
      state = true;
      if(first,{
        first = false;
			  Ndef(m.ptn).play(fadeTime:3);
      },{
			  Ndef(m.ptn).resume();
      });
		});
	},{
		if( state == true,{
      state = false;
			Ndef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
};




