var m = ~model;
var d = ~device;
var isOn = false;
var esp;
var notes = [0,4,7,4,7,12,16,7,12];
var note = notes[0];
var root = 60 - 24;

m.midiChannel = 0;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	// Pdef(m.ptn,
	// 	Pbind(
	// 		\note, Pseq([0,4,7,4,7,12,16,7,12].stutter(1),inf),
	// 		\args, #[],
	// 	);
	// );

	// Pdef(m.ptn).set(\dur,0.5);
	// Pdef(m.ptn).set(\octave,4);
	// Pdef(m.ptn).set(\amp,0.8);
	// Pdef(m.ptn).play();

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

	if( d.blob.data.size > 2, {

		if( isOn == false, {

			isOn = true;
			[d.blob.index, "on"].postln;
			m.midiOut.noteOn(m.midiChannel, note + root, 100);
			// Pdef(m.ptn).set(\amp,0.8);
		});

	},{

		if( isOn == true, {
			isOn = false;
			[d.blob.index, "off"].postln;
			m.midiOut.noteOff(m.midiChannel, note + root, 0);
			notes = notes.rotate(-1);
			note = notes[0];
			// Pdef(m.ptn).set(\amp,0.0);
		});

	});


};
~nextMidiOut = {|d|

	if( d.blob.data.size > 2, {

		m.midiOut.control(m.midiChannel, 0,  d.blob.center.x * 127 );
		m.midiOut.control(m.midiChannel, 1,  d.blob.center.y * 127 );
		// m.midiOut.control(m.midiChannel, 2,  d.blob.area * 127 );
	});
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	[d.blob.center.x, d.blob.center.y, d.blob.area]
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};


