var listsDir = "~/Develop/SuperCollider/Projects/AirKit/lists/";//RPI
var personalityDir = "~/Develop/SuperCollider/Projects/AirKit/personalities/";//RPI
// var personalityDir = "~/Develop/SuperCollider/Projects/scbounce/personalities/"; //laptop
// var personalityDir = "~/Develop/SuperCollider/oscMusic/personalities/"; //mac mini cabin

var list = "list_cotf.sc";
// var list = "list_performanceTests.sc";
// var list = "list_ITR_Mel.sc";
// var list = "list_SiS_Alessio.sc";
// var list = "list_yourDNA26.sc";

// var list = "list_glenroy.sc";
var defaultLists = list!5;


// var defaultLists = [
//     "list_MIM25_James.sc",
//     "list_MIM25_Caz.sc",
//     "list_MIM25_Tim.sc",
//     "list_MIM25_Matt.sc",
//     "list_MIM25_Heather.sc",
// ];



// var defaultLists = [
//     "list_ITR_Mel.sc",
//     "list_ITR_Nic.sc",
//     "list_ITR_Brenton.sc",
//     "list_ITR_Alessio.sc",
//     "list_ITR_Mel.sc",
// ];

var com = (
    \root: 0,
    \dur: 1,
    \accelMass: 0,
    \rrateMass: 0,
);

//------------------------------------------------------------
// 
//------------------------------------------------------------
var loadPersonalityList = {|i|
    var path = PathName.new(listsDir++defaultLists[i]);
    var file = File.new(path.asAbsolutePath,"r");
    var str = file.readAllString;
    // var time = File.mtime(path.asAbsolutePath);
    // time.postln;
    interpret(str) // returns array of personality files
};

