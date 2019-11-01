var m = ~model;
var isOn = false;

m.midiChannel = 0;
m.accelMassThreshold = 0.1;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,2,7,9,5,4,-2,2,5,4].stutter(8),inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\octave,Prand([3,4,5,6,7],inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.125);
	//Pdef(m.ptn).set(\octave,Prand([4,5,6,7],inf));
	Pdef(m.ptn).set(\amp,0.0);

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|

	// m.com.root = e.note;
	// m.com.dur = e.dur;
};

~onHit = {|state|
	var vel = 60;

	if(state == true,{
		m.midiOut.noteOn(5, 60, vel);
	},{
		m.midiOut.noteOff(5, 60, vel);
	});
};

~onMoving = {|state|
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

	if(m.accelMass > 0.1,{
		changeState.(true);
	},{
		changeState.(false);
	});
};

~nextMidiOut = {|d|

	m.midiOut.control(m.midiChannel, 1, (m.accelMassFiltered * 127 * 0.6) + 0 );
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
