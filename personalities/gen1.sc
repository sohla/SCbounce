var m = ~model;
var i = 0;
var oscOut;


var eulerToQuaternion = {|y,p,r|

    var cy = cos(y * 0.5);
    var sy = sin(y * 0.5);
    var cp = cos(p * 0.5);
    var sp = sin(p * 0.5);
    var cr = cos(r * 0.5);
    var sr = sin(r * 0.5);

    Quaternion.new(
        cy * cp * cr + sy * sp * sr,
        cy * cp * sr - sy * sp * cr,
        sy * cp * sr + cy * sp * cr,
        sy * cp * cr - cy * sp * sr
    )
};

/*
    p = cos(i * 2pi * 0.002) * 90;
    r = sin(i * 2pi * 0.01) * 90;
    y = sin(i * 2pi * 0.006) * 90;

    q = eulerToQuaternion.(y,p,r);
    oscOut.sendMsg("/gyrosc/quat", q.coordinates[0],q.coordinates[1],q.coordinates[2],q.coordinates[3]);

    t = 3.1 + (cos(i * 2pi * 0.1) * 3);
    // [d.ip, d.port,t].postln;
    oscOut.sendMsg("/gyrosc/rrate", t,t,t);
    // t.postln;

            i = i + 0.03;

*/
//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

    oscOut = NetAddr(~device.ip, ~device.port);
};


//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	

// example feeding the community
~onEvent = {|e|
};



~onHit = {|state|

};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

    var x = cos(i * 2pi * 0.002) * 90;
    var z = sin(i * 2pi * 0.01) * 90;
    var y = sin(i * 2pi * 0.006) * 90;
    var t = 3.1 + (cos(i * 2pi * 0.1) * 3);
    var q = eulerToQuaternion.(y,x,z);

    // oscOut.sendMsg("/gyrosc/quat", q.coordinates[0],q.coordinates[1],q.coordinates[2],q.coordinates[3]);
    oscOut.sendMsg("/gyrosc/gyroSS", x,y,z);
    oscOut.sendMsg("/gyrosc/rrate", t,t,t);

    // [d.ip, d.port,t].postln;

    i = i + 0.03;

};

~nextMidiOut = {|d|
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	
~plotMin = -pi.half;
~plotMax = pi.half;

~plot = { |d,p|
	// [m.rrateMassFiltered * 0.5, ~device.sensors.quatEvent.z, ~device.sensors.quatEvent.x];
	[ ~device.sensors.quatEvent.x, ~device.sensors.quatEvent.y, ~device.sensors.quatEvent.z];
};
