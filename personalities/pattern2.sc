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
    \degree, Pseq([0,2,4], inf),
    \func, Pfunc{|ev| ev.postln}
);
~a.play
(
TempoClock.default.tempo = 1;

~bass = Pbind(
    \degree, Pwhite(0, 7, inf),
    \octave, 3,    // down 2 octaves
    \dur, Pwhite(1, 4, inf),
    \legato, 1,
    \amp, 0.2
).collect({ |event|
    ~lastBassEvent = event;
}).play(quant: Quant(quant: 1, timingOffset: 0.1));

// shorter form for the Quant object: #[1, 0, 0.1]

~chords = Pbind(
    \topNote, Pseries(7, Prand(#[-2, -1, 1, 2], inf), inf).fold(2, 14),
    \bassTriadNotes, Pfunc { ~lastBassEvent[\degree] } + #[0, 2, 4],
        // merge triad into topnote
        // raises triad notes to the highest octave lower than top note
        // div: is integer division, so x div: 7 * 7 means the next lower multiple of 7
    \merge, (Pkey(\topNote) - Pkey(\bassTriadNotes)) div: 7 * 7 + Pkey(\bassTriadNotes),
        // add topNote to the array if not already there
    \degree, Pfunc { |ev|
        if(ev[\merge].detect({ |item| item == ev[\topNote] }).isNil) {
            ev[\merge] ++ ev[\topNote]
        } {
            ev[\merge]
        }
    },
    \dur, Pwrand([Pseq([0.5, Pwhite(1, 3, 1), 0.5], 1), 1, 2, 3], #[1, 3, 2, 2].normalizeSum, inf),
    \amp, 0.05
).play(quant: 1);
)

~bass.stop;
~chords.stop;