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



(
	var dataPath = "~/Develop/SuperCollider/Projects/SCbounce/data/";
	var path = PathName.new(dataPath);

path.files.postln;

)

a = SortedList[1,2,3]
a = a.removeEvery(a)

a.indexOf(2)

b = List[1, 2, 3, 4]
b.collect({ arg item, i; item + 10 });

a = Array.newClear(1);
a = a.addFirst("a")
a.pop();a;



(
x = Array.newClear(3);

y = List.new(3);
10.do({ arg i; z = x.add(i); y.add(i); });
 z.postln; //y.postln;

 x.size.postln;
 z.size.postln;
)

a = Array.fill(3,{0});
a = a.shift(1);
a = a.put(0,"d");
a.at(a.size-1)




a = [1,7,5,14];
a.sum / a.size



(4-5).isNegative.if("yes")

(3<4).if("YEA","NA")


a = [[1,2],[3,4]];
a = [];
a = a.flop;
a = a.insert(0,[5,6]);
a = a.keep(3);
a = a.flop;

4.isNegative

2.atan2(3)


Quarks.gui

Quaternion

Quaternion.new(1.2,0.1,0.2,0.3).conjugate

(a:1,b:2).asArray.normalize



float q0 = q0Buffer.at(i);
float q1 = q1Buffer.at(i);
float q2 = q2Buffer.at(i);
float q3 = q3Buffer.at(i);

float angle_rad = acos(q0) * 2;
float angle_deg = angle_rad * 180 / PI;
// deal with divide by 0
float x = q1 / sin(angle_rad/2);
float y = q2 / sin(angle_rad/2);
float z = q3 / sin(angle_rad/2);

(1-0.2.squared).sqrt


public void set(Quat4d q1) {
   if (q1.w > 1) q1.normalise(); // if w>1 acos and sqrt will produce errors, this cant happen if quaternion is normalised
   angle = 2 * Math.acos(q1.w);
   double s = Math.sqrt(1-q1.w*q1.w); // assuming quaternion normalised then w is less than 1, so term always positive.
   if (s < 0.001) { // test to avoid divide by zero, s is always positive due to sqrt
     // if s close to zero then direction of axis not important
     x = q1.x; // if it is important that axis is normalised then replace with x=1; y=z=0;
     y = q1.y;
     z = q1.z;
   } else {
     x = q1.x / s; // normalise axis
     y = q1.y / s;
     z = q1.z / s;
   }
}


/*
				var sx,sy,sz;
				var qa = data.quatEvent.asArray;
				var ny,np,nr;
				var sqx = qa[1].squared;
				var sqy = qa[2].squared;
				var sqz = qa[3].squared;

				var test = qa[1]*qa[2] + qa[3]*qa[0];

				ny = (2.0*qa[2]*qa[0]-2.0*qa[1]*qa[3]).atan2(1.0-2.0*sqy-2.0*sqz);
				np = (2.0*test).asin;
				nr = (2.0*qa[1]*qa[0]-2.0*qa[2]*qa[3]).atan2(1.0-2.0*sqx-2.0*sqz);

				if( test > 0.499,{
					ny = 2.0 * qa[1].atan(qa[0]);
					np = pi.half;
					nr = 0;
				});

				if( test < -0.499,{
					ny = -2.0 * qa[1].atan(qa[0]);
					np = -pi.half;
					nr = 0;
				});

				[ny,np,nr].postln;
*/

Quarks.gui