var m = ~model;
var isOn = false;
var cr = [0];

m.midiChannel = 3;
m.accelMassAmpThreshold = 0.5;
m.rrateMassThreshold = 0.01;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0],inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.18);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.0);

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq([0,1,2,3,4,5,6,7],inf)
	));
	Pdef(m.ptn).play();
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
	m.com.dur = e.dur;
};

~onHit = {|state|

	if(state == true,{
		cr = cr.rotate(-1);
		m.midiOut.noteOn(m.midiChannel, 80  + cr[0], 100);
		{m.midiOut.noteOff(m.midiChannel, 80  + cr[0], 0)}.defer(0.01);
	},{
	});
};

~onMoving = {|state|

	if(state == true,{
		Pdef(m.ptn).set(\amp, 0.9);
	},{
		Pdef(m.ptn).set(\amp, 0.0);
	});
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = 5 - (((0.1 + m.rrateMassFiltered.cubed) * 60).mod(5).floor);
	var div = [].add(2.pow(oct).reciprocal).stutter(oct.asInteger);
	//div.postln;

	Pdef(m.ptn,Pbind( 
		\note, Pseq([0,1,2,3,4,5,6,7],inf),
		\dur,Pseq(div * 2,inf)
	));

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 2	, m.rrateMassFiltered * 127 );
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

	//[num,val].postln;

	// if(num == 4,{ threshold = 0.01 + (val * 0.99)});

	// threshold = threshold * 2;
	//m.midiOut.control(m.midiChannel, 65, val * 127 );

};
