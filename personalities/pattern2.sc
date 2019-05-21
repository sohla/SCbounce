(
	//------------------------------------------------------------	

	MIDIClient.init;
	MIDIClient.destinations;

	~midiOut = MIDIOut.newByName("IAC Driver", "Bus 1", dieIfNotFound: true).latency_(0.00);

	//------------------------------------------------------------	


	~a = Pdef(\a,
		Pbind(
			\note, Pseq([0,2,4],inf),
			\dur, 0.5,
			// \amp, 0.5,
			// \octave, 4,
			\type, \midi,
			\midiout, ~midiOut,
			\chan, 6,
			\args, #[],
		);
	);

	~b = Pdef(\b,
		Pbind(
			\note, Pseq([4,5,7],inf),
			\dur, Pseq([0.5],inf),
			\amp, 0.5,
			\type, \midi,
			\midiout, ~midiOut,
			\chan, 6,
			\args, #[],
		);
	);


	// Pdef(\a).play();
	// Pdef(\b).play();

	// ~pp = Ppar([Pdef(\a),Pdef(\b)]).play();	

	Pdef(\a).set(\amp,0.5);
	//------------------------------------------------------------	

)	
Pdef(\a).set(\amp,0.5);
Pdef(\a).pause()

Pdef(\a).play
Pdef(\b).play

Pdef(\a).stop
Pdef(\b).stop


~a.stop()
~b.stop()
~pp.play()
~pp.stop()



~a = Pbind(
    \dur, 0.5,
    \degree, Pseq([0,2,4], inf)
);
~b = Pbind(
    \root, Pseq([0,2,4].stutter(3), inf)
);

~c = Pbind(
    \dur, 0.5,
    \degree, Pseq([8,7,6], inf),
);

p = Pchain(~a, ~c, ~b).play;
p.stop;