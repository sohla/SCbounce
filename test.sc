(

	~init = { "init".postln;};
	~run = { "running...".postln;};



)


a = [1,2,3,4];
b = [1,2];
c = (a.as(Set)-b.as(Set)).as(Array)


// create unique

"a".ascii

64.asAscii

128.rrand(64).asAscii

[128.rrand(64).asAscii,128.rrand(64).asAscii]

[123,78].asAscii

Array.fill(16,{|i|i=90.rrand(65)}).asAscii

    // create a new DataCollector:
d = DataCollector.new( "test" );
d.collection; // empty sorted list

~date = d.addData( [1,2,3] );
d.collection // now we have one entry with data in it and a timestamp

d.addMetaData( [5,6,7], ~date ); // add some metadata to it
d.collection

d.addData( [3,4.5,6] );
d.collection

// create a backup:
d.backup

// create a new collector:
e = DataCollector.new( "test2" );

// recover from the backup
e.recover

// we have the same data again:
e.collection;

(	
	Window().front.layout_(HLayout( *{|i|
	Button().states_([["yes"],["no"]]).maxWidth_(70)
	}!3 ).spacing_(0));
)
4.mod(3)


var square = { |x| x * x };
var map = { |fn, xs|
  all {: fn.(x), x <- xs };
};
map.(square, [1, 2, 3]);

all{:x, x <- (3)}

ServerMeter.new(s, 2, 2);




(
{
	var sig = SinOsc.ar(200,0,0.1);

Out.ar(0,Pan2.ar(sig, 0.0, 1.0) );

}.play
)




(

var a = [10,20,30,40,50,60,70];
a = a.collect({|o,i|i.even.if(o)}).replace([nil]);
a.postln;
)


90.rrand(65).asAscii