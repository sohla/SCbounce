var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var d = ~device;

m.midiChannel = 0;

//------------------------------------------------------------
// ~outBus convention — every cotf personality's SynthDefs/Pbinds/Synths
// route audio to `ob` (captured above), not a hardcoded bus. `ob` is safe
// to read even inside topEnvironment.use{} blocks, unlike ~outBus itself.
// Follow this shape when this template becomes a real personality:
//
// SynthDef(\example, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03,
//     sustain=0.8, release=0.59, gate=1|
// 	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
// 	var sig = SinOsc.ar(freq,0,1)!2;
// 	Out.ar(out, sig * env * amp);
// }).add;
//
// Pbind style:
// 	Pbind(\instrument, \example, \out, ob, \dur, 1, \freq, 440, \amp, 0.2);
//
// Direct Synth style:
// 	Synth(\example, [\out, ob, \freq, 440, \amp, 0.2]);
//------------------------------------------------------------

//------------------------------------------------------------
// intial state
//------------------------------------------------------------

~init = ~init <> {


};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|


};

~onMoving = {|state|
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

};

~nextMidiOut = {|d|

};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|

};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};


