var m = ~model;
var bl = [0,4,-2,2].stutter(8);
var cr = [0,5,-2,3,-4,1,-5];
var isOn = false;

m.midiChannel = 7;
m.accelMassThreshold = 0.2;
m.rrateMassThreshold = 0.2;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
	        \degree, Pseq([0,2,5,4,7,7,9,12,11], inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	var vel = 10;
	var note = [0,4,7,9,7,4,2].choose;
	if(state == true,{
		m.midiOut.noteOn(m.midiChannel, 60 + m.com.root + note + 24, vel);
		{m.midiOut.noteOff(m.midiChannel, 60 + m.com.root + note + 24, vel);}.defer(0.3);
	},{

	});
};

~onMoving = {|state|

	if(state == true,{
		Pdef(m.ptn).resume();
	},{
		Pdef(m.ptn).pause();
	});
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oo = [24,36];
	var changeState = {|state|
		if(isOn != state,{
			isOn = state;
			if(isOn == true,{
				"Note ON".postln;
				m.com.root = bl.[0];
				cr = cr.rotate(-1);
				m.midiOut.noteOn(10, 60-oo.choose  + m.com.root, 20);
				{m.midiOut.noteOff(10, 60-oo.choose  + m.com.root, 0)}.defer(0.5);
				bl = bl.rotate(-1);
			},{
				"Note OFF".postln;
			});
		});
	};

	var ga = 0;
	var amp = d.ampValue * 10;

	Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\octave, 5 + (m.rrateMassFiltered * 3).floor);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 12).reciprocal);


	if( m.accelMass > 0.8,{
		if( d.ampValue > 0.13,{
			ga = m.accelMass * amp;
		},{
		});
	},{
	});

	if(ga > 0.6,{
		changeState.(true);
	},{
		changeState.(false);
	});

};
~nextMidiOut = {|d|
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	// [(m.rrateMassFiltered * 3).ceil.mod(3)];
	[m.rrateMassFiltered.pow(3)*2, m.accelMassFiltered, m.com.rrateMass];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

	//[num,val].postln;

	// if(num == 4,{ threshold = 0.01 + (val * 0.99)});

	// threshold = threshold * 2;
	//m.midiOut.control(m.midiChannel, 65, val * 127 );

};


