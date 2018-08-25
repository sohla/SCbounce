		(

			var devicesDir = "~/Develop/SuperCollider/Projects/SCbounce/personalities/";
			var dataPath = "~/Develop/SuperCollider/Projects/SCbounce/data/";

			var loadDeviceList;

			var names;// = ["adam","george","harry","susan","eve"];
			//var faces = ["🦆","🦊","🐴","🐯","🍎"];


		    var width = Window.screenBounds.width, height = Window.screenBounds.height ;
			var startup, shutdown, buildUI;

			var contentView = View().background_(Color.grey(0.2));


			var reloadButton;
			var stackButton;

			var createPlotterGroup, createGyroCanvas, createAccelCanvas, createTransportView;
			var createWindowView, addDeviceView;
			var startOSCListening, stopOSCListening, enableOSCListening, disableOSCListening;

			var addDevice, removeDevice;

		    var gryoListner, rotMatListner, rrateListener, accelListener, quatListener, altListener, ampListener;
		    var buttonListener;

		    var oscOut = NetAddr.new("127.0.0.1", 9003); 

			var devices = Dictionary();

			var dataRate = 1; 
			var renderRate = 20;

			var loadPersonality;
			var reloadDevice;
			var createRout;
			

			var dataSizes = [100,200,300,400];
			var dataSize = dataSizes[0];

			var midiOut;


		    // data
			var deviceProto = (
				\name: "adam",
				\ip: "127.0.0.1",
				\port: 0101,
				\gyroEvent: (\pitch:0,\roll:0,\yaw:0),
				\gyroMass: 0,
				\rrateEvent: (\x:0,\y:0,\z:0),
				\rrateMass: 0,
				\accelEvent: (\x:0,\y:0,\z:0),
				\accelMass: 1,
				\quatEvent: (\w:0,\x:0,\y:0,\z:0),
				\ampValue: 0,
				\env: nil,	// Environment for injected code
				\rout: nil,	// Routine calls ~next every ~fps

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
		midiOut = MIDIOut(0).latency_(Server.default.latency);

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

				interpret(str);

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
		createRout = {|d|

			Routine { 
				var i=0;
				loop{ 
					d.env.use{
						~next.(d);
						(~secs.()).yield;
						i=i+1;
					};
				}
			};
		};
		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	
		
		startup = {

			names = loadDeviceList.();
		};

		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	
		shutdown = {

			devices.keysValuesDo({|k,v|
				v.rout.stop();
				v.env.use{ ~deinit.() };
			});


			stopOSCListening.();
			s.queryAllNodes;

			Server.freeAll;

		};
		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	

		addDevice = { |ip|

			var d = Event.new(proto:deviceProto);
			
			d.ip = ip;


			devices.put(ip,d);
			
			// load the data
			reloadDevice.(d);

			d // return the device (data)
		};

		reloadDevice = { |d|


			// stop personality

			d.rout.stop;
			if(d.env != nil,{ d.env.use{ ~deinit.() }});


			// start new personality
			d.env = loadPersonality.(d.name);
			d.env.use{ ~init.(midiOut) };
			d.rout = createRout.(d);
			d.rout.reset.play(AppClock);

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

			window = Window("bounce", Rect(0, height, width, height-150), false).front;
			window.view.keyDownAction_({|view,char,mods,uni,code,key|
				if(uni==114,{//r
					devices.keysValuesDo({|k,v|
						reloadDevice.(v);
					});
				});
				if(uni==97,{//a
					stackButton.valueAction_((stackButton.value+1).mod(3));
				});
				if(uni==100,{
					//•disconnect
				});
				//uni.postln;
			});

			window.onClose = {
				shutdown.();
			};
			CmdPeriod.doOnce({window.close});
			
			createWindowView.(window);

		};

		createWindowView = {|view|

			var scroll = ScrollView(view,Rect(0,30,width,height - 150 )).background_(Color.yellow);
			
			StaticText(view)
					.stringColor_(Color.yellow)
					.font_(Font(size:14))
					.minHeight_(30)
					.minWidth_(200)
					.string_(" :: bounce");

			contentView.layout_(VLayout());
			scroll.canvas = contentView;

			// set this for quick connection to device
			//addDeviceView.(contentView, addDevice.("127.0.0.1"));
			//addDeviceView.(contentView, addDevice.("192.168.0.101"));

		};

		addDeviceView = { |view, d|

			var header;
			var va,vb,vc;
			var stackView, stackLayout;
			var dataSizeMenu;
			var popup;

			header = View(view).layout_( HLayout(
				
				stackButton = Button(view)
					.minWidth_(160)
					.states_([
						["⚾︎ attitude"],	
						["⊙ accelerometer"],
						["⊚ rotation rate"]
					])
					.action_({|b|
						stackLayout.index = b.value;
					}),
				Button()
					.maxWidth_(40)
					.states_([["📡"]])
					.action_({|b|
							d.ip.class.postln;
							d.port.class.postln;
						b = NetAddr.new(d.ip, d.port.asInt+1);    // create the NetAddr
						b.sendMsg("/bounce", "motionReset");    // send the application the messa

					}),
				StaticText(view)
					.stringColor_(Color.white)
					.font_(Font(size:14))
					.minWidth_(200)
					.string_("ip ["+d.ip+"] port ["+d.port+"]"),

				reloadButton = Button(view)
					.maxWidth_(80)
					.states_([
						["💾 reload"],	
					])
					.action_({|b|
						reloadDevice.(d);
						
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


				dataSizeMenu = PopUpMenu(view)
						.maxWidth_(60)
						.items_(dataSizes)
						.valueAction_(0)
						.action_({|b|
							dataSize = dataSizes.at(b.value);
						}),


				Button(view)
					.maxWidth_(80)
					.states_([
						["✖️ disconect",Color.red(0.5)],	

					])
					.action_({|b|
						header.remove();
						stackView.remove();
						
						// stop personality
						d.env.use{ ~deinit.() };
						d.rout.stop;

						devices.removeAt(d.ip);

					})

			).setAlignment(0,\left).setAlignment(3,\left));

			view.layout.add(stackView = View().background_(Color.yellow).minHeight_(250).layout_( stackLayout = StackLayout(
					va = View().background_(Color.cyan(0.2)),
					vb = View().background_(Color.blue(0.2)),
					vc = View().background_(Color.magenta(0.2))
					)
				);
			);	

			createPlotterGroup.(vc, Rect(750,0,500,250), [
				// ["a",[Color.red,Color.green,Color.blue],{|p| [d.gyroEvent.pitch,d.gyroEvent.roll,d.gyroEvent.yaw]}]

				["pitch",Color.red,{|p| d.gyroEvent.pitch }],
				["roll",Color.green,{|p| d.gyroEvent.roll }],
				["yaw",Color.blue,{|p| d.gyroEvent.yaw }]
			],-pi,pi);

			createPlotterGroup.(vb, Rect(750,0,500,250), [
				["aaccelX",Color.cyan,{|p| d.accelEvent.x }],
				["accelY",Color.magenta,{|p| d.accelEvent.y }],
				["accelZ",Color.yellow,{|p| d.accelEvent.z }]
			],-2,2);

			createPlotterGroup.(va, Rect(750,0,500,250), [
				["rrateX",Color.cyan,{|p| d.rrateEvent.x }],
				["rrateY",Color.magenta,{|p| d.rrateEvent.y }],
				["rrateZ",Color.yellow,{|p| d.rrateEvent.z }]
			],-6,6);

			createPlotterGroup.(va, Rect(150,0,600,250), [
				["a",[Color.yellow,Color.magenta,Color.cyan],{|p| d.env.use{ ~plot.(d)} }]
			],-1,1);

			createGyroCanvas.(va,d);
			createAccelCanvas.(vb,d);

			contentView.layout.add(nil);
		};

		//------------------------------------------------------------	
		// Plotters
		//------------------------------------------------------------	

		createPlotterGroup = {|view, bounds, plotters, pmin=(-pi), pmax=pi|


			var plotterView = UserView(view,bounds).background_(Color.black).animate_(true);
			var size = plotters.size;
			var pw = bounds.width;
			var ph = bounds.height / size;

			 plotterView.drawFunc_({});

			 plotters.do{ |specs,i|


				var max = 0;
				var plotter = Plotter(specs[0], Rect(pw*0.3,ph*i,pw*0.7,ph),plotterView)
					.value_((0..dataSize).dup(plotters.size)) //need to init arrays with data
					.refresh;


				var st = StaticText(plotterView,Rect(pw*0.15,ph*i,pw*0.15,ph/2))
					.string_(specs[0])
					.align_(\center)
					.stringColor_(specs[1])
					.background_(Color.black);

				var su = StaticText(plotterView,Rect(pw*0.15,(ph*i)+(ph/2),pw*0.15,ph/2))
					.string_(max)
					.align_(\center)
					.stringColor_(specs[1])
					.background_(Color.black)
					.mouseUpAction_({max = 0});

				// StaticText(plotterView,Rect(0,ph*i,pw*0.15,ph/2))
				// 	.string_(specs[0])
				// 	.align_(\center)
				// 	.stringColor_(specs[1])
				// 	.background_(Color.black);


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

			graph1 = Canvas3D(view, Rect(0, 0, 250, 250))
			    .scale_(200)
				.background_(Color.black)
			    .perspective_(0.5)
			    .distance_(3.5);


			// graph1.view.drawFunc= graph1.view.drawFunc<>{
			// 	("[data"+"}-->".scramble).draw
			// };
			graph1.add(cube = Canvas3DItem.cube
			    .color_(Color.red(0.8))
			    .width_(1)
			);

			graph1.add(top = Canvas3DItem.grid(4)
				.color_(Color.green(0.9))
			    .width_(1)
			    // need to rotate for grids orientation
			    .transform(Canvas3D.mRotateY(pi/2))
			);

			graph1.add(rate = Canvas3DItem.grid(2)
				.color_(Color.white(0.9))
			    .width_(1)
			);

			graph1.add(loc = Canvas3DItem.regPrism(3)
				.color_(Color.blue(0.9))
			    .width_(1)
			);


		     graph1.add(p1 = Canvas3DItem.grid(2)
		        .color_(Color.green)
				.fill_(false)
		        .width_(1)
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

			// animate
			graph1.animate(renderRate) {|t|

				var tr = [data.gyroEvent.roll,data.gyroEvent.yaw,data.gyroEvent.pitch];
				//data.ip.postln;
		        
		        loc.transforms = [
		        	Canvas3D.mScale(1,0.02,0.02),
		        	Canvas3D.mTranslate(1.5,0,0),
			        Canvas3D.mRotateX(tr[0]),
			        Canvas3D.mRotateY(tr[1]),
			        Canvas3D.mRotateZ(tr[2]),
		        ];

				cube.transforms = [ 
					Canvas3D.mScale(0.05,1,0.5),
					Canvas3D.mTranslate(0,0,0),
			        Canvas3D.mRotateX(tr[0]),
			        Canvas3D.mRotateY(tr[1]),
			        Canvas3D.mRotateZ(tr[2]),
			    ];

				top.transforms = [ 
					Canvas3D.mScale(0.05,1,0.5),
					Canvas3D.mTranslate(1,0,0),
			        Canvas3D.mRotateX(tr[0]),
			        Canvas3D.mRotateY(tr[1]),
			        Canvas3D.mRotateZ(tr[2]),
			    ];

			    ico.transforms = [ 
					Canvas3D.mScale(0.6,0.6,0.6),
			        Canvas3D.mRotateX(tr[0]),
			        Canvas3D.mRotateY(tr[1]),
			        Canvas3D.mRotateZ(tr[2]),
			    ];
			    
			    p1.transforms=ico.transforms;
				p2.transforms=ico.transforms;
				p3.transforms=ico.transforms;

				rate.transforms = [ 
					Canvas3D.mScale(0.01,data.rrateMass.half ,1.0),
					Canvas3D.mTranslate(-2.1,0,0)

			    ];

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

		startOSCListening = {

			buttonListener = OSCFunc({ |msg, time, addr, recvPort|
				if(msg[1].asFloat == 1.0, {
					if(devices.at(addr.ip) == nil,{
						{
							var d = addDevice.(addr.ip);
							d.port = addr.port;
							addDeviceView.(contentView, d)
						}.defer;
					},{
						devices.removeAt(addr.ip);
					});
				});

			}, '/gyrosc/button');

			gryoListner = OSCFunc({ |msg, time, addr, recvPort|

				// don't store as quaternions will supply us with data

				// if(devices.at(addr.ip) != nil,{
				// 	devices.at(addr.ip).gyroEvent = (
				// 		\pitch:msg[1].asFloat,
				// 		\roll:msg[2].asFloat,
				// 		\yaw:msg[3].asFloat);
				// });
			
			}, '/gyrosc/gyro');


			// rotMatListner = OSCFunc({ |msg, time, addr, recvPort|
			// 	// gyroEvent.pitch = msg[1].asFloat;
			// 	// gyroEvent.roll = msg[2].asFloat;
			// 	// gyroEvent.yaw = msg[3].asFloat;
			// 	//rotMatEvent = msg[1..].clumps([4]);
			// 	//rotMatEvent.postln;
			// }, '/gyrosc/rotmat');


			rrateListener = OSCFunc({ |msg, time, addr, recvPort|
				//[msg, time, addr, recvPort].postln;

				if(devices.at(addr.ip) != nil,{
					devices.at(addr.ip).rrateEvent = (
						\x:msg[1].asFloat,
						\y:msg[2].asFloat,
						\z:msg[3].asFloat);
				});

			}, '/gyrosc/rrate');

			accelListener = OSCFunc({ |msg, time, addr, recvPort|

				if(devices.at(addr.ip) != nil,{
					devices.at(addr.ip).accelEvent = (
						\x:msg[1].asFloat,
						\y:msg[2].asFloat,
						\z:msg[3].asFloat);
				});
			}, '/gyrosc/accel');


			quatListener = OSCFunc({ |msg, time, addr, recvPort|

				var sx,sy,sz,qe,q,ss,r;
				var tr;

				if(devices.at(addr.ip) != nil,{

					devices.at(addr.ip).quatEvent = (
						\w:msg[1].asFloat,
						\x:msg[2].asFloat,
						\y:msg[3].asFloat,
						\z:msg[4].asFloat);

					// take quaternion and convert to ueler angles 
					qe = devices.at(addr.ip).quatEvent;
					q = Quaternion.new(qe.w,qe.x,qe.y,qe.z);
					// ss = Quaternion.new(0.2,0.1,0.4,-0.3) + q;
					r = q.asEuler;
					tr = [r[0],r[1],r[2] + pi.half];

					//send something out to play with
					//oscOut.sendMsg("/bounce/quat",r[0],r[1],r[2]);

					// save converted data as Event
					devices.at(addr.ip).gyroEvent = (
						\pitch:tr[2].asFloat,
						\roll:tr[0].asFloat,
						\yaw:tr[1].asFloat);
				});




			}, '/gyrosc/quat');
				
			ampListener = OSCFunc({ |msg, time, addr, recvPort|
					if(devices.at(addr.ip) != nil,{
						devices.at(addr.ip).ampValue = msg[1].asFloat;
					});
			}, '/gyrosc/amp');

		};

		stopOSCListening = {
			buttonListener.free;
			ampListener.free;
			gryoListner.free;
			// altListener.free;
			rrateListener.free;
			//rotMatListner.free;
			accelListener.free;
			quatListener.free;
		};

		enableOSCListening = {
			buttonListener.enable;
			ampListener.enable;
			gryoListner.enable;
			// altListener.enable;
			rrateListener.enable;
			quatListener.enable;
			//rotMatListner.enable;
			//accelListener.enable;
		};

		disableOSCListening = {
			buttonListener.disable;
			ampListener.disable;
			gryoListner.disable;
			//altListener.disable;
			rrateListener.disable;
			//rotMatListner.disable;
			accelListener.disable;
			quatListener.disable;
		};


		//------------------------------------------------------------	
		// 
		//------------------------------------------------------------	


		startup.();
		buildUI.();
		startOSCListening.();


	)

