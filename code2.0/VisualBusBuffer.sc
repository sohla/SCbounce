w = Window.new.front;
b = Buffer.alloc(s, 44100 * 1, 2)
v = Stethoscope(s,2, view:w.view, bufnum: b.bufnum);
v.view.bounds = Rect(0,0,600,300);
v.rate = \control;
v.bus.value = 0.2
v.cycle = 4096 * 8
v.xZoom = 4

y = VisualBuffer.new("a");
b.
y.buffer.play(true)

SynthDef(\test, {|bus=0,freq=111, amp=0.1|
    var sig = LFTri.kr(freq * MouseX.kr(1,100), amp);
    Out.kr(bus, sig);
    
}).add;

b.sine1(1.0 / [1, 2, 3, 4], true, true, true);


a = Synth(\test, [\bus, 0, \freq, 1, \amp, 0.1]);

a.free

b.sine1
y.refresh
y.bufnum
s.freeAll

Quarks.gui

FluidBufToKr

y = [ 1, 2, 3 ];

y.reshape(3,1)
y
y = y.add(4)
y.removeAt(0)



Array.with([Array.newClear(3),Array.newClear(3),Array.newClear(3)])


Array.fill(10,[0.1,0.2,0.4]);
a = [Array.fill(10,0.8),Array.fill(10,0.2),Array.fill(10,0.3)];
a[0].add(0.1)



FluidWaveform
VisualBuffer


// fill a 1-channel buffer with 7 numbers
a = 16
~buf = Buffer.loadCollection(s,{exprand(100,4000)} ! a);

// in a synth, read those numbers out of the buffer and get them as a control stream
(
~synth = {
    arg buf;
    var freqs = FluidBufToKr.kr(buf,numFrames:a);
    var sig = SinOsc.ar(freqs.lag(0.03)) * 0.1;
    sig.poll;
    Splay.ar(sig);
}.play(args:[\buf,~buf]);
)

// then you can change what's in the buffer and it will get read out by the synth
~buf.setn(0,{exprand(100,4000)} ! a);



FluidKrToBuf
(
~synth = {
        var buf = LocalBuf(512).clear;
        var sig = SinOsc.ar([440,441]);
        // var lfos = Array.fill(512,{arg i; SinOsc.ar(i.linlin(0,511,0.01,0.2))});
        FluidKrToBuf.kr(sig,buf);
        // sig = Shaper.ar(buf,sig);
        sig.dup * -40.dbamp;
    }.scope;
    )



~y = VisualBuffer.new("a");

(
    ~b = Buffer.alloc(s, 512 * 1, 1);

   ~ss =  {
        var sig = SinOsc.kr([1],0,0.1);
        FluidKrToBuf.kr(sig, ~b);
        Out.kr(0, sig);
    }.play;
)
~b.plot
w = Window.new.front;
v = Stethoscope(s,2, view:w.view, bufnum: ~b.bufnum);

Bus


// OSC out sending dummy data

SendReply
(
    {
        SendReply.kr(Impulse.kr(10), '/dummy', MouseX.kr(0.0,1.0), 1905);
    }.play(s);
)
    
~buffer = Buffer.alloc(s, 44100 * 1, 1);
// OSC in send to bus
(
    var w = Window.new.front;
    var v = Stethoscope(s, 1, view:w.view);
    w.onClose_({
        o.free;
        v.free;
        ~buffer.free;
    });

    CmdPeriod.doOnce({w.close});
    v.view.bounds = Rect(0,0,1200,800);
    v.bus.value = 0.1;

    o = OSCFunc({ |msg| 
            {v.bus.value = msg[3]}.defer;
            msg[3].postln;
        }, '/dummy');
    
    v.rate = \control;
    v.run;

    {
        var sig = v.bus.ar(1);
        RecordBuf.ar(sig, ~buffer, \offset.kr(0), \reclev.kr(1), \prelev.kr(0), \run.kr(1), \loop.kr(1));
        Out.ar(0, SinOsc.ar(100 + (100 * sig),0,0.1));
    }.scope;


    
)

