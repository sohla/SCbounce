//------------------------------------------------------------
// machine.scd
//
// Per-machine settings. Every value that differs between one AirKit and
// the next lives in machines/<name>.scd, so no two kits ever edit the
// same file and a merge has nothing to reconcile. Branches stop being
// how machines differ.
//
// Selection, in order:
//   1. machines/CURRENT  - one line naming a machine file (gitignored)
//   2. hostname          - matched against each file's \hostnames
//   3. platform          - the single file claiming this \platform
//   4. nothing matched   - posted loudly, built-in defaults
//
// Step 3 is what keeps a fresh Pi quiet without anyone knowing its
// hostname in advance. It only holds while one file per platform exists;
// the second Linux kit makes it ambiguous, and ambiguous is loud.
//
// Paths are derived from this file's own location, so a clone anywhere
// works - nothing here knows about ~/Develop.
//
// Require caches by canonical path within one evaluation, so main.sc,
// oscController, personalityController and systemView all share the one
// resolved Event.
//
// Returns the config Event.
//------------------------------------------------------------

var root = thisProcess.nowExecutingPath.dirname.dirname;
var dir = root +/+ "machines";
var currentFile = dir +/+ "CURRENT";

var platform = thisProcess.platform.name;
var isOSX = platform == \osx;
var host = "hostname".unixCmdGetStdOut.stripWhiteSpace;
var shortHost = host.split($.)[0];

// The defaults are what an unrecognised machine boots with, so they are
// derived from the platform rather than fixed - a Pi that nothing claims
// still comes up borderless and fullscreen, as it did before this file.
var defaults = (
	label: "unknown",
	hostnames: [],
	platform: nil,

	outAddr: ["127.0.0.1", 5005],
	numDevices: 5,

	list: "list_dev.sc",

	secs: 0.01,
	latency: if(isOSX, 0.03, 0.1),

	border: isOSX,
	fullScreen: isOSX.not,

	listsDir: root +/+ "lists/",
	personalityDir: root +/+ "personalities/",
	versionPath: root +/+ "VERSION"
);

var readMachine = { |path|
	var func = thisProcess.interpreter.compileFile(path);
	var ev = func !? { func.value };
	if(ev.isKindOf(Event).not, {
		"[machine] %.scd did not return an Event - skipped"
			.format(PathName(path).fileNameWithoutExtension).warn;
		ev = nil;
	});
	ev
};

var loaded = (dir +/+ "*.scd").pathMatch
	.collect({ |p| (path: p, name: PathName(p).fileNameWithoutExtension, cfg: readMachine.(p)) })
	.select({ |e| e[\cfg].notNil });

var named = { |n| loaded.detect({ |e| e[\name] == n }) };

var wanted = if(File.exists(currentFile), {
	File.readAllString(currentFile).split($\n)
		.collect(_.stripWhiteSpace)
		.detect({ |line| line.notEmpty and: { line.beginsWith("//").not } })
}, { nil });

var hit = nil;
var cfg;

//------------------------------------------------------------

if(loaded.isEmpty, {
	"[machine] no machine files in % - using built-in defaults".format(dir).warn;
});

if(wanted.notNil, {
	hit = named.(wanted);
	if(hit.isNil, {
		"[machine] CURRENT names '%' but machines/%.scd was not found".format(wanted, wanted).warn;
	});
});

if(hit.isNil, {
	hit = loaded.detect({ |e|
		(e[\cfg][\hostnames] ? []).any({ |h| (h == host) or: { h == shortHost } })
	});
});

if(hit.isNil, {
	var samePlatform = loaded.select({ |e| e[\cfg][\platform] == platform });
	if(samePlatform.size == 1, {
		hit = samePlatform[0];
		"[machine] no hostname match for '%' - using the only % file, machines/%.scd"
			.format(host, platform, hit[\name]).postln;
	});
});

if(hit.isNil, {
	"".postln;
	"*** [machine] UNKNOWN MACHINE - running built-in defaults ***".postln;
	"    hostname : %".format(host).postln;
	"    platform : %".format(platform).postln;
	"    No machines/*.scd claims this host, machines/CURRENT is not set,".postln;
	"    and this platform is claimed by % files rather than exactly one."
		.format(loaded.count({ |e| e[\cfg][\platform] == platform })).postln;
	"    Fix: add '%' to \\hostnames in your machine file,".format(host).postln;
	"         or write that file's name into machines/CURRENT.".postln;
	"".postln;
});

cfg = defaults.copy;
if(hit.notNil, { cfg.putAll(hit[\cfg]) });

if(cfg[\list].isKindOf(String), { cfg[\list] = cfg[\list] ! cfg[\numDevices] });

"[machine] % : host % : % devices : out %:% : roster %".format(
	cfg[\label], host, cfg[\numDevices],
	cfg[\outAddr][0], cfg[\outAddr][1],
	cfg[\list].asSet.asArray.join(", ")
).postln;

cfg
