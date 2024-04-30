

//------------------------------------------------------
// FluidKrToBuf
//------------------------------------------------------
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



//------------------------------------------------------
// FluidKrToBuf 2 
//------------------------------------------------------

(
    ~b = Buffer.alloc(s, 512 * 1, 1);
   ~ss =  {
    // var sig = SinOsc.kr([MouseX.kr(0.1,1)],0,0.1);
    var sig = SinOsc.kr([LFNoise2.kr(100)],0,0.8);
    FluidKrToBuf.kr(sig, ~b);
        Out.kr(0, sig);
    }.scope;
)

// can use the buffer elsewhere
~b.plot
w = Window.new.front;
v = Stethoscope(s,2, view:w.view, bufnum: ~b.bufnum);


//------------------------------------------------------
// OSC out sending dummy data
//------------------------------------------------------

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




//------------------------------------------------------
// OSC out sending dummy data into a BUS
//------------------------------------------------------

SendReply
(
    {
        SendReply.kr(Impulse.kr(10), '/dummy', MouseX.kr(0.0,1.0), 1905);
    }.play(s);
)
    



// OSC in send to bus
(
    var controlBus = Bus.control(s, 4);
    var audioBus = Bus.audio(s, 4);

    SynthDef(\sender, {|sr=10|
        var sig = [
            LFNoise0.kr(3,0.5),
            LFNoise1.kr(1, 0.5),
            LFNoise2.kr(2, 0.5)
        ];
        SendReply.kr(Impulse.kr(sr), '/sender', sig, 1905);
    }).add;

    SynthDef(\receiver, {|outBus=0, inBus=0, bufnum|
        var sig = In.kr(inBus, 4);
        // var buffer = LocalBuf(256, 1);
        // FluidKrToBuf.kr(sig, bufnum);
        Out.ar(outBus, K2A.ar(sig));
    }).add;
        
    // SynthDef(\pb, {|bus=0, bufnum=0|
    //     var sig = SinOsc.ar(50,0,0.1);//PlayBuf.ar(1, bufnum, 1, 1, 0, 1);
    //     Out.ar(bus, sig);
    // }).add;

    //-----

    o = OSCFunc({ |msg| 
        {controlBus.setn(msg[3..])}.defer;
        // msg[1..].postln;
    }, '/sender');


    ~tx = Synth(\sender);
    ~rx =  Synth(\receiver, [\outBus, audioBus.index, \inBus, controlBus.index]);
    
    // controlBus.scope;
    audioBus.scope;

)




~ds = FluidDataSet(s);

// run a synth with varying sounds and an mfcc analysis
(
~synth = {
    arg t_trig;
    var buf = LocalBuf(13);
    var n = 7;
    var sig = BPF.ar(PinkNoise.ar.dup(n),LFDNoise1.kr(2.dup(n)).exprange(100,4000)).sum * -20.dbamp;
    var mfccs = FluidMFCC.kr(sig,buf.numFrames,startCoeff:1,maxNumCoeffs:buf.numFrames);

    // write the real-time mfcc analysis into this buffer so that...
    FluidKrToBuf.kr(mfccs,buf);

    // it can be added to the dataset from that buffer by sending a trig to the synth
    FluidDataSetWr.kr(~ds,"point-",PulseCount.kr(t_trig),buf:buf,trig:t_trig);
    sig.dup;
}.play;
)

// send a bunch of triggers and...
~synth.set(\t_trig,1);

// see how your dataset grows
~ds.print;





// exmaple FluidMFCC on a signal
(

    var w = Window("MFCCs Monitor",Rect(0,0,800,400)).front;
    var ms = MultiSliderView(w,Rect(0,0,w.bounds.width,w.bounds.height)).elasticMode_(1).isFilled_(1);
    

    ms.reference_(Array.fill(13,{0.5})); //make a center line to show 0
    
    //play a simple sound to observe the values
    ~synth = {
        arg type = 0;
        var lfo = LFNoise2.ar(10).range(-pi,pi);
        var freq = 111;
        var source = Select.ar(type,[SinOsc.ar([freq,freq+2] * SinOsc.ar(freq/2), [lfo, lfo]),Saw.ar(freq),Pulse.ar(freq)]) * LFTri.kr(0.1).exprange(0.01,0.1);
        var mfccs = FluidMFCC.kr(source,numCoeffs:13,startCoeff:0,maxNumCoeffs:13);
        SendReply.kr(Impulse.kr(30),"/mfccs",mfccs);
        source.dup;
    }.play;
    
    ~mfccRange = 40;
    OSCdef(\mfccs,{
        arg msg;
         {ms.value = (msg[3..].linlin(-40,40,0,1))}.defer;
    },"/mfccs");

    w.onClose_({
        ~synth.free;
        ms.close;
    });
    CmdPeriod.doOnce({w.close});

)
    
    // change the wave types, observe that, apart from the 0th coefficient, different loudness does not change the values
    ~synth.set(\type, 1) // sawtooth wave
    ~synth.set(\type, 2) // pulse wave
    ~synth.set(\type, 0) // sine wave
    
    ~synth.free;







