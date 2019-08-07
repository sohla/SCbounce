s = Server.local.boot;
{SinOsc.ar(111)}.play
b = Bus.control(s,1);

( // generate some data
Tdef( \updateBus, {
    loop{
        b.set( 1.0.rand.postln; );
        rrand(0.05,0.3).wait;
    }
}).play;
);
~output = Bus.new('audio', 0 );
SynthDescLib.new( \SensorSonification );
// create a sonificator for the first bus:
x = SensorSonificator.new( b, ~output );

// right now there are three different kinds of sonificators
// \value : sonifies the current value as frequency, amplitude is controlled by the rate of change in value
// \inrange : makes sound when the value is within a certain range
// \intrig : makes a sound whenever new data is coming in. Pitch is higher when the time since the last trigger was longer ago

// add value sonification:
x.addSonification( \value );

// start the sonification
x.start;

// set parameters for it
x.synths[0].set( \amp, 0.01 ); // base amplitude
x.synths[0].set( \slamp, 0.1 );  // slope amplitude

// post a list of available parameter names:
x.synths[0].controlNames;

// add inrange sonification:
x.addSonification( \inrange );

x.start;

// set parameters:
x.synths[1].set( \amp, 0.2 );
x.synths[1].set( \freq, 600 );
x.synths[1].set( \lo, 0.5 );

x.stop;
// arguments are kept when the sonification is stopped and started
x.start;

// add another inrange sonificator
x.addSonification( \inrange );
x.synths[2].set( \amp, 0.2 );
x.synths[2].set( \freq, 700 );
x.synths[2].set( \lo, 0.1 );
x.synths[2].set( \hi, 0.2 );
x.start;

// add an intrig sonificator:
x.addSonification( \intrig );

x.start;
x.synths[3].set( \amp, 0.5 );
x.synths[3].set( \dur, 0.5 );
x.synths[3].set( \freq, 100 );
x.synths[3].set( \fmmod, 500 );

x.synths[3].controlNames;

// query the group of sonificators (including argument values)
x.query

// with argument values:
x.query( false );

// stop the sonification:
x.stop;