var interpretPersonality = {|d|

    var path = PathName.new(personalityDir++d.name++".sc");
    var file = File.new(path.asAbsolutePath,"r");
    var str = file.readAllString;

    // after adding personality to an Environment, add useful functions to be used by anyone
    var env = Environment.make {
        ~filePath = path.asAbsolutePath;
        ~fileTime = File.mtime(path.asAbsolutePath);
        // ~group = Group.new;
        ~model = (
            \com: com,
            \name: d.name,
            \ptn: Array.fill(16,{|i|i=90.rrand(65).asAscii.toLower}).join(),
            \rrateMass: 0,
            \rrateMassFiltered: 0,
            \rrateMassFilteredAttack: 0.9,
            \rrateMassFilteredDecay: 0.7,

            // \rrateMassThreshold: 0.21, //use for isMoving
            // \rrateMassThresholdSpec: ControlSpec(0.07, 0.4, \lin, 0.01, 0.21),

            \accelMass: 0,
            \accelMassFiltered: 0,
            \accelMassFilteredAttack: 0.9,
            \accelMassFilteredDecay: 0.7,

            // \accelMassAmpThreshold: 2.0,
            // \accelMassThresholdSpec: ControlSpec(0.4, 3.0, \lin, 0.1, 2.0),

            \gyroXFiltered: 0,
            \gyroYFiltered: 0,
            \gyroZFiltered: 0,
            \gyroFilteredAttack: 0.9,
            \gyroFilteredDecay: 0.7,


            // \isHit: false,
            // \isMoving: true,
            // \accelMassAmp: 0.0,

        );
        ~device = d;
        //------------------------------------------------------------
        // frame rate of rout
        ~secs = 0.03;

        //------------------------------------------------------------
        // Output routing convention (COTF-compatible, additive).
        // Personalities route audio via ~outBus (\out, ~outBus in Pbinds).
        // Standalone: 0 (hardware out, today's behaviour). Under the COTF
        // profile, topEnvironment holds ~cotfSeatBus (seat -> private Bus)
        // and each device's audio lands on its seat bus -> monitor -> final.
        ~outBus = topEnvironment.at(\cotfSeatBus) !? { |map|
            map.at(d.index) !? { |bus| bus.index } ? 0
        } ? 0;

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
            // normalise filtered gyro
            ~model.gyroXFiltered = ~smooth.(
                d.sensors.gyroEvent.x / pi,
                ~model.gyroXFiltered,
                ~model.gyroFilteredAttack,
                ~model.gyroFilteredDecay
            );
            ~model.gyroYFiltered = ~smooth.(
                d.sensors.gyroEvent.y / pi.half,
                ~model.gyroYFiltered,
                ~model.gyroFilteredAttack,
                ~model.gyroFilteredDecay
            );
            ~model.gyroZFiltered = ~smooth.(
                // (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5), // nice to have
                d.sensors.gyroEvent.z / pi,
                ~model.gyroZFiltered,
                ~model.gyroFilteredAttack,
                ~model.gyroFilteredDecay
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
        // Beat-aligned hooks dispatched from conductorController's score
        // Routine (see ~scheduleFromIndex). Personalities override any of
        // these by simple top-level assignment (~onBeat = { |ctx| ... }).
        // ctx is an Event with: idx, beatInBar, isDown, isTrueBeat, barIdx,
        // half, sectionId, phraseId, chord ("root roman"), key, mode, scale;
        // matching prev* fields for each change-triggered value;
        // voicePool + voiceAmp; p (score bar entry); m (meta bar entry).
        // Change-triggered hooks fire only when the tracked field differs
        // from its previous value.
        ~onTick    = { |ctx| };  // every beats.txt event (per subdivision)
        ~onHalf    = { |ctx| };  // when the half-bar index changes
        ~onBeat    = { |ctx| };  // every true musical beat
        ~onBar     = { |ctx| };  // every tracked downbeat (\D)
        ~onPhrase  = { |ctx| };  // when p["phrase_id"] changes
        ~onSection = { |ctx| };  // when p["section_id"] changes
        ~onChord   = { |ctx| };  // when chord_root + chord_roman changes
        ~onKey     = { |ctx| };  // when key + mode changes
        ~onScale   = { |ctx| };  // when p["active_scale"] changes
        ~onState   = { |old, new| };  // [COTF] /airkit/state change (see API.md)

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

var createProcRout = {|d|
    Routine {
        loop{
            d.env.use{
                if(~fileTime != File.mtime(~filePath),{
                    ~fileTime = File.mtime(~filePath);
                    "[file saved]".postln;
                    NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/loadPersonality", d.port, d.personality);
                }); 
                if(d.enabled == true,{
                    // pre-process data -> personality model
                    ~processDeviceData.(d);
                    //post process : make changes in personlity file
                    ~next.(d);
                });
                (~secs.()).yield;
            };
        };
    }
};
//------------------------------------------------------------
// s
//------------------------------------------------------------

var loadPersonality = { |port, index|

    var d = ~devices.at(port);
    var devicePersonalityList = loadPersonalityList.(d.index-1);

    // d.personality = index;
    // stop personality
    d.procRout.stop;
    d.procRout.free;

    if(d.env != nil,{ d.env.use{
        Routine{
            s.sync;
            ~deinit.();
            s.sync;
            // ~group.free;
            s.sync;
        }.play;
    }});

    // change device to new personality
    index = index.mod(devicePersonalityList.size-1);
    d.name = devicePersonalityList[index];
    "loading personality: %".format(d.name).postln;

    NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/personalityName", d.port, d.name);
    d.env = interpretPersonality.(d);
    // ["TODO",d.enabled].postln;
    d.env.use{
        Routine{
            s.sync;
            ~init.(d);
            s.sync;
        }.play;
    };

    d.procRout = createProcRout.(d);
    d.procRout.reset.play(AppClock);

    ~devices.put(d.port,d);

    d

};
//------------------------------------------------------------
// 
//------------------------------------------------------------

var unLoadPersonality = { |port, index|

    var d = ~devices.at(port);
    var devicePersonalityList = loadPersonalityList.(d.index-1);

    // d.personality = index;
    // stop personality
    d.procRout.stop;
    d.procRout.free;

    if(d.env != nil,{ d.env.use{
        Routine{
            s.sync;
            ~deinit.();
            s.sync;
            // ~group.free;
            // s.sync;
        }.play;
    }});
    d
};

//------------------------------------------------------------
// 
//------------------------------------------------------------

var reLoadPersonality = { |port, index|

    var d = ~devices.at(port);
    // var devicePersonalityList = loadPersonalityList.(d.index-1);

    // change device to new personality
    // index = index.mod(devicePersonalityList.size-1);
    // d.name = devicePersonalityList[index];
    // "loading personality: %".format(d.name).postln;

    // NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/personalityName", d.port, d.name);
    d.env = interpretPersonality.(d);
    // ["TODO",d.enabled].postln;
    d.env.use{
        Routine{
            s.sync;
            ~init.(d);
            s.sync;
        }.play;
    };

    d.procRout = createProcRout.(d);
    d.procRout.reset.play(AppClock);

    ~devices.put(d.port,d);

    d

};

//------------------------------------------------------------
// 
//------------------------------------------------------------
~devices = Dictionary();

OSCFunc({ |msg, time, addr, recvPort|
    loadPersonality.(msg[1], msg[2]);
},'/airkit/loadPersonality'); //* not having an NetAddr makes this public ie. external control

OSCFunc({ |msg, time, addr, recvPort|
    unLoadPersonality.(msg[1], msg[2]);
},'/airkit/unLoadPersonality', NetAddr("127.0.0.1", NetAddr.langPort));

OSCFunc({ |msg, time, addr, recvPort|
    reLoadPersonality.(msg[1], msg[2]);
},'/airkit/reLoadPersonality', NetAddr("127.0.0.1", NetAddr.langPort));

// OSCFunc({ |msg, time, addr, recvPort|
//     NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/personalityName", msg[1], ~devices.at(msg[1]).name);
// },'/airkit/getPersonalityName', NetAddr("127.0.0.1", NetAddr.langPort));
