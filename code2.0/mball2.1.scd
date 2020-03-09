/*
Router
user: admin
pw: zxzxzxzx

IP: 192.168.10.x
SUB: 255.255.255.0
Router 192.168.1.1 (wifi)	
Router 192.168.10.1 (ethernet)

SSID : SOHLA
pw : zxzxzxzx


Router setup with Laptop 
192.168.10.x
255.255.255.0
192.168.10.1


Ports

SC Laptop	57120
Recorder 	57201

*/


	(
		// 
		var devicesDir = "~/Develop/SuperCollider/SCbounce/personalities/";
		var dataPath = "~/Develop/SuperCollider/SCbounce/data/";
		//var image = Image.new("~/Develop/SuperCollider/Projects/SCbounce/m-ball_logo_vector600.png".asAbsolutePath);

		var midiControlOffset = 1;
		var loadDeviceList;

		var names;// = ["adam","george","harry","susan","eve"];
		//var faces = ["🦆","🦊","🐴","🐯","🍎"];


	    var width = Window.screenBounds.width * 0.9, height = Window.screenBounds.height * 0.7;
		var startup, shutdown, buildUI;

		var contentView = UserView().background_(Color.grey(0.2));

		var reloadButton;
		var voltButton;

		// var stackButton;

		var createPlotterGroup, createGyroCanvas, createAccelCanvas, createTransportView;
		var createWindowView, addDeviceView;
		var startOSCListening, stopOSCListening, enableOSCListening, disableOSCListening, addOSCDeviceListeners;

		var addDevice, removeDevice;

	    var gryoListner, rotMatListner, rrateListener, accelListener, quatListener, altListener, ampListener;
	    var buttonListener, voltListener;

	    var oscOut = NetAddr.new("127.0.0.1", 9003); 

		var devices = Dictionary();

		var dataRate = 1; 
		var renderRate = 20;

		var loadPersonality;
		var reloadDevice;
		var createProcRout;
		var createMidiRout;
		
		var createGenerator;


		var dataSizes = [100,200,300,400];
		var dataSize = dataSizes[0];

		var midiOut, midiController;

		var midiControllers = []; //hold the sliders and values 

		var eulerToQuaternion;

		var com = (
			\root: 0,
			\dur: 1,
			\accelMass: 0,
			\rrateMass: 0,
		);

		var comRout = Routine { 
	    	loop {

				var a = 0, r = 0;

				devices.keysValuesDo({|k,v|
					v.env.use{

						a = a + ~model.accelMassFiltered;
						r = r + ~model.rrateMassFiltered;

						//• pre call each device 

						//• call com process functiom

						//• post call each device
					 };
				});

				com.accelMass = a;
				com.rrateMass = r;

				0.06.yield;

		    }
		}.play;


	    // data
		var deviceProto = (
			\name: "eve2",
			\ip: "127.0.0.1",
			\port: 57120,
			\gyroEvent: (\pitch:0,\roll:0,\yaw:0),
			//\gyroMass: 0,
			\rrateEvent: (\x:0,\y:0,\z:0),
			\rrateMass: 0,
			\accelEvent: (\x:0,\y:0,\z:0),
			\accelMass: 1,
			\quatEvent: (\w:0,\x:0,\y:0,\z:0),
			\ampValue: 0,
			\env: nil,	// Environment for injected code
			\procRout: nil,	// Routine calls ~next every ~fps
			\midiRout: nil,	// Routine calls ~next every ~fps
			\enabled: true, // are we running
			\generator: nil, // Routine for generating data
			);


		// var path = PathName.new("~/Develop/SuperCollider/Projects/SCbounce/helloWorld.scd");
		// var file = File.new(path.asAbsolutePath,"r");
		// var str = file.readAllString;
		// interpret(str);
		// as a loader
		//import.("helloWorld.scd");
	//------------------------------------------------------------	
	// midi
	//------------------------------------------------------------	
	MIDIClient.init;
	MIDIClient.destinations;

	midiOut = MIDIOut.newByName("IAC Driver", "Bus 1", dieIfNotFound: true);
	midiOut.latency_(0.00);

	midiController = MIDIOut(2).latency_(0.0);
	MIDIIn.connectAll;
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
	loadPersonality = {|name|

		var path = PathName.new(devicesDir++name++".sc");
		var file = File.new(path.asAbsolutePath,"r");
		var str = file.readAllString;

		// after adding personality to an Environment, add useful functions to be used by anyone
		var env = Environment.make {


			~model = (
				\com: com,
				\name: name,
				\ptn: Array.fill(16,{|i|i=90.rrand(65).asAscii}),
				\midiOut: midiOut,
				\midiChannel: 1,

				\rrateMass: 0,
				\rrateMassFiltered: 0,
				\rrateMassThreshold: 0.1, //use for isMoving
				\rrateMassThresholdSpec: ControlSpec(0.05, 0.4, \lin, 0.01, 0.22),

				\accelMass: 0,
				\accelMassFiltered: 0,
				\accelMassAmpThreshold: 0.4,
				\accelMassThresholdSpec: ControlSpec(0.4, 3.0, \lin, 0.1, 1.7),

				\isHit: false,
				\isMoving: true,
				\accelMassAmp: 0.0,

			);

			//------------------------------------------------------------	
			// frame rate of rout
			~secs = 0.03;

			//------------------------------------------------------------	
			// process data->model
			~processDeviceData = {|d|

				~model.accelMass = d.accelEvent.sumabs * 0.33;
				~model.rrateMass = d.rrateEvent.sumabs * 0.1;

				~model.accelMassFiltered = ~tween.(~model.accelMass, ~model.accelMassFiltered, 0.2);
				~model.rrateMassFiltered = ~tween.(~model.rrateMass, ~model.rrateMassFiltered, 0.5);

			};

			//------------------------------------------------------------	
			// process triggers
			~processTriggers = {|d|
				// isHit
				// TODO smarter gate with min/max threasholds and level
				// use improved code in eve2
				// if(~model.accelMassFiltered > ~model.accelMassThreshold,{
				// 	if(~model.isHit == false,{
				// 		~model.isHit = true;
				// 		~onHit.(~model.isHit);
				// 	});
				// },{
				// 	if(~model.isHit == true,{
				// 		~model.isHit = false;
				// 		~onHit.(~model.isHit);
				// 	});
				// });


				var changeState = {|state|
					if(~model.isHit != state,{
						~model.isHit = state;
						if(~model.isHit == true,{
							//"Note ON".postln;
							~onHit.(~model.isHit);
						},{
							//"Note OFF".postln;
							~onHit.(~model.isHit);
						});
					});
				};

				// should we tweak this constants!?
				// Lost lands hitting with helium. only need the microphone to detect hits
				//• need to pass these thrsholds on to model!
				if( ~model.accelMass > ~model.accelMassAmpThreshold,{ 
					//if( d.ampValue > 0.00,{
						~model.accelMassAmp = 0.1;//d.ampValue;
						//~model.accelMass.postln;
					// },{
					// 	~model.accelMassAmp = 0.0;
					// });
					//~model.accelMassAmp = 0.0;
				},{
					~model.accelMassAmp = 0.0;
				});

				if(~model.accelMassAmp > 0.0,{
					changeState.(true);
				},{
					changeState.(false);
				});

				//isMoving
				if(~model.rrateMassFiltered > (~model.rrateMassThreshold.half - 0.02),{

					if(~model.isMoving == false,{
						~model.isMoving = true;
						~onMoving.(~model.isMoving);
						//Pdef(~model.ptn).resume();
					});

					//midiOut.control(midiChannel, 1, (smooth*127).asInteger );
				},{

					if(~model.isMoving == true,{
						~model.isMoving = false;
						~onMoving.(~model.isMoving);
					});
				});			
			};
			//------------------------------------------------------------	
			~play = {
				Pdef(~model.ptn).play();
				("play" + ~model.name).postln;
			};

			~stop = {
				Pdef(~model.ptn).stop();
				("stop" + ~model.name).postln;
				~model.midiOut.allNotesOff(~model.midiChannel);
			};

			//------------------------------------------------------------	
			~init = { 
				("init" + ~model.name).postln;
			};

			//------------------------------------------------------------	
			~buildPattern = {
				Pdef(~model.ptn).set(\type,\midi);
				Pdef(~model.ptn).set(\midiout,~model.midiOut);
				Pdef(~model.ptn).set(\chan,~model.midiChannel);
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
	createMidiRout = {|d|

		Routine { 

			loop{ 
				d.env.use{

					if(d.enabled == true,{
		
						//midi out has it's own set interval
						~nextMidiOut.(d);

					});

					(0.1).yield;
				};
			};
		}
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


		comRout.stop();
		midiControllers.do{|mc|mc.postln;mc.free};

		devices.keysValuesDo({|k,v|
			v.procRout.stop();
			v.midiRout.stop();
			v.generator.stop();
			v.env.use{ ~deinit.() };
		});

		// strange bug, we can not restart if we don't defer
		{MIDIClient.disposeClient}.defer(1);


		stopOSCListening.();
		s.queryAllNodes;
		
		Pdef.clear;
		Server.freeAll;

	};
	//------------------------------------------------------------	
	// 
	//------------------------------------------------------------	

	addDevice = { |ip,port|

		var d = Event.new(proto:deviceProto);
		
		d.ip = ip;
		d.port = port;

		devices.put(port,d);
		
		// load the data
		reloadDevice.(d);

		d // return the device (data)
	};

	reloadDevice = { |d|


		// stop personality

		d.procRout.stop;
		d.midiRout.stop;
		if(d.env != nil,{ d.env.use{ ~deinit.() }});


		// start new personality
		d.env = loadPersonality.(d.name);
		d.env.use{ 
			~init.(); 
			~buildPattern.();
		};


		d.procRout = createProcRout.(d);
		d.procRout.reset.play(AppClock);

		d.midiRout = createMidiRout.(d);
		d.midiRout.reset.play(AppClock);


		d.generator = createGenerator.(d);
		//d.generator.reset.play(AppClock);

	};

	//------------------------------------------------------------	
	// 
	//------------------------------------------------------------	
	buildUI = {
		
		var window;

		QtGUI.palette = QPalette.dark; 

		GUI.skin.plot.gridLinePattern = FloatArray[1, 0];
		GUI.skin.plot.gridColorX = Color.yellow(0.5);
		GUI.skin.plot.gridColorY = Color.yellow(0.5);
		GUI.skin.plot.background = Color.black;
		GUI.skin.plot.plotColor = Color.white;

		window = Window("m-ball", Rect(400, 200, width, height), false).front;
		window.view.keyDownAction_({|view,char,mods,uni,code,key|
			if(uni==114,{//r
				devices.keysValuesDo({|k,v|
					reloadDevice.(v);
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

	createWindowView = {|view|

		var scroll = ScrollView(view,Rect(0,30,width,height - 150 ));
		var d;

		StaticText(view)
				.stringColor_(Color.yellow)
				.font_(Font(size:14))
				.minHeight_(30)
				.minWidth_(200)
				.string_(" :: m-ball");

		contentView.layout_(VLayout());
		scroll.canvas = contentView;

		// example of loading a device (can only make 1 with generator)
		// d = addDevice.("127.0.0.1",64564);
		// addDeviceView.(contentView, d);
		// addOSCDeviceListeners.(d);

	};

	addDeviceView = { |view, d|

		var header;
		var va,vb,vc;
		var stackView, stackLayout;
		var dataSizeMenu;
		var popup;
		var col = Color.rand(0.1,0.9).alpha_(0.8);

		var createGraphs = {

			// createPlotterGroup.(vc, Rect(850,5,400,240), col, [
			// 	["pitch",Color.red,{|p| d.gyroEvent.pitch }],
			// 	["roll",Color.green,{|p| d.gyroEvent.roll }],
			// 	["yaw",Color.blue,{|p| d.gyroEvent.yaw }]
			// ],-pi,pi);

			// createPlotterGroup.(vb, Rect(850,5,400,240), col, [
			// 	["aaccelX",Color.cyan,{|p| d.accelEvent.x }],
			// 	["accelY",Color.magenta,{|p| d.accelEvent.y }],
			// 	["accelZ",Color.yellow,{|p| d.accelEvent.z }]
			// ],-2,2);

			// createPlotterGroup.(va, Rect(850,5,400,240), col, [
			// 	["rrateX",Color.cyan,{|p| d.rrateEvent.x }],
			// 	["rrateY",Color.magenta,{|p| d.rrateEvent.y }],
			// 	["rrateZ",Color.yellow,{|p| d.rrateEvent.z }]
			// ],-6,6);

			createPlotterGroup.(va, Rect(250,5,400,240), col, [
				["ymc",[Color.yellow,Color.magenta,Color.cyan],{|p| d.env.use{ ~plot.(d)} }]
			],d.env.use{ ~plotMin.()},d.env.use{ ~plotMax.()});
		};

		var removeGraphs = {
			va.children[2].removeAll;
			va.children[2].remove;
			// va.children[2].removeAll;
			// va.children[2].remove;
			// vb.children[1].removeAll;
			// vb.children[1].remove;
			// vc.children[0].removeAll;
			// vc.children[0].remove;
		};

		var sliderViews = {|i|

			var label = StaticText().string_(["Movement","Trigger"].at(i));
			var valueLabel = StaticText()
				.string_("-");

			VLayout(
				label,
				Slider()
				.maxWidth_(40)
				.action_({|o|
					d.env.use{
						//~midiControllerValue.(i,o.value);

						switch(i,
							0, {
								// for isMoving
								~model.rrateMassThreshold = ~model.rrateMassThresholdSpec.map(o.value);
								valueLabel.string_(~model.rrateMassThresholdSpec.map(o.value).round(0.01));

								(~model.rrateMassThreshold.reciprocal).postln;
						},1, {
								~model.accelMassAmpThreshold = ~model.accelMassThresholdSpec.map(o.value);
								valueLabel.string_(~model.accelMassThresholdSpec.map(o.value).round(0.01));
						});
					};

				})
				.valueAction_(
					d.env.use{
						switch(i,
							0, {
								~model.rrateMassThresholdSpec.unmap(~model.rrateMassThresholdSpec.default)
							},1, {
								~model.accelMassThresholdSpec.unmap(~model.accelMassThresholdSpec.default)
							});
					}
				)


				,valueLabel
			)}!2;
		var sliderView = {|v|
			UserView(v,Rect(700,0,200,250)).layout_( HLayout(
				*sliderViews.collect{|c,i|

					var mc = MIDIFunc.cc({|val, num, chan| 
						//[c,val, num, chan].postln;
						//• TODO 
						{
							c.valueAction_(val/127.0);

						}.defer(0);
					},midiControlOffset+i);

					midiControllers = midiControllers.add(mc);	

					c
				};
				)
			);

		};

		var onOffButton;

		header = View(view).background_(col).maxHeight_(50).layout_( HLayout(

			onOffButton = Button()
				.maxWidth_(40)
				.states_([["mute",Color.yellow],["mute"]])
				.valueAction_(1)
				.action_({|b|
					d.enabled = b.value.asBoolean;

					if(d.enabled == true,{
						reloadDevice.(d);
//						d.env.use{~play.()};
					},{
						d.env.use{~stop.()};
					});
				}),	

			voltButton = Button()
				.maxWidth_(80)
				.states_([["volt",Color.red]])
				.action_({|b|
					n = NetAddr.new(d.ip, d.port.asInt);
					n.postln;
					n.sendMsg("/togyrosc/volt", 42.asInt);
				}),			

			StaticText(view)
				.stringColor_(Color.white)
				.font_(Font(size:14))
				.maxWidth_(200)
				.string_(d.ip+":"+d.port),
			Button()
				.maxWidth_(40)
				.states_([["reset"]])
				.action_({
						d.ip.class.postln;
						d.port.class.postln;
					b = NetAddr.new(d.ip, d.port.asInt+1);    // create the NetAddr
					b.sendMsg("/bounce", "motionReset");    // send the application the messa

				}),
			Button()
				.maxWidth_(80)
				.states_([["generator", Color.white],["generator",Color.green]])
				.action_({|b|
					b.value.postln;
					if(b.value == 1,{
						d.generator.reset.play(AppClock);
					},{
						d.generator.stop();
					});
				}),
			// stackButton = Button(view)
			// 	.maxWidth_(100)
			// 	.states_([
			// 		["attitude"],	
			// 		//["accelerometer"]
			// 	])
			// 	.action_({|b|
			// 		stackLayout.index = b.value;
			// 	}),
			Button(view)
				.maxWidth_(60)
				.states_([
					["graphs"],	
					["graphs",Color.green]
				])
				.action_({|b|
					if(b.value == 1,{
						createGraphs.();

					},{
						removeGraphs.();
					});
				}),
			dataSizeMenu = PopUpMenu(view)
					.maxWidth_(60)
					.items_(dataSizes)
					.valueAction_(0)
					.action_({|b|
						dataSize = dataSizes.at(b.value);
					}),


			reloadButton = Button(view)
				.maxWidth_(80)
				.states_([
					["reload"],	
				])
				.action_({|b|
					//reloadDevice.(d);
					onOffButton.valueAction_(1);
					// load the list again allows us to make changes
					names = loadDeviceList.();
					popup.items = names;
					popup.valueAction = names.find([d.name]);

				}),

			popup = PopUpMenu(view)
					.minWidth_(160)
					.items_(names)
					.valueAction_(names.find([d.name]))
					.action_({|b|
						d.name = names.at(b.value);
						reloadDevice.(d);
					}),

			Button(view)
				.maxWidth_(40)
				.states_([
					["x",Color.red(0.5)],	

				])
				.action_({|b|
					header.remove();
					stackView.remove();
					
					// stop personality
					d.env.use{ ~deinit.() };
					d.procRout.stop;
					d.midiRout.stop;

					// remove midi controller listener
					midiControllers.do{|mc|mc.postln;mc.free};

					devices.removeAt(d.port);

				})

		));
		//view.layout.spacing_(1);
		view.layout.add(stackView = View()
			//.background_(Color.yellow)
			.minHeight_(250)
			.maxHeight_(250)
			.layout_( stackLayout = StackLayout(
				va = View().background_(col),
				//vb = View().background_(col),
				//vc = View().background_(col)
				)
			);
		);	

		sliderView.(va);
		createGyroCanvas.(va,d);
		//createAccelCanvas.(vb,d);

		contentView.layout.add(nil);
	};

	//------------------------------------------------------------	
	// Plotters
	//------------------------------------------------------------	

	createPlotterGroup = {|view, bounds, col, plotters, pmin=(-pi), pmax=pi|


		var plotterView = UserView(view,bounds).animate_(true);
		var size = plotters.size;
		var pw = bounds.width;
		var ph = bounds.height;// / size;

		 plotterView.drawFunc_({});
		 plotters.do{ |specs,i|


			var max = 0;
			var plotter = Plotter(specs[0], Rect(pw*0.1,ph*i,pw*0.9,ph),plotterView)
				.value_((0..dataSize).dup(plotters.size)) //need to init arrays with data
				.refresh;


			var st = StaticText(plotterView,Rect(0,ph*i,pw*0.1,ph/2))
				.string_("CH 0")
				.font_(Font(size:9))
				.align_(\center)
				.stringColor_(specs[1][0]);

			var su = StaticText(plotterView,Rect(0,(ph*i)+(ph/3),pw*0.1,ph/2))
				.string_("CH 1")
				.font_(Font(size:9))
				.align_(\center)
				.stringColor_(specs[1][1])
				.mouseUpAction_({max = 0});

			var sv = StaticText(plotterView,Rect(0,(ph*i)+(ph/3 * 2),pw*0.1,ph/2))
				.string_("CH 2")
				.font_(Font(size:9))
				.align_(\center)
				.stringColor_(specs[1][2])
				.mouseUpAction_({max = 0});

			// StaticText(plotterView,Rect(0,ph*i,pw*0.15,ph/2))
			// 	.string_(specs[0])
			// 	.align_(\center)
			// 	.stringColor_(specs[1])
			// 	.background_(Color.black);
			plotter.setProperties(\backgroundColor, Color.gray(0.25));

			plotterView.drawFunc = plotterView.drawFunc <> {
				{
					
					plotter.superpose = true;
					plotter.value = plotter.value.flop;
					plotter.value = plotter.value.insert(0,specs[2].(plotter));
					plotter.value = plotter.value.keep(dataSize);
					plotter.value = plotter.value.flop;

					// old way of parsing single values
					// p.value = p.value.shift(1).putFirst(d.env.use{ ~plot.(d)});
					
					plotter.minval_(pmin);
					plotter.maxval_(pmax);

					plotter.setProperties(\plotColor, specs[1]).refresh;

				}.defer(0.1);// need to delay to allow for construction 
			}
		 };


	};

	//------------------------------------------------------------	
	// Gyro Canvas
	//------------------------------------------------------------	

	createGyroCanvas = { |view, data|
		var graph1;
		var cube, top, rate, loc, ico;
		var p1,p2,p3;
		var t = (1.0 + (5.0).sqrt) / 2.0;
	    var accelX, accelY, accelZ;

		graph1 = Canvas3D(view, Rect(5, 5, 245, 240))
		    .scale_(200)
			.background_(Color.black)
		    .perspective_(0.5)
		    .distance_(3.5);

		graph1.preDrawFunc = {
			// var scale = 0.22;
			// Pen.scale(scale,scale);
			// Pen.drawImage( Point(0,0), image, operation: 'sourceOver', opacity:0.05);
			// Pen.scale(scale.reciprocal,scale.reciprocal);

		};

		// graph1.view.drawFunc= graph1.view.drawFunc<>{
		// 	("[data"+"}-->".scramble).draw
		// };

		// graph1.add(cube = Canvas3DItem.cube
		//     .color_(Color.red(0.8))
		//     .width_(1)
		// );

		// graph1.add(top = Canvas3DItem.grid(4)
		// 	.color_(Color.green(0.9))
		//     .width_(1)
		//     // need to rotate for grids orientation
		//     .transform(Canvas3D.mRotateY(pi/2))
		// );

		// graph1.add(rate = Canvas3DItem.grid(2)
		// 	.color_(Color.white(0.9))
		//     .width_(1)
		// );

		// graph1.add(loc = Canvas3DItem.regPrism(3)
		// 	.color_(Color.blue(0.9))
		//     .width_(1)
		// );


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

		// animate
		graph1.animate(renderRate) {|t|

			var tr = [data.gyroEvent.roll,data.gyroEvent.yaw,data.gyroEvent.pitch];
			//data.ip.postln;
	        
	        // loc.transforms = [
	        // 	Canvas3D.mScale(1,0.02,0.02),
	        // 	Canvas3D.mTranslate(1.5,0,0),
		       //  Canvas3D.mRotateX(tr[0]),
		       //  Canvas3D.mRotateY(tr[1]),
		       //  Canvas3D.mRotateZ(tr[2]),
	        // ];

			// cube.transforms = [ 
			// 	Canvas3D.mScale(0.05,1,0.5),
			// 	Canvas3D.mTranslate(0,0,0),
		 //        Canvas3D.mRotateX(tr[0]),
		 //        Canvas3D.mRotateY(tr[1]),
		 //        Canvas3D.mRotateZ(tr[2]),
		 //    ];

			// top.transforms = [ 
			// 	Canvas3D.mScale(0.05,1,0.5),
			// 	Canvas3D.mTranslate(1,0,0),
		 //        Canvas3D.mRotateX(tr[0]),
		 //        Canvas3D.mRotateY(tr[1]),
		 //        Canvas3D.mRotateZ(tr[2]),
		 //    ];

		    ico.transforms = [ 
				Canvas3D.mScale(0.6,0.6,0.6),
		        Canvas3D.mRotateX(tr[0]),
		        Canvas3D.mRotateY(tr[1]),
		        Canvas3D.mRotateZ(tr[2]),
		    ];
		    
			accelY.transforms = [
				Canvas3D.mScale(0.01,(data.accelEvent.y),0.01),
		        Canvas3D.mRotateX(data.gyroEvent.roll),
		        Canvas3D.mRotateY(data.gyroEvent.yaw),
		        Canvas3D.mRotateZ(data.gyroEvent.pitch),
		    ];

			accelX.transforms = [ 
				Canvas3D.mScale((data.accelEvent.z),0.01,0.01),
		        Canvas3D.mRotateX(data.gyroEvent.roll),
		        Canvas3D.mRotateY(data.gyroEvent.yaw),
		        Canvas3D.mRotateZ(data.gyroEvent.pitch),
		    ];
			accelZ.transforms = [ 
				Canvas3D.mScale(0.01,0.01,(data.accelEvent.x)),
		        Canvas3D.mRotateX(data.gyroEvent.roll),
		        Canvas3D.mRotateY(data.gyroEvent.yaw),
		        Canvas3D.mRotateZ(data.gyroEvent.pitch),
		    ];		    
		    p1.transforms=ico.transforms;
			p2.transforms=ico.transforms;
			p3.transforms=ico.transforms;

			// rate.transforms = [ 
			// 	Canvas3D.mScale(0.01,data.rrateMass.half ,1.0),
			// 	Canvas3D.mTranslate(-2.1,0,0)

		 //    ];

		};

	};


	//------------------------------------------------------------	
	// Accel Canvas
	//------------------------------------------------------------	
	createAccelCanvas = { |view, data|
		
	    var graph2, spot, accelRate, accelX, accelY, accelZ;

		graph2 = Canvas3D(view, Rect(0, 0, 250, 250))
		    .scale_(200)
			.background_(Color.black)
		    .perspective_(0.5)
		    .distance_(2);

		graph2.add(spot = Canvas3DItem.cube
		    .color_(Color.red(0.8))
		    .width_(1)

		);

		graph2.add(accelRate = Canvas3DItem.grid(2)
			.color_(Color.white(0.9))
		    .width_(1)
		);

		graph2.add(accelX = Canvas3DItem.regPrism()
			.color_(Color.yellow(0.9))
		    .width_(1)
		);
		graph2.add(accelY = Canvas3DItem.regPrism()
			.color_(Color.magenta(0.9))
		    .width_(1)
		);
		graph2.add(accelZ = Canvas3DItem.regPrism()
			.color_(Color.cyan(0.9))
		    .width_(1)
		);
		graph2.animate(renderRate) {|t|
			

			spot.transforms = [ 
				Canvas3D.mScale(0.8,0.8,0.8),
				Canvas3D.mScale(0.05,1,0.5),
			    Canvas3D.mRotateX(data.gyroEvent.roll),
		        Canvas3D.mRotateY(data.gyroEvent.yaw),
		        Canvas3D.mRotateZ(data.gyroEvent.pitch),
				 Canvas3D.mTranslate(data.accelEvent.y.neg.half,data.accelEvent.z.half,data.accelEvent.x.half)
		    ];

			accelRate.transforms = [ 
				Canvas3D.mScale(0.01,data.accelMass ,1.0),
				Canvas3D.mTranslate(-1.0,0,0)
		    ];

			accelY.transforms = [
				Canvas3D.mScale(0.01,(data.accelEvent.y),0.01),
		        Canvas3D.mRotateX(data.gyroEvent.roll),
		        Canvas3D.mRotateY(data.gyroEvent.yaw),
		        Canvas3D.mRotateZ(data.gyroEvent.pitch),
		    ];

			accelX.transforms = [ 
				Canvas3D.mScale((data.accelEvent.z),0.01,0.01),
		        Canvas3D.mRotateX(data.gyroEvent.roll),
		        Canvas3D.mRotateY(data.gyroEvent.yaw),
		        Canvas3D.mRotateZ(data.gyroEvent.pitch),
		    ];
			accelZ.transforms = [ 
				Canvas3D.mScale(0.01,0.01,(data.accelEvent.x)),
		        Canvas3D.mRotateX(data.gyroEvent.roll),
		        Canvas3D.mRotateY(data.gyroEvent.yaw),
		        Canvas3D.mRotateZ(data.gyroEvent.pitch),
		    ];


		};

	};

	//------------------------------------------------------------	
	// osc listneners
	//------------------------------------------------------------	
	addOSCDeviceListeners = {|d|

		var na = NetAddr.new(d.ip, d.port);

		// gryoListner = OSCFunc({ |msg, time, addr, recvPort|

		// }, '/gyrosc/gyro', na);

		// rotMatListner = OSCFunc({ |msg, time, addr, recvPort|
		// 	// gyroEvent.pitch = msg[1].asFloat;
		// 	// gyroEvent.roll = msg[2].asFloat;
		// 	// gyroEvent.yaw = msg[3].asFloat;
		// 	//rotMatEvent = msg[1..].clumps([4]);
		// 	//rotMatEvent.postln;
		// }, '/gyrosc/rotmat');


		rrateListener = OSCFunc({ |msg, time, addr, recvPort|
			//[msg, time, addr, recvPort].postln;

			if(devices.at(addr.port) != nil,{
				devices.at(addr.port).rrateEvent = (
					\x:msg[1].asFloat,
					\y:msg[2].asFloat,
					\z:msg[3].asFloat);
			});

		}, '/gyrosc/rrate', na);

		accelListener = OSCFunc({ |msg, time, addr, recvPort|

			if(devices.at(addr.port) != nil,{
				devices.at(addr.port).accelEvent = (
					\x:msg[1].asFloat,
					\y:msg[2].asFloat,
					\z:msg[3].asFloat);
			});
		}, '/gyrosc/accel', na);


		quatListener = OSCFunc({ |msg, time, addr, recvPort|

			var sx,sy,sz,qe,q,ss,r;
			var tr;

			if(devices.at(addr.port) != nil,{
				devices.at(addr.port).quatEvent = (
					\w:msg[1].asFloat,
					\x:msg[2].asFloat,
					\y:msg[3].asFloat,
					\z:msg[4].asFloat);

				// take quaternion and convert to ueler angles 
				qe = devices.at(addr.port).quatEvent;
				q = Quaternion.new(qe.w,qe.x,qe.y,qe.z);
				// ss = Quaternion.new(0.2,0.1,0.4,-0.3) + q;
				r = q.asEuler;
				tr = [r[0],r[1],r[2] + pi.half];

				//send something out to play with
				//oscOut.sendMsg("/bounce/quat",r[0],r[1],r[2]);

				// save converted data as Event
				devices.at(addr.port).gyroEvent = (
					\pitch:tr[2].asFloat,
					\roll:tr[0].asFloat,
					\yaw:tr[1].asFloat);
			});




		}, '/gyrosc/quat', na);
			
		ampListener = OSCFunc({ |msg, time, addr, recvPort|

				if(devices.at(addr.port) != nil,{
					devices.at(addr.port).ampValue = msg[1].asFloat;
				});
		}, '/gyrosc/amp', na);

	};
	
	startOSCListening = {

		buttonListener = OSCFunc({ |msg, time, addr, recvPort|
			[msg, time, addr, recvPort].postln;
			if(msg[1].asFloat == 1.0, {
				if(devices.at(addr.port) == nil,{
					{

						var d = addDevice.(addr.ip, addr.port);

						[d.port,recvPort].postln;
						addDeviceView.(contentView, d);

						addOSCDeviceListeners.(d);

					}.defer;
				},{
					//devices.removeAt(addr.port);
				});
			});

		}, '/gyrosc/button');

		voltListener = OSCFunc({ |msg, time, addr, recvPort|
			[msg, time, addr, recvPort].postln;

			{voltButton.states = [["volt"+msg[1].round(0.1).asString,Color.red]]}.defer(0);

		}, '/gyrosc/volt');
	};


	stopOSCListening = {
		buttonListener.free;
		voltListener.free;
		ampListener.free;
		gryoListner.free;
		// altListener.free;
		rrateListener.free;
		//rotMatListner.free;
		accelListener.free;
		quatListener.free;
	};

	// enableOSCListening = {
	// 	buttonListener.enable;
	// 	ampListener.enable;
	// 	gryoListner.enable;
	// 	// altListener.enable;
	// 	rrateListener.enable;
	// 	quatListener.enable;
	// 	//rotMatListner.enable;
	// 	//accelListener.enable;
	// };

	// disableOSCListening = {
	// 	buttonListener.disable;
	// 	ampListener.disable;
	// 	gryoListner.disable;
	// 	//altListener.disable;
	// 	rrateListener.disable;
	// 	//rotMatListner.disable;
	// 	accelListener.disable;
	// 	quatListener.disable;
	// };

	//------------------------------------------------------------	
	// 
	//------------------------------------------------------------	


	startup.();
	buildUI.();
	startOSCListening.();

)

