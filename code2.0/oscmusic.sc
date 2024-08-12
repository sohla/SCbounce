(

// var devicesDir = "~/Develop/SuperCollider/Projects/scbounce/personalities/";
var devicesDir = "~/Develop/SuperCollider/oscMusic/personalities/";
// var oscMessageTag  = "IMUFusedData";
var oscMessageTag  = "CombinedDataPacket";
var first = "timDrums";




var loadDeviceList;

var names;


var width = 600, height = Window.screenBounds.height * 0.9;
var startup, shutdown, buildUI;

var contentView = UserView().background_(Color.grey(0.2));

var reloadButton;
var voltButton;


var createPlotterGroup, createThreeDeeCanvas, createTransportView, createTwoDeeCanvas;

var createWindowView, addDeviceView;
var startOSCListening, stopOSCListening, enableOSCListening, disableOSCListening, addOSCDeviceListeners;

var addDevice, removeDevice, removeDeviceButton;
var buttonListener, airstickListeners = [], numAirwareVirtualDevices = 4;

var oscOut = NetAddr.new("127.0.0.1", 9003);

var devices = Dictionary();

var dataRate = 1;
var renderRate = 30;
var loadPersonality;
var reloadPersonality;
var createProcRout;

var createGenerator;

var infoView;

var dataSizes = [100,200,300,400];

var eulerToQuaternion;

var com = (
	\root: 0,
	\dur: 1,
	\accelMass: 0,
	\rrateMass: 0,
);


//------------------------------------------------------------
// models
//------------------------------------------------------------
//• can we use this Event proto as a way of decalritive building an application

var twoCh = (\x: 0, \y:0);
var threeCh = (\x: 0, \y:0, \z:0);
var fourCh = (\w: 0, \x: 0, \y:0, \z:0);

var listenersProto = (

	\gryoListner: nil,
	\rotMatListner: nil,
	\rrateListener: nil,
	\accelListener: nil,
	\airware:nil,
	\quatListener: nil,
	\altListener: nil,
	\ampListener: nil,
	\voltListener: nil,
	\lineListener: nil;

);

var sensorsProto = (

	\gyroEvent: threeCh,
	\gyroMass: 0,
	\rrateEvent: threeCh,
	\rrateMass: 0,
	\accelEvent: threeCh,
	\accelMass: 1,
	\quatEvent: fourCh,
	\ampValue: 0,


);

var blobProto = (
	\index: 0,
	\dataSize: 3,
	\area: 0,
	\perimeter: 0,
	\center: Point(0,0),
	\rect: Rect(0,0,20,20),
	\label: 0,
	\velocity: Point(0,0),
	\data: [[0,0]],
);

var deviceProto = (
	\name: first,
	\ip: "127.0.0.1",
	\port: 57120,
	\did: "nil",

	\enabled: true, // are we running
	\dataSize: dataSizes[0],

	\listeners: Event.new(proto:listenersProto),

	\env: nil,	// Environment for injected code
	\procRout: nil,	// Routine calls ~next every ~fps

	\generator: nil, // Routine for generating data

	\sensors: Event.new(proto:sensorsProto),

	\blob:  Event.new(proto:blobProto),


	\noteOnFunc: nil,
	\noteOffFunc: nil

);

//------------------------------------------------------------
//
//------------------------------------------------------------

loadDeviceList = {

	var path = PathName.new(devicesDir++"list.sc");
	var file = File.new(path.asAbsolutePath,"r");
	var str = file.readAllString;

	interpret(str)
};

//------------------------------------------------------------
loadPersonality = {|d|

	var path = PathName.new(devicesDir++d.name++".sc");
	var file = File.new(path.asAbsolutePath,"r");
	var str = file.readAllString;

	// after adding personality to an Environment, add useful functions to be used by anyone
	var env = Environment.make {




		~model = (
			\com: com,
			\name: d.name,
			\ptn: Array.fill(16,{|i|i=90.rrand(65).asAscii}).join(),

			\rrateMass: 0,
			\rrateMassFiltered: 0,
			\rrateMassThreshold: 0.21, //use for isMoving
			\rrateMassThresholdSpec: ControlSpec(0.07, 0.4, \lin, 0.01, 0.21),

			\accelMass: 0,
			\accelMassFiltered: 0,
			\accelMassAmpThreshold: 2.0,
			\accelMassThresholdSpec: ControlSpec(0.4, 3.0, \lin, 0.1, 2.0),

			\isHit: false,
			\isMoving: true,
			\accelMassAmp: 0.0,

		);
		~device = d;
		//------------------------------------------------------------
		// frame rate of rout
		~secs = 0.03;

		//------------------------------------------------------------
		// process data->model
		~processDeviceData = {|d|

			~model.accelMass = d.sensors.accelEvent.sumabs * 0.33;
			~model.rrateMass = d.sensors.rrateEvent.sumabs;

			if(~model.accelMass > 0.3,{
				//			(~model.accelMass - ~model.accelMassFiltered).sign.postln;
				if( (~model.accelMass - ~model.accelMassFiltered).sign > 0, {
					~model.accelMassFiltered = ~tween.(~model.accelMass, ~model.accelMassFiltered, 0.8);
				},{
					// "decay".postln;
					~model.accelMassFiltered = ~tween.(~model.accelMass, ~model.accelMassFiltered, 0.8);
				});


			},{
				~model.accelMassFiltered = ~tween.(~model.accelMass, ~model.accelMassFiltered, 0.05);

			});

			// ~model.accelMassFiltered = ~tween.(~model.accelMass, ~model.accelMassFiltered, 0.2);
			~model.rrateMassFiltered = ~tween.(~model.rrateMass, ~model.rrateMassFiltered, 0.4);

		};

		//------------------------------------------------------------
		// process triggers
		~processTriggers = {|d|


			// isHit imp.
			//
			var changeState = {|state|
				if(~model.isHit != state,{
					~model.isHit = state;
					if(~model.isHit == true,{~onHit.(~model.isHit)},{~onHit.(~model.isHit)});
				});
			};

			// use raw accelMass to get the quickest response
			if( ~model.accelMass > ~model.accelMassAmpThreshold,{
				changeState.(true);
			},{
				changeState.(false);
			});

			// isMoving imp.
			//

			if(~model.rrateMassFiltered > ~model.rrateMassThreshold, {
				if(~model.isMoving == false,{
					~model.isMoving = true;
					// Pdef(~model.ptn).resume();
				});
			},{
				if(~model.isMoving == true,{
					~model.isMoving = false;
					// Pdef(~model.ptn).pause();
				});
			});
		};


		//------------------------------------------------------------
		~play = {
			Pdef(~model.ptn).play();
		};

		~stop = {
			Pdef(~model.ptn).stop();
		};

		//------------------------------------------------------------
		~init = {
			("init" + ~model.name).postln;
		};

		//------------------------------------------------------------
		~buildPattern = {
			Pdef(~model.ptn).play();


		};
		//------------------------------------------------------------
		~deinit = {
			~stop.();
			Pdef(~model.ptn).clear;//or use endless?
			("deinit" + ~model.name).postln;
		};

		//------------------------------------------------------------

		//------------------------------------------------------------
		interpret(str);
		//------------------------------------------------------------


		~tween = {|input,history,friction = 0.5|
			(friction * input + ((1 - friction) * history))
		};

		~slope = {|input,history|
			history - input
		};
	};

	env
};


//------------------------------------------------------------
//
//------------------------------------------------------------
//https://en.wikipedia.org/wiki/Conversion_between_quaternions_and_Euler_angles
eulerToQuaternion = {|y,p,r|

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
//------------------------------------------------------------
//
//------------------------------------------------------------
createGenerator = {|d|

	var oscOut = NetAddr(d.ip, d.port);
	var p,y,r,t;
	var i=0;
	var q;
	Routine {
		loop {

			p = cos(i * 2pi * 0.002) * 90;
			r = sin(i * 2pi * 0.01) * 90;
			y = sin(i * 2pi * 0.006) * 90;

			q = eulerToQuaternion.(y,p,r);
			oscOut.sendMsg("/gyrosc/quat", q.coordinates[0],q.coordinates[1],q.coordinates[2],q.coordinates[3]);

			t = 3.1 + (cos(i * 2pi * 0.1) * 3);
			[d.ip, d.port,t].postln;
			//				oscOut.sendMsg("/gyrosc/rrate", t,t,t);

			i = i + 0.03;
			0.03.yield;

		}
	}.stop;
};


//------------------------------------------------------------
//
//------------------------------------------------------------
createProcRout = {|d|

	Routine {

		loop{
			d.env.use{

				if(d.enabled == true,{

					// process data -> personality model
					~processDeviceData.(d);

					// model to functions
					~processTriggers.(d);

					//post process : make changes to patterns etc.
					~next.(d);

				});

				(~secs.()).yield;
			};
		};
	}
};
//------------------------------------------------------------
//
//------------------------------------------------------------

startup = {
	names = loadDeviceList.();
};

//
//------------------------------------------------------------
shutdown = {


	stopOSCListening.();

	devices.keysValuesDo({|k,d|
		removeDevice.(d);
	});


	s.queryAllNodes;

	Pdef.clear;
	Server.freeAll;

	s.quit;

};
//------------------------------------------------------------
//
//------------------------------------------------------------
removeDevice = {|d|

	d.noteOnFunc.free;
	d.noteOffFunc.free;

	d.procRout.stop();

	d.procRout.free;

	d.generator.stop();
	d.env.use{ ~deinit.() };

	d.listeners.voltListener.free;
	d.listeners.lineListener.free;
	d.listeners.ampListener.free;
	d.listeners.gryoListner.free;
	d.listeners.rrateListener.free;
	d.listeners.accelListener.free;
	// numAirwareVirtualDevices.do({ |i|
		d.listeners.airware.free;
// });
	d.listeners.quatListener.free;
};

addDevice = { |ip,port|

	var d = Event.new(proto:deviceProto);

	d.listeners = Event.new(proto:listenersProto);
	d.sensors =  Event.new(proto:sensorsProto);
	d.blob = Event.new(proto:blobProto);
	d.ip = ip;
	d.port = port;

	devices.put(port,d);

	// load the data
	reloadPersonality.(d);

	addDeviceView.(contentView, d);

	addOSCDeviceListeners.(d);

	d // return the device (g)
};

reloadPersonality = { |d|


	// stop personality

	d.procRout.stop;

	d.procRout.free;

	if(d.env != nil,{ d.env.use{ ~deinit.() }});


	// start new personality
	d.env = loadPersonality.(d);
	d.env.use{
		~init.();
		~buildPattern.();
	};


	d.procRout = createProcRout.(d);
	d.procRout.reset.play(AppClock);

	d.generator = createGenerator.(d);
	//d.generator.reset.play(AppClock);

};

//------------------------------------------------------------
//
//------------------------------------------------------------
buildUI = {

	var window;

	QtGUI.palette = QPalette.dark;

	// GUI.skin.plot.gridLinePattern = FloatArray[1, 0];
	// GUI.skin.plot.gridColorX = Color.yellow(0.5);
	// GUI.skin.plot.gridColorY = Color.yellow(0.5);
	// GUI.skin.plot.background = Color.black;
	// GUI.skin.plot.plotColor = Color.white;

	window = Window("osc music", Rect(400, 200, width, height), false).front;
	window.view.keyDownAction_({|view,char,mods,uni,code,key|
		if(uni==114,{//r
			devices.keysValuesDo({|k,v|
				reloadPersonality.(v);
			});
		});
		// if(uni==97,{//a
		// 	stackButton.valueAction_((stackButton.value+1).mod(3));
		// });
		if(uni==100,{
			//•disconnect
		});
		//uni.postln;
	});
	// contentView.drawFunc = {
	// 	Pen.scale(1.1,1.1);
	// 	Pen.drawImage( Point(0,-250), image, operation: 'sourceOver', opacity:0.1);

	// };

	window.onClose = {
		shutdown.();
	};
	CmdPeriod.doOnce({window.close});

	createWindowView.(window);

};


addDeviceView = { |view, d|

	var header;
	var va,vb,vc;
	var stackView, stackLayout;
	var dataSizeMenu;
	var popup;
	var col = Color.rand(0.1,0.9).alpha_(0.8);

	var createGraphs = {

		createPlotterGroup.(va, Rect(250,5,400,240), col,
			[
				"ymc",
				[Color.yellow,Color.magenta,Color.cyan,Color.red,Color.green,Color.blue],
				{|p| d.env.use{ ~plot.(d)} }
			]
			,d.env.use{ ~plotMin.()},d.env.use{ ~plotMax.()});
	};

	var removeGraphs = {
		va.children[2].removeAll;
		va.children[2].remove;
	};



	var onOffButton;

	header = View(view).background_(col).maxHeight_(100).layout_( GridLayout.rows( [

		removeDeviceButton = Button(view)
		.maxWidth_(40)
		.states_([
			["x",Color.red(0.5)],
		])
		.action_({|b|
			header.remove();
			stackView.remove();
			removeDevice.(d);
			devices.removeAt(d.port);
		}),
		onOffButton = Button()
		.maxWidth_(40)
		.states_([["mute",Color.yellow],["mute"]])
		.valueAction_(1)
		.action_({|b|
			d.enabled = b.value.asBoolean;

			if(d.enabled == true,{
				// reloadPersonality.(d);
				d.env.use{~play.()};
			},{
				d.env.use{~stop.()};
			});
		}),

	/*	voltButton = Button()
		.maxWidth_(80)
		.states_([["volt",Color.red]])
		.action_({|b|
			n = NetAddr.new(d.ip, d.port.asInt);
			n.postln;
			n.sendMsg("/togyrosc/volt", 42.asInt);
		}),
*/
		Button()
		.maxWidth_(40)
		.states_([["reset"]])
		.action_({
			d.ip.class.postln;
			d.port.class.postln;
			b = NetAddr.new(d.ip, d.port.asInt+1);    // create the NetAddr
			b.sendMsg("/bounce", "motionReset");    // send the application the messa

		}),


		infoView = StaticText(view)
		.stringColor_(Color.white)
		.font_(Font(size:12))
		.minWidth_(100)
		.string_(d.ip+":"+d.port+"["+d.did+"]"),

	],[

		dataSizeMenu = PopUpMenu(view)
		.maxWidth_(60)
		.items_(dataSizes)
		.valueAction_(0)
		.action_({|b|
			d.dataSize = dataSizes.at(b.value);
		})
		.valueAction_(0),


		popup = PopUpMenu(view)
		.minWidth_(120)
		.items_(names)
		.valueAction_(names.find([d.name]))
		.action_({|b|
			d.name = names.at(b.value);
			reloadPersonality.(d);
		}),

		Button()
		.maxWidth_(40)
		.states_([["gen", Color.white],["gen",Color.green]])
		.action_({|b|
			//b.value.postln;
			if(b.value == 1,{
				d.generator.reset.play(AppClock);
			},{
				d.generator.stop();
			});
		}),

		reloadButton = Button(view)
		.minWidth_(200)
		.states_([
			["reload"],
		])
		.action_({|b|

			onOffButton.valueAction_(1);
			{
				reloadPersonality.(d);
				onOffButton.valueAction_(1);
			}.defer(0.1);
		}),




	]));


	//------------------------------------------------------------
	//view.layout.spacing_(1);
	view.layout.add(stackView = View()
		// .minHeight_(270)
		// .maxHeight_(270)
		.background_(col)
		.layout_(
			stackLayout = HLayout(
				vc = View().background_(col),
				vb = View().background_(col),
		)).minHeight_(250)
	);


	createPlotterGroup.(vb,d);
	createThreeDeeCanvas.(vc,d);

	contentView.layout.add(nil);


};


//------------------------------------------------------------
// createPlotterGroup
//------------------------------------------------------------

createPlotterGroup = {|view, data|

	var col = [Color.yellow,Color.magenta,Color.cyan,Color.red,Color.green,Color.blue];
	var bounds = Rect(0,0,570/2 - 20,200);
	var pw = bounds.width;
	var ph = bounds.height;
	var plotterView = UserView(view,bounds).animate_(true);
	var pmin = data.env.use{ ~plotMin.()};
	var pmax = data.env.use{ ~plotMax.()};
	var plotData = { data.env.use{ ~plot.(data)} };

	var plotter = Plotter("plotter", Rect(10,30,pw-10,ph-30),plotterView)
	.value_((0..data.dataSize)) //need to init arrays with data
	.refresh;

	var st = Array.fill(4,"""");
	var checkBox = CheckBox(view, Rect(10,-20,50,70), "plot");

	checkBox.action_({ plotterView.visible = checkBox.value });
	checkBox.valueAction_(checkBox.value);

	plotData.().size.do({|i|

		st[i] = StaticText(view,Rect(10+(ph/2 * i), 210, pw * 0.2, 14))
		.string_("channel"+i)
		.font_(Font(size:9))
		.background_(Color.gray(0.25))
		.align_(\center)
		.stringColor_(col[i]);
	});

	plotterView.drawFunc_({});

	plotter.setProperties(\backgroundColor, Color.gray(0.25));

	plotterView.drawFunc = plotterView.drawFunc <> {
		{


				plotter.superpose = true;
				plotter.value = plotter.value.flop;
				plotter.value = plotter.value.insert(0, plotData.());
				plotter.value = plotter.value.keep(data.dataSize);
				plotter.value = plotter.value.flop;

				// old way of parsing single values
				// p.value = p.value.shift(1).putFirst(d.env.use{ ~plot.(d)});


				plotter.setProperties(\plotColor, col).refresh;

				//• this breaks
				// plotData.().do({|o,i|
				// 	st[i].string = o.round(0.01).asString;
				// });

			plotter.minval_(pmin);
			plotter.maxval_(pmax);

		}.defer(0.1);// need to delay to allow for construction
	}

};

//------------------------------------------------------------
// Three Dee Canvas
//------------------------------------------------------------
// special view for special data

createThreeDeeCanvas = { |view, data|
	var graph1;
	var cube, top, rate, loc, ico;
	var p1,p2,p3;
	var t = (1.0 + (5.0).sqrt) / 2.0;
	var accelX, accelY, accelZ;
	var checkBox = CheckBox(view, Rect(10,-20,50,70), "3d").value_(true);

	graph1 = Canvas3D(view, Rect(10, 30, 570/2 - 30, 170))
	.scale_(140)
	.background_(Color.gray(0.25))
	.perspective_(0.5)
	.transforms_([Canvas3D.mTranslate(0,0,0)])
	.distance_(3.5);

	graph1.add(p1 = Canvas3DItem.grid(2)
		.color_(Color.green)
		.fill_(false)
		.width_(0)
		.transform(Canvas3D.mScale(1,t,1))
	);
	graph1.add(p2 = Canvas3DItem.grid(2)
		.color_(Color.red)
		.width_(1)
		.fill_(false)
		.transform(Canvas3D.mScale(t,1,1))
		.transform(Canvas3D.mRotateY(pi/2))
	);
	graph1.add(p3 = Canvas3DItem.grid(2)
		.color_(Color.blue)
		.width_(1)
		.fill_(false)
		.transform(Canvas3D.mScale(t,1,t))
		.transform(Canvas3D.mRotateX(pi/2))
	);

	graph1.add(ico = Canvas3DItem()
		.color_(Color.white.alpha_(0.1))
		.width_(1)
		.fill_(true)
		.paths_([
			[p1.paths[0][0],p1.paths[0][1],p2.paths[0][1]],
			[p2.paths[0][1],p3.paths[0][0],p1.paths[0][0]],
			[p1.paths[0][1],p3.paths[0][1],p2.paths[0][1]],
			[p1.paths[0][0],p2.paths[0][0],p1.paths[0][1]],
			[p1.paths[0][1],p3.paths[1][1],p2.paths[0][0]],
			[p1.paths[0][0],p3.paths[1][0],p2.paths[0][0]],

			[p1.paths[1][0],p2.paths[1][0],p1.paths[1][1]],
			[p1.paths[1][1],p3.paths[1][1],p2.paths[1][0]],
			[p1.paths[1][0],p3.paths[1][0],p2.paths[1][0]],
			[p1.paths[1][0],p2.paths[1][1],p1.paths[1][1]],
			[p1.paths[1][1],p3.paths[0][1],p2.paths[1][1]],
			[p1.paths[1][0],p3.paths[0][0],p2.paths[1][1]],

			[p2.paths[0][1],p3.paths[0][0],p2.paths[1][1]],
			[p2.paths[0][1],p3.paths[0][1],p2.paths[1][1]],

			[p2.paths[0][0],p3.paths[1][1],p2.paths[1][0]],
			[p2.paths[0][0],p3.paths[1][0],p2.paths[1][0]],

			[p3.paths[0][0],p1.paths[1][0],p3.paths[1][0]],
			[p3.paths[0][0],p1.paths[0][0],p3.paths[1][0]],

			[p3.paths[0][1],p1.paths[1][1],p3.paths[1][1]],
			[p3.paths[0][1],p1.paths[0][1],p3.paths[1][1]],
		])
	);

	graph1.add(accelX = Canvas3DItem.regPrism()
		.color_(Color.yellow(0.9))
		.width_(1)
	);
	graph1.add(accelY = Canvas3DItem.regPrism()
		.color_(Color.magenta(0.9))
		.width_(1)
	);
	graph1.add(accelZ = Canvas3DItem.regPrism()
		.color_(Color.cyan(0.9))
		.width_(1)
	);

	checkBox.action_({ graph1.visible = checkBox.value });
	checkBox.valueAction_(checkBox.value);
	// animate
	graph1.animate(renderRate) {|t|
		var tr = [
			data.sensors.gyroEvent.y,//0
			data.sensors.gyroEvent.z,//1
			data.sensors.gyroEvent.x//2
		];

		ico.transforms = [
			Canvas3D.mScale(0.6,0.6,0.6),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];

		accelY.transforms = [
			Canvas3D.mScale(0.01,(data.sensors.accelEvent.y),0.01),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];

		accelX.transforms = [
			Canvas3D.mScale((data.sensors.accelEvent.z),0.01,0.01),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];
		accelZ.transforms = [
			Canvas3D.mScale(0.01,0.01,(data.sensors.accelEvent.x)),
			Canvas3D.mRotateX(tr[0]),
			Canvas3D.mRotateY(tr[1]),
			Canvas3D.mRotateZ(tr[2]),
		];
		p1.transforms=ico.transforms;
		p2.transforms=ico.transforms;
		p3.transforms=ico.transforms;
	};

};


//------------------------------------------------------------
// osc listneners
//------------------------------------------------------------
addOSCDeviceListeners = {|d|

	var na = NetAddr.new(d.ip, d.port);

	d.listeners.rrateListener = OSCFunc({ |msg, time, addr, recvPort|
		// [msg, time, addr, recvPort].postln;

		if(devices.at(addr.port) != nil,{
			devices.at(addr.port).sensors.rrateEvent = (
				\x:msg[1].asFloat,
				\y:msg[2].asFloat,
				\z:msg[3].asFloat);
			// devices.at(addr.port).sensors.rrateEvent.sumabs.postln;
		});

	}, '/gyrosc/rrate', na);


	//•• currently not being used : gets set in /gyrosc/quat
	d.listeners.gryoListner = OSCFunc({ |msg, time, addr, recvPort|

		var cy,sy,cr,sr,cp,sp,ge;
		var qe,q,ss,r,tr;

		if(devices.at(addr.port) != nil,{
			devices.at(addr.port).sensors.gyroEvent = (
				\x:msg[3].asFloat,
				\y:msg[2].asFloat,
				\z:msg[1].asFloat);
		});

		// ge = devices.at(addr.port).sensors.gyroEvent;
		// cy = cos(ge.x * 0.5);
		// sy = sin(ge.x * 0.5);
		// cr = cos(ge.z * 0.5);
		// sr = sin(ge.z * 0.5);
		// cp = cos(ge.y * 0.5);
		// sp = sin(ge.y * 0.5);
		//
		// devices.at(addr.port).sensors.quatEvent = (
		// 	\w: cy * cr * cp + sy * sr * sp,
		// 	\z: cy * sr * cp - sy * cr * sp,
		// 	\y: cy * cr * sp + sy * sr * cp,
		// \x: sy * cr * cp - cy * sr * sp);
		//
		//
		// qe = devices.at(addr.port).sensors.quatEvent;
		// q = Quaternion.new(qe.w,qe.z,qe.y,qe.x);
		// r = q.asEuler;
		// tr = [r[2],r[1],r[0]+ pi.half];
		// devices.at(addr.port).sensors.gyroEvent = (
		// 	\x:tr[0].asFloat,
		// 	\y:tr[1].asFloat,
		// \z:tr[2].asFloat);


	}, '/gyrosc/gyroSSSS', na); // see notes below



	d.listeners.accelListener = OSCFunc({ |msg, time, addr, recvPort|

		if(devices.at(addr.port) != nil,{
			devices.at(addr.port).sensors.accelEvent = (
				\x:msg[1].asFloat,
				\y:msg[2].asFloat,
				\z:msg[3].asFloat);
		});
	}, '/gyrosc/accel', na);


	// listen to all the airware that are connected (1 ip/port)
	numAirwareVirtualDevices.do({|i|


		var pattern = "/"++(i+1)++"/"++oscMessageTag;
		// var pattern = "/60:01:E2:E2:27:48/"++oscMessageTag;
		var address = NetAddr.new(d.ip, d.port - i);
		var prev = fourCh;
		var angVel = threeCh;
		var rx,ry,rz,ox=0,oy=0,oz=0;

		// if(devices.at(d.port) != nil,{
			d.listeners.airware = OSCFunc({ |msg, time, addr, recvPort|
				var sx,sy,sz,qe,q,ss,r, rq, rr, rtr;
				var tr;


				if(devices.at(addr.port+i) != nil,{
					var oq = devices.at(addr.port+i).sensors.quatEvent;

					devices.at(addr.port+i).sensors.accelEvent = (
						\x:msg[1].asFloat * 0.1,
						\y:msg[2].asFloat * 0.1,
						\z:msg[3].asFloat * 0.1);

					devices.at(addr.port+i).sensors.quatEvent = (
						\w:msg[7].asFloat,
						\x:msg[4].asFloat,
						\y:msg[5].asFloat,
						\z:msg[6].asFloat);


					// take quaternion and convert to ueler angles
				qe = devices.at(addr.port+i).sensors.quatEvent;
				q = Quaternion.new(qe.w,qe.x,qe.y,qe.z);
				r = q.asEuler;
				tr = [r[0],r[1],r[2]];


				// normalize gyro from 0 to pi
				rx = tr[2];
				ry = tr[0];
				rz = tr[1] * (pi.half + pi.half.half);

				if(rx <= 0, { rx = pi - (pi + rx)});
				if(ry <= 0, { ry = pi - (pi + ry)});
				if(rz <= 0, { rz = pi - (pi + rz)});

				// rx,ry,rz / pi = 0-1
				//•• need to save rx,ry,rz

				devices.at(addr.port+i).sensors.gyroEvent = (
					\x:tr[2].asFloat,
					\y:tr[0].asFloat,
					\z:tr[1].asFloat);


				// store rate of change
				devices.at(addr.port+i).sensors.rrateEvent = (
					\x:rx - ox,
					\y:ry - oy,
					\z:rz - oz);

				ox = rx;
				oy = ry;
				oz = rz;

			});
		}, pattern, address);
	});



	d.listeners.quatListener = OSCFunc({ |msg, time, addr, recvPort|

		var sx,sy,sz,qe,q,ss,r;
		var tr;
		// [msg, time, addr, recvPort].postln;

		if(devices.at(addr.port) != nil,{
			devices.at(addr.port).sensors.quatEvent = (
				\w:msg[1].asFloat,
				\x:msg[2].asFloat,
				\y:msg[3].asFloat,
				\z:msg[4].asFloat);

			// take quaternion and convert to ueler angles
			qe = devices.at(addr.port).sensors.quatEvent;
			q = Quaternion.new(qe.w,qe.x,qe.y,qe.z);
			r = q.asEuler;
			tr = [r[0],r[1],r[2] + pi.half];
			devices.at(addr.port).sensors.gyroEvent = (
				\x:tr[2].asFloat,
				\y:tr[0].asFloat,
				\z:tr[1].asFloat);
		});
	}, '/gyrosc/quat', na);

	/*
	quat being used for bounceOSC

	gyro being used for M5StickC

	quat being used for WemosBNO055 but range is wrong!

	each device needs to have it's own INPORT address:
	eg:
	-DINPORT=56145
	-DSTATIP=45




	*/

	d.listeners.ampListener = OSCFunc({ |msg, time, addr, recvPort|
		if(devices.at(addr.port) != nil,{
			devices.at(addr.port).sensors.ampValue = msg[1].asFloat;
		});
	}, '/gyrosc/amp', na);

	//• NEED TO TEST THIS!! working for Wemos only
	d.listeners.voltListener = OSCFunc({ |msg, time, addr, recvPort|
		var v = "volt"+msg[1].round(0.1).asString;
		v.postln;
		{voltButton.states = [[v,Color.red]]}.defer(0);
	}, '/gyrosc/volt');

	d.listeners.lineListener = OSCFunc({ |msg, time, addr, recvPort|
		var scale = 1;
		if(devices.at(addr.port) != nil,{
			// if( msg[11].asInt > 1, {
			devices.at(addr.port).blob = (
				//msg[0] is the path
				\index: msg[1].asInt,
				\area: msg[2].asFloat,
				\perimeter: msg[3].asFloat,
				\center: Point(
					msg[4].asFloat * scale,
					msg[5].asFloat * scale),
				\rect: Rect(
					msg[6].asFloat * scale,
					msg[7].asFloat * scale,
					msg[8].asFloat * scale,
					msg[9].asFloat * scale),

				\label: msg[10].asInt,
				\velocity: Point(msg[11],msg[12]),
				\dataSize: msg[13].asInt,
				\data: msg.copyRange(14,256)


			);
			// },{
			// 	//devices.at(addr.port).blob = ();
			// });

		});
	}, '/gyrosc/line', na);///•••••••
};

startOSCListening = {

	// handy way to listen to multiple ports
	// 4.do{|i| thisProcess.openUDPPort(57120 + i)};

	// listen for data and if found, add airware virtual device and stop listening
	numAirwareVirtualDevices.do({|i|
		airstickListeners.add( OSCFunc({ |msg, time, addr, recvPort|
			{
				if(devices.at(addr.port+i) == nil,{
					var d = addDevice.(addr.ip,addr.port+i);
					//addOSCDeviceListeners.(d);
					["device:",i, d.port].postln;
					airstickListeners[i].free;

				});
			}.defer;
			},"\/"++(i+1)++"\/"++oscMessageTag));
// },"\/60:01:E2:E2:27:48\/"++oscMessageTag));
	});

	// trigger device creation via OSC
	buttonListener = OSCFunc({ |msg, time, addr, recvPort|
		[msg, time, addr, recvPort].postln;
		if(msg[1].asFloat == 1.0, {
			if(devices.at(addr.port) == nil,{
				{
					var d = addDevice.(addr.ip, addr.port);
				}.defer;
			});
		},{
			// {
			// 	//• TODO UI is not repsonsive outside its scope
			// 	// removeDeviceButton.valueAction_(0);
			// }.defer;
		});

	}, '/gyrosc/button');
};


stopOSCListening = {
	numAirwareVirtualDevices.do({|i|
		airstickListeners[i].free;
	});


	buttonListener.free;
};

//------------------------------------------------------------
//
//------------------------------------------------------------


createWindowView = {|view|

	var scroll = ScrollView(view,Rect(0,30,width ,height - 50 ));
	var d;

	StaticText(view)
	.stringColor_(Color.yellow)
	.font_(Font(size:14))
	.minHeight_(30)
	.minWidth_(200)
	.string_(" :: osc music");

	contentView.layout_(VLayout());
	contentView.maxHeight_(5000);
	scroll.canvas = contentView;

	// example of 	loading a device (can only make 1 with generator)
	// d = addDevice.("127.0.0.1",53692);
	// addOSCDeviceListeners.(d);
	// d = addDevice.("127.0.0.1",53692+1);
	// addOSCDeviceListeners.(d);
};

//------------------------------------------------------------
//
//------------------------------------------------------------

s.waitForBoot({
	startup.();
	buildUI.();
	startOSCListening.();

});

)

