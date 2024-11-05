(

// Global config

// var personalityDir = "~/Develop/SuperCollider/Projects/SCbounce/personalities/";
var personalityDir = "~/Develop/SuperCollider/Projects/scbounce/personalities/";
// var personalityDir = "~/Develop/SuperCollider/oscMusic/personalities/";
var defaultPersonality = "wingChimes1";
var defaultList = "list_yourDNA.sc";
var oscMessageTag  = "CombinedDataPacket";
// var oscMessageTag  = "IMUFusedData";
var renderRate = 30;

// UI config
var windowWidth = 600, windowHeight = Window.screenBounds.height * 0.9;
var dataSizeOptions = [100,200,300,400];

// Device managment
var devices = Dictionary();
var names;
var airstickListeners = [], numAirwareVirtualDevices = 4;

// UI elements
var contentView = UserView().background_(Color.grey(0.2));
var reloadButton;
var removeDeviceButton;
var infoView;


// Functions
var startup, shutdown;
var loadPersonalityList, interpretPersonality, reloadPersonality;
var eulerToQuaternion, createProcRout;
var buildUI, addDevice, removeDevice;
var addDeviceView, createPlotterGroup, createThreeDeeCanvas, createTwoDeeCanvas;
var createWindowView;
var addOSCDeviceListeners, startOSCListening, stopOSCListening;


//------------------------------------------------------------
// Models
//------------------------------------------------------------

var twoCh = (\x: 0, \y:0);
var threeCh = (\x: 0, \y:0, \z:0);
var fourCh = (\w: 0, \x: 0, \y:0, \z:0);


var com = (
	\root: 0,
	\dur: 1,
	\accelMass: 0,
	\rrateMass: 0,
);

var listenersProto = (
	\airware:nil,
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

var deviceProto = (
	\name: defaultPersonality,
	\ip: "127.0.0.1",
	\port: 57120,
	\did: "nil",
	\enabled: true, // are we running
	\dataSize: dataSizeOptions[0],
	\listeners: Event.new(proto:listenersProto),
	\env: nil,	// Environment for injected code
	\procRout: nil,	// Routine calls ~next every ~fps
	\sensors: Event.new(proto:sensorsProto),
	\sensorBus: Bus.control(s,7);
);

/*

Sensor ControlBus

accel.x
accel.y
accel.z
quant.x
quant.y
quant.z
quant.w



*/
//------------------------------------------------------------
//
//------------------------------------------------------------

loadPersonalityList = {

	var path = PathName.new(personalityDir++defaultList);
	var file = File.new(path.asAbsolutePath,"r");
	var str = file.readAllString;

	interpret(str)
};

//------------------------------------------------------------
interpretPersonality = {|d|

	var path = PathName.new(personalityDir++d.name++".sc");
	var file = File.new(path.asAbsolutePath,"r");
	var str = file.readAllString;

	// after adding personality to an Environment, add useful functions to be used by anyone
	var env = Environment.make {
		~model = (
			\com: com,
			\name: d.name,
			\ptn: Array.fill(16,{|i|i=90.rrand(65).asAscii.toLower}).join(),
			\rrateMass: 0,
			\rrateMassFiltered: 0,
			\rrateMassFilteredAttack: 0.8,
			\rrateMassFilteredDecay: 0.1,
			\rrateMassThreshold: 0.21, //use for isMoving
			\rrateMassThresholdSpec: ControlSpec(0.07, 0.4, \lin, 0.01, 0.21),
			\accelMass: 0,
			\accelMassFiltered: 0,
			\accelMassFilteredAttack: 0.5,
			\accelMassFilteredDecay: 0.08,
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
			~model.accelMass = d.sensors.accelEvent.sumabs * 0.33; // scale it
			~model.rrateMass = d.sensors.rrateEvent.sumabs;
			~model.accelMassFiltered = ~smooth.(
				~model.accelMass,
				~model.accelMassFiltered,
				~model.accelMassFilteredAttack,
				~model.accelMassFilteredDecay

			);
			~model.rrateMassFiltered = ~smooth.(
				~model.rrateMass,
				~model.rrateMassFiltered,
				~model.rrateMassFilteredAttack,
				~model.rrateMassFilteredDecay

			);
		};

		//------------------------------------------------------------
		~play = {
			postf("play : % \n",~model.name);
			Pdef(~model.ptn).play(0.125);

		};

		~stop = {
			postf("stop : % \n",~model.name);
			Pdef(~model.ptn).stop();
		};

		//------------------------------------------------------------
		~init = {
			postf("init : % [%] \n",~model.name, ~model.ptn);
		};

		//------------------------------------------------------------
		//------------------------------------------------------------
		~deinit = {
			postf("deinit : % [%] \n",~model.name, ~model.ptn);
		};

		//------------------------------------------------------------

		//------------------------------------------------------------
		interpret(str);
		//------------------------------------------------------------

		~smooth= {|input,history, attack=0.5, decay=0.05|
			var coeff = attack;
			if(history > input, {coeff = decay});
			(coeff * input + ((1 - coeff) * history))
			// history + coeff * (input - history)
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
createProcRout = {|d|

	Routine {

		loop{
			d.env.use{

				if(d.enabled == true,{

					// process data -> personality model
					~processDeviceData.(d);

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
	names = loadPersonalityList.();
};

//
//------------------------------------------------------------
shutdown = {

	stopOSCListening.();
	Routine{
		// everything called in the correct order but leaves synths hanging!?!
		s.sync;
		devices.keysValuesDo({|k,d|
			removeDevice.(d);
		});
		s.sync;
		Pdef.clear;
		s.sync;
		Server.freeAll;
		s.sync;
		s.queryAllNodes;
		s.sync;
		s.quit;
	}.play;


};
//------------------------------------------------------------
//
//------------------------------------------------------------
removeDevice = {|d|

	"removing device...".postln;
	d.procRout.stop();

	d.procRout.free;

	d.env.use{
		~deinit.();
	};

	d.listeners.airware.free; //?
};

addDevice = { |ip,port, id|

	var d = Event.new(proto:deviceProto);

	d.listeners = Event.new(proto:listenersProto);
	d.sensors =  Event.new(proto:sensorsProto);
	d.ip = ip;
	d.port = port;
	d.did = id;

	devices.put(port,d);
	reloadPersonality.(d);
	addDeviceView.(contentView, d);
	addOSCDeviceListeners.(d);

	d // return the device
};

reloadPersonality = { |d|

	// stop personality
	d.procRout.stop;
	d.procRout.free;

	if(d.env != nil,{ d.env.use{
		Routine{
			s.sync;
			~deinit.();
			s.sync;
		}.play;
	}});

	d.env = interpretPersonality.(d);
	d.env.use{
		Routine{
			s.sync;
			~init.();
			s.sync;
		}.play;
	};

	d.procRout = createProcRout.(d);
	d.procRout.reset.play(AppClock);

};

//------------------------------------------------------------
//
//------------------------------------------------------------
buildUI = {

	var window;

	QtGUI.palette = QPalette.dark;

	window = Window("osc music", Rect(400, 200, windowWidth, windowHeight), false).front;
	window.view.keyDownAction_({|view,char,mods,uni,code,key|
		if(uni==114,{//r
			devices.keysValuesDo({|k,v|
				reloadPersonality.(v);
			});
		});
	});

	window.onClose = {
		shutdown.();
	};
	CmdPeriod.doOnce({window.close});
	createWindowView.(window);

};

addDeviceView = { |view, d|

	var header, dataView;
	var va,vb,vc;
	var stackView, stackLayout;
	var popup,personalityMenu;
	var col = Color.rand(0.1,0.9).alpha_(0.75);

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

	var makeDataView = {|view|
		UserView(view)
		.background_(col)
		.minHeight_(20)
		.minWidth_(200)
		.drawFunc_({
			d.sensors.accelEvent.asString.drawAtPoint(10@0, Font(size:7));
			d.sensors.gyroEvent.asString.drawAtPoint(10@10, Font(size:7));
		})
		.animate_(true)

	};

	var removeDeviceButton = {|view|
		Button(view)
		.minWidth_(80)
		.states_([
			["x",Color.red(0.5)],
		])
		.action_({|b|
			dataView.remove();
			header.remove();
			stackView.remove();
			removeDevice.(d);
			devices.removeAt(d.port);
		})
	};

	var infoView = {|view|
		StaticText(view)
		.stringColor_(Color.white)
		.font_(Font(size:12))
		.minWidth_(100)
		.string_("ID: "+d.did+" OSC: ["++d.ip+", "+d.port++"]")
	};
	var muteButtonLocal;
	var muteButton = {|view|
		muteButtonLocal = Button()
		.enabled_(false) //broken
		.maxWidth_(80)
		.states_([["mute"],["mute",Color.red(0.5)]])
		.action_({|b|
			d.enabled = b.value.asBoolean;

			if(d.enabled == true,{
				d.env.use{~stop.()};
			},{
				d.env.use{~play.()};
			});
		})
	};

	var reloadButton = {|view|
		Button(view)
		.minWidth_(80)
		.states_([
			["reload"],
		])
		.action_({|b|
			{
				reloadPersonality.(d);
			}.defer(0.1);
		})
	};

	var personalityMenuView = {|view|
		personalityMenu = PopUpMenu(view)
		.font_(Font(size:16))
		.minWidth_(220)
		.minHeight_(40)
		.items_(names)
		.valueAction_(names.find([d.name]))
		.action_({|b|
			d.name = names.at(b.value);
			{reloadPersonality.(d)}.defer(0.1);
		})
	};

	var dataSizeMenu = {|view|
		PopUpMenu(view)
		.maxWidth_(80)
		.items_(dataSizeOptions.collect{|v| v+"pnts"})
		.action_({|b|
			d.dataSize = dataSizeOptions.at(b.value);
		})
		.valueAction_(1)
	};

	// build layout
	header = View(view).background_(col).maxHeight_(100).layout_( GridLayout.rows( [
		removeDeviceButton.(view),
		infoView.(view),
		reloadButton.(view)
	],[
		Button(view)
		.minHeight_(40)
		.font_(Font(size:16))
		.states_([["-"]])
		.action_({|b|
			if(personalityMenu.value == 0,
				{personalityMenu.value = personalityMenu.items.size - 1},
				{personalityMenu.value = personalityMenu.value - 1}
			);
			{personalityMenu.valueAction = personalityMenu.value}.defer;
		}),

		personalityMenuView.(view),
		Button(view)
		.minHeight_(40)
		.font_(Font(size:16))
		.states_([["+"]])
		.action_({|b|
			if(personalityMenu.value == (personalityMenu.items.size - 1),
				{personalityMenu.value = 0},
				{personalityMenu.value = personalityMenu.value + 1}
			);
			{personalityMenu.valueAction = personalityMenu.value}.defer;
		}),
	]
		// ,[
		// 	muteButton.(view),
		// 	UserView(view),
		// 	dataSizeMenu.(view)
		// ]
	 ));
	dataView = makeDataView.(view);

	view.layout.add(stackView = View()
		.background_(col)
		.layout_(
			stackLayout = HLayout(
				vc = UserView().background_(col),
				vb = UserView().background_(col),
		)).minHeight_(250)
	);
	createPlotterGroup.(vb,d);
	createThreeDeeCanvas.(vc,d);

	contentView.layout.add(nil);
};


//------------------------------------------------------------
// createPlotterGroup
//------------------------------------------------------------
// plots up to 3 streams of data

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
	var pd = [];


	plotData.().size.do({|i|
		st[i] = StaticText(view,Rect(10+(ph/2 * i), 210, pw * 0.2, 14))
		.string_("channel"+i)
		.font_(Font(size:9))
		.background_(Color.gray(0.25))
		.align_(\center)
		.stringColor_(col[i].alpha_(0.5));
	});

	checkBox.action_({
		plotterView.visible = checkBox.value;
	});
	checkBox.valueAction_(checkBox.value);

	plotterView.drawFunc_({});

	plotter.setProperties(\backgroundColor, Color.gray(0.25));

	plotterView.drawFunc = plotterView.drawFunc <> {
		{
			// another way but still slow
			pd = pd.addFirst(plotData.());
			if(pd.size > data.dataSize, {pd.pop()});
			plotter.superpose = true;
			plotter.setValue(pd, false, true, false);
			plotter.value = plotter.value.keep(data.dataSize).flop;

			// original version and cpu intensive
			// plotter.superpose = true;
			// plotter.value = plotter.value.flop;
			// plotter.value = plotter.value.insert(0, plotData.());
			// plotter.value = plotter.value.keep(data.dataSize);
			// plotter.value = plotter.value.flop;

			plotter.minval_(pmin);
			plotter.maxval_(pmax);
			plotter.setProperties(\plotColor, col).refresh;

		}.defer(0.1);// need to delay to allow for construction
	}

};

//------------------------------------------------------------
// Three Dee Canvas
//------------------------------------------------------------
// special view for 3d data

createThreeDeeCanvas = { |view, data|
	var graph1;
	var cube;
	var accelX, accelY, accelZ;
	var checkBox = CheckBox(view, Rect(10,-20,50,70), "3d").value_(true);

	graph1 = Canvas3D(view, Rect(10, 30, 570/2 - 30, 170))
	.scale_(160)
	.background_(Color.gray(0.25))
	.perspective_(0)
	.transforms_([Canvas3D.mTranslate(0,0,0)])
	.distance_(3.5);

	graph1.add(cube = Canvas3DItem.cube()
		.color_(Color.white.alpha_(0.4))
		.width_(2)
		.transform(Canvas3D.mScale(0.4,0.5,1))
	);

	graph1.add(accelX = Canvas3DItem.regPrism()
		.color_(Color.yellow(0.9))
		.width_(2)
	);
	graph1.add(accelY = Canvas3DItem.regPrism()
		.color_(Color.magenta(0.9))
		.width_(2)
	);
	graph1.add(accelZ = Canvas3DItem.regPrism()
		.color_(Color.cyan(0.9))
		.width_(2)
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

		cube.transforms = [
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
	};
};


//------------------------------------------------------------
// device listnener
//------------------------------------------------------------
// passes Airware OSC data to local models

addOSCDeviceListeners = {|d|

	var na = NetAddr.new(d.ip, d.port);
	var patternBase = "/%/" ++ oscMessageTag;

	// listen to all the airware that are connected (1 ip/port)
	numAirwareVirtualDevices.do({|i|


		var address = NetAddr.new(d.ip, d.port - i);
		var pattern = patternBase.format(i+1);
		// var pattern = patternBase.format("/60:01:E2:E2:27:48/");
		var prev = fourCh;
		var angVel = threeCh;
		var rx,ry,rz,ox=0,oy=0,oz=0;
		d.listeners.airware = OSCFunc({ |msg, time, addr, recvPort|
			var sx,sy,sz,qe,q,ss,r, rq, rr, rtr;
			var tr;


			if(devices.at(addr.port+i) != nil,{
				var oq = devices.at(addr.port+i).sensors.quatEvent;
				var bus = devices.at(addr.port+i).sensorBus;


				bus.setn([
					msg[1].asFloat * 0.1,
					msg[2].asFloat * 0.1,
					msg[3].asFloat * 0.1,
					msg[7].asFloat, //w
					msg[4].asFloat, //x
					msg[5].asFloat, //y
					msg[6].asFloat, //z
				]);
				// bus.getn(7).postln;testing

				devices.at(addr.port+i).sensors.accelEvent = (
					\x:msg[1].asFloat * 0.1,
					\y:msg[2].asFloat * 0.1,
					\z:msg[3].asFloat * 0.1
				);

				devices.at(addr.port+i).sensors.quatEvent = (
					\w:msg[7].asFloat,
					\x:msg[4].asFloat,
					\y:msg[5].asFloat,
					\z:msg[6].asFloat
				);


				//
				// Calculate others
				// take quaternion and convert to ueler angles
				qe = devices.at(addr.port+i).sensors.quatEvent;
				q = Quaternion.new(qe.w,qe.x,qe.y,qe.z);
				r = q.asEuler;
				tr = [r[0],r[1],r[2]];

				devices.at(addr.port+i).sensors.gyroEvent = (
					\x:tr[2].asFloat,
					\y:tr[0].asFloat,
					\z:tr[1].asFloat);

				// normalize gyro from 0 to pi
				rx = tr[2];
				ry = tr[0];
				rz = tr[1] * (pi.half + pi.half.half);

				if(rx <= 0, { rx = pi - (pi + rx)});
				if(ry <= 0, { ry = pi - (pi + ry)});
				if(rz <= 0, { rz = pi - (pi + rz)});

				// rx,ry,rz / pi = 0-1 ie. normalized
				//•• need to save rx,ry,rz

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
};

//------------------------------------------------------------
// osc listnener
//------------------------------------------------------------

startOSCListening = {

	var patternBase = "/%/" ++ oscMessageTag;

	// listen for data and if found, add airware virtual device and stop listening
	numAirwareVirtualDevices.do({|i|
		var pattern = patternBase.format(i+1);
		// var pattern = patternBase.format("60:01:E2:E2:27:48");

		airstickListeners = airstickListeners.add( OSCFunc({ |msg, time, addr, recvPort|
			{
				if(devices.at(addr.port+i) == nil,{
					var d = addDevice.(addr.ip,addr.port+i,i+1);
					postf("device auto detected : % \n", d.did);
					//
					// airstickListeners[i].free;
					// airstickListeners.removeAt(i);
				});
			}.defer;
		}, pattern));
	});


};

stopOSCListening = {
	airstickListeners.do({|obj|
		postf("free : %\n", obj);
		obj.free;
	});
};

//------------------------------------------------------------
//
//------------------------------------------------------------


createWindowView = {|view|

	var scroll = ScrollView(view,Rect(0,30,windowWidth ,windowHeight- 50 ));
	var d;
	var wifiAddress = ("ifconfig | grep \"\inet \"\ | grep -v 127.0.0.1 | awk '{print $2}'").unixCmdGetStdOut();
	var wifiInfoView, cpuInfo;
	wifiAddress = wifiAddress[0..wifiAddress.size-2];

	wifiInfoView = StaticText(view)
	.stringColor_(Color.gray(0.5))
	.align_(\right)
	.font_(Font(size:12))
	.minHeight_(30)
	.minWidth_(windowWidth)
	.string_("OSC: ["++wifiAddress++", "+NetAddr.localAddr.port++"] ");

	cpuInfo = UserView(view)
		.maxWidth_(80)
		.maxHeight_(30)
		.animate_(true)
		.drawFunc_({|uv|
		(s.peakCPU.asStringPrec(2)++"%").drawAtPoint(8@8, Font.default, Color.yellow);
	});
	// view.layout_( HLayout(cpuInfo,wifiInfoView));
	contentView.layout_(VLayout());
	contentView.maxHeight_(5000);
	scroll.canvas = contentView;

};
//------------------------------------------------------------
//
//------------------------------------------------------------
//[ Built-in Microph, Built-in Output, Soundflower (2ch), Soundflower (64ch), ZoomAudioD, Zoomy, SF Record ]

// Server.local.options.outDevice = ServerOptions.devices[
// ServerOptions.devices.indexOfEqual("Soundflower (2ch)")];

// Server.local.options.outDevice = ServerOptions.devices[
// ServerOptions.devices.indexOfEqual("Built-in Output")];

//Server.local.options.outDevice = ServerOptions.devices[
//	ServerOptions.devices.indexOfEqual("SERIES 208i")];

s.waitForBoot({
	startup.();
	buildUI.();
	startOSCListening.();
});
// s.plotTree;

)
