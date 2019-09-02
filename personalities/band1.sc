var m = ~model;
var isOn = false;

m.midiChannel = 0;
m.accelMassThreshold = 0.5;
m.rrateMassThreshold = 0.5;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,2,7,9,5,4].stutter(8),inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.125);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.4);

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|

	m.com.root = e.note;
	m.com.dur = e.dur;
};

~onHit = {|state|

};

~onMoving = {|state|
};

~onAmp = {|v|
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var changeState = {|state|
		if(isOn != state,{
			isOn = state;
			if(isOn == true,{
				// "Note ON".postln;
				Pdef(m.ptn).set(\amp, 0.4);
			},{
				Pdef(m.ptn).set(\amp, 0.0);
				// "Note OFF".postln;
			});
		});
	};

	if(m.accelMass > 0.5,{
		changeState.(true);
	},{
		changeState.(false);
	});
};

~nextMidiOut = {|d|

	m.midiOut.control(m.midiChannel, 1, (m.rrateMassFiltered * 127 * 1.3) + 0 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|

	[m.rrateMassFiltered];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|


};
