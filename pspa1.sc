(
x = {
    var in = SoundIn.ar(7);
    
    var ps = PitchShiftPA.ar(
        in,
        Lag.kr(Pitch.kr(in)[0],0.2), //pitch tracking - we take just the frequency
        MouseX.kr(0.5, 2), //pitchRatio
        MouseY.kr(0.5, 2), //formantRatio
    );

    Out.ar(0,in+ps!2);    
}.play
)
x.free;

s.meter

o = Server.local.options;
o.numOutputBusChannels = 14; 
o.numInputBusChannels = 8;
s.reboot;
Delay
Quarks.gui

d=Buffer.read(s,PathName.new("~/Music/SCSamples/inMemoryKidsStories2.wav").asAbsolutePath);
d=Buffer.read(s,PathName.new("~/Music/SCSamples/lassem_ria2.wav").asAbsolutePath);
d=Buffer.read(s,PathName.new("~/Music/SCSamples/lassem_anton.wav").asAbsolutePath);
d=Buffer.read(s,PathName.new("~/Music/SCSamples/adultquestionsAll.wav").asAbsolutePath);
d=Buffer.read(s,PathName.new("~/Music/VoiceLab/templateQuestions/5.whatchallenges.wav").asAbsolutePath);


(
{
 
   var in, fft, output, sms;
   var min = 0.6;
   var max = 1.8;


    in=PlayBuf.ar(1,d,BufRateScale.kr(d),1,0,1);
	//in = SoundIn.ar(0);
	sms = SMS.ar(in, 50,50, 1.0,0.1, MouseX.kr(min, max));

    output=PitchShiftPA.ar(
        in,
        Lag.kr(Pitch.kr(sms[0]),0.07), //pitch tracking - we take just the frequency
        MouseX.kr(min, max).poll, //pitchRatio
        MouseY.kr(min, max).poll, //formantRatio
    )!2;

    Out.ar(0,Pan2.ar(output[0][1]));
}.play
)
Sum


(
{

    var in, output;

    in=PlayBuf.ar(1,d,BufRateScale.kr(d),1,0,1);

    output=SMS.ar(in, 80,80, 8.0,0.2, MouseX.kr(0.5,2), 0, formantpreserve:0 );

    Out.ar(0,Pan2.ar(output[0]));
}.play
)
(
{

var in, fft, sines, noise, freq, hasFreq;

//in= SoundIn.ar(0);
in=PlayBuf.ar(1,d,BufRateScale.kr(d),1,0,1);

hasFreq= Pitch.kr(in);

noise=SMS.ar(in, 10,10, 8, 1.0, MouseX.kr(0.5,4));

Pan2.ar(sines*(hasFreq.lag(0.01,0.01)) + LPF.ar(noise,MouseY.kr(100,10000,'exponential')),0.0)
}.play
)

(
{

    var in, fft, output;

    in=PlayBuf.ar(1,d,BufRateScale.kr(d),1,0,1);

    output=SMS.ar(in, 50,50, MouseX.kr(1.0,10.0).round(1.0),MouseY.kr(0.1,20.0,'exponential'), 1.0);

    Out.ar(0,Pan2.ar(output[0]));
}.play

)