(
p = Pbindef(\testp,
	\dur, Pseq([0.125, Rest(0.125)], inf),
	\root, 0,
	\note, Pxrand([0,4,7,12,11,7,5,2], 32),
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
n.adjustEndOfTrack(1,100);
n.metaEvents.dopostln;
n.plot;
// n.metaEvents.remove( n.endOfTrack( 1 )[0] );
// n.addMetaEvent([1, 7144, \endOfTrack ]);
// n.metaEvents.dopostln;
Routine{loop{n.p(\organ).play;3.9728.wait;}}.play(quant:0)
)



Routine{loop{Clock.beats.postln;0.1.wait;}}.play
