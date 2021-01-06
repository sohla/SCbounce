var m = ~model;
var d = ~device;
var isOn = false;
var esp;

m.midiChannel = 0;

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

// d.blob.isEmpty.not.postln;
	if( d.blob.isEmpty.not, {

		if( isOn == false, {

			isOn = true;
			[d.blob.index, "on"].postln;
			m.midiOut.noteOn(m.midiChannel, 25, 100);
		});

	},{

		if( isOn == true, {
			isOn = false;
			[d.blob.index, "off"].postln;
			m.midiOut.noteOff(m.midiChannel, 25, 0);
		});

	});


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


