a = Routine {
    var    i = 0;
    loop {
        i.yield;
        i = i + 1;
    };
};

a.nextN(10);

a = Pseries(start: 0, step: 1, length: inf).asStream;

a.nextN(10);

p = Pseries(0, 1, 10);
p.next;    // always returns the Pseries, not actual numbers

q = p.asStream;
q.next;    // calling this repeatedly gets the desired increasing integers


r = p.asStream;
r.next;    // starts from zero, even though q already gave out some numbers

q.next;    // resumes where q left off, with no effect from getting values from r

[q.next, r.next]    // and so on...



a = Pseq([0,2,4,6],inf,2).asStream
a.next

a = Pshuf([0,1,2,3],inf).asStream
a.next


a = Pwrand([0,1,2,3],[0.4,0.3,0.2,0.1],inf).asStream
a.next


a = Pseries(0,0.1,10).asStream
a.next

a = Pgeom(1,2,10).asStream
a.nextN(5)

a = Pwhite(0.2,0.4).asStream
a.nextN(5)

a = Pfunc({"hello"},{"reset"}).asStream
a.nextN(5)



a = Pser([0,1,2,3,4],3,0).asStream
a.nextN(5)


a = Pslide([0,1,2,3,4],5,3,2).asStream
a.nextN(6)

a = Place([0,1,[10,11],[12,13]],inf).asStream
a.nextN(8)


a = Place([0,1,[10,11],[12,13]],2).asStream.all


Pseq(#[1, 2, 3], 4).asStream.all;  

(-6, -4 .. 12)


Pseries(0, 1, 8).asStream
Ppatlace([Pseries(0, 1, 8), Pseries(2, 1, 7)],inf).asStream.nextN(8)

(\freq:520,\pan:-0.8).play

Pwhite(0.2, 0.8).asStream.nextN(50).plot


Pxrand((0..8),inf).asStream.nextN(8)


PdegreeToKey(Pxrand((0..6),inf),[0,2,4,5,7,9,11],0).asStream.nextN(20)

Pseries(4, Pwhite(-2, 2, inf).reject({ |x| x == 0 }), inf).asStream.nextN(10)

Pseries(4, Pwhite(-2, 2, inf).reject({ |x| x == 0 }), inf).fold(-7, 11).asStream.nextN(10)

Prorate(1, 1).asStream.nextN(10) 

(
	a = #[0,1,2,4,7,10];
Pbind(
	\note,PdegreeToKey(Pwhite(0,a.size,inf),a,7).trace,
	\dur, 0.125 
).play
)

Pstutter(Pseq([1,2],inf),Pseq((0..8))).asStream.nextN(8)

Pstep(Pseq([2,4]), Pseq([1,2,1]), inf).trace.play

Pstep(Pseq([1,2].collect((_).half)), Pseq([1,2,1]), inf).trace.play


Link.enable;


(
Ndef(\foo, {
	Pan2.ar(SinOsc.ar(100,0,EnvGen.kr(Env.perc(0,0.2,0.3),LinkTrig.kr(1))))
}).play

)

(

Pbind(
	\note,[Pseq((0,2..19),inf).asStream,4],
	\dur, Pseq([0.1,0.2],inf)
).iter.next()

)



( 
// trigger stream maker 
f = { |pat, group| 
        var ev, lastEv, str = pat.iter, hasStarted = 0; 
        Pn(Pfunc { 
                ev = str.next(()).play(); 
                (hasStarted == 1).if { group.release }; 
                hasStarted = 1; 
                lastEv = ev; 
        }).iter 
}; 

g = Group.new; 

p = Pbind( 
        \dur, 0.5, 
        \degree, Pseq([1, 2, 3, 4], inf), 
        \group, g 
); 

x = f.(p, g) 
) 


)

// do several times 
x.next(()) 


//• create synth SendTrig( LinkTrig(1))
//• OSCrespond to trig and call x.next()

g.release; 
MIDIClient.disposeClient;
Pdef.clear

(
var midiOut;
var ptn = \asd;

MIDIClient.init;
MIDIClient.destinations;

midiOut = MIDIOut.newByName("IAC Driver", "Bus 1", dieIfNotFound: true).latency_(0.0);

       Pdef(ptn,
                Pbind(
                        \note, Pseq([0,2,7,9,5,4].stutter(8),inf),
                        \value, Pwhite(50, 127, inf),
                        \func, Pfunc({|e| midiOut.control(1, 0, e[\value]) }),
                        \args, #[],
                );
        );

        Pdef(ptn).set(\dur,0.125);
        Pdef(ptn).set(\octave,3);
        Pdef(ptn).set(\amp,0.8);
        
        Pdef(ptn).set(\type,\midi);
        Pdef(ptn).set(\midiout,midiOut);
        Pdef(ptn).set(\chan,1);
        
        Pdef(ptn).set(\midicmd, \noteOn);
        
        Pdef(ptn).play();
)