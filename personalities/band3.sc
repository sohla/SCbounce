var m = ~model;
var isOn = false;

m.midiChannel = 2;
m.accelMassThreshold = 0.9;
m.rrateMassThreshold = 0.05;
//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	
~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			 \note, Pseq([0,1,2,3,4,5,6,7],inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.8);
	Pdef(m.ptn).set(\root,0);

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

~onAmp = {|v|

};
//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = ((0.2 + m.accelMassFiltered.cubed) * 25).mod(2).floor;

	var changeState = {|state|
		if(isOn != state,{
			isOn = state;
			if(isOn == true,{
				// "Note ON".postln;
				Pdef(m.ptn).set(\amp, 0.8);
			},{
				Pdef(m.ptn).set(\amp, 0.0);
				// "Note OFF".postln;
			});
		});
	};

	Pdef(m.ptn).set(\dur,m.com.dur);

	if(m.accelMass > 0.1,{
		changeState.(true);
	},{
		changeState.(false);
	});

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 3, m.rrateMassFiltered * 127 );
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
