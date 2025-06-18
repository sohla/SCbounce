(
p = Pbindef(\testp,
	\dur, Pseq([0.125, Rest(0.125)] * 0.5, 32),
	\root, 0,
	\note, Prand([0,2,4,5,7,9,11,12].stutter(2), 64),
    \db, Pwhite( -10,-4 )
);


m = SimpleMIDIFile( "~/Downloads/CRand.mid" );
m.init1( 2, 120, "4/4" );
m.fromPattern( p ).play(quant:0);
)

m.plot
m.p.play
m.write

(
SynthDef( \organ, { |freq = 440, sustain = 1, amp = 0.1|
        var sig;
        sig = LFPar.ar( freq * [1,2,3,5], 0, amp/[2,4,5,7] );
        Out.ar( 0, Env([0,1,1,0], [0.025,sustain,0.025]).kr(2) * sig.dup );
}).add;
);

(
n = SimpleMIDIFile.read( "~/Downloads/output.mid" );
// n = SimpleMIDIFile.read( "~/Downloads/CRand.mid" );
n.adjustEndOfTrack(1,100);
n.tempo;
n.metaEvents.dopostln;
n.plot;
Routine{loop{n.p(\organ).play;((60 / n.tempo) * 8).wait;}}.play(quant:0)
)

// 1.4 note * num 1/4 notes
(60 / 120.8159912046) * 8
// (60 / tempo) * numNotes




Routine{loop{Clock.beats.postln;0.1.wait;}}.play



(
a = SimpleMIDIFile.read( "~/Downloads/CRand.mid" );
b = SimpleMIDIFile.read( "~/Downloads/CMaj.mid" );
// a.metaEvents.dopostln;
// b.metaEvents.dopostln;
a.tempo.postln;
b.tempo.postln;
((60 / a.tempo) * 8).postln;
((60 / b.tempo) * 8).postln;

// b.plot;
// n.metaEvents.remove( n.endOfTrack( 1 )[0] );
// n.addMetaEvent([1, 7144, \endOfTrack ]);
// n.metaEvents.dopostln;
)



(
SynthDef(\fm7synth, {
    |out=0, freq=440, amp=0.5, att=0.01, dcy=0.1, sus= 0.3, rel=1, gate=1|
    var env, signal, ctls, mods;
    var x = MouseX.kr(0, 1);
    var y = MouseY.kr(0, 1);

    // Envelope
    env = EnvGen.kr(Env.adsr(att, dcy, sus, rel), gate, doneAction: 2);

    ctls = [
        // freq, phase, amp
        [freq, 0, 1],
        [freq * 0.5, 0, 1],
        [freq * 2, 0, 1],
        [LFNoise1.kr(0.5).exprange(3, 100), 0, 1],
        [LFNoise1.kr(0.5).exprange(3, 100), 0, 1],
        [LFNoise1.kr(0.5).exprange(3, 100), 0, 1]
    ];
    mods = [
        [MouseY.kr(0, 1), 0, MouseX.kr(4, 1), 0, 0, 0],
        [MouseX.kr(0, 6), MouseY.kr(2, 0), 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0]
    ];
	signal = SinOsc.ar(freq * 0.5,0,2);

    // Generate FM7 signal
    signal = signal + FM7.ar(ctls, mods) * env * amp;


    // Output
    Out.ar(out, signal);
}).add;
)


(

a = [
	// "~/Downloads/Cmaj.mid",
	// "~/Downloads/Cmaj_1.mid",
	"~/Downloads/CRand.mid",
	"~/Downloads/CRand_1.mid",
	"~/Downloads/CRand_2.mid",
];

Routine{
	loop{
		"playing : %".format(a[0]).postln;
		n = SimpleMIDIFile.read(a[0]);
		n.metaEvents.dopostln;
		n.p(\fm7synth).play;
		((60 / n.tempo) * 8).wait;
		a = a.rotate(-1);
	}
}.play(quant:0)
)



(11/10).mod(1.0)
