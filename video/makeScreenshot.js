var page = require("webpage").create();
var fs = require("fs");
var args = require("system").args;

var directoryRelative = args[1];
var start = parseInt(args[2]);
var end = parseInt(args[3]);
var incrementBy = parseInt(args[4])

page.viewportSize = { width: 1680, height: 970 };

var takeScreenshots = function(curr) {
	return function(status) {
		setTimeout(function() {
			var clipRect = page.evaluate(function(){
				return document.querySelector('#chart').getBoundingClientRect();
			});

			page.clipRect = {
				top:    clipRect.top,
				left:   clipRect.left,
				width:  clipRect.width,
				height: clipRect.height
			};

			console.log(page.renderBase64());
			
			var next = curr + incrementBy;
			
			if(next > end) {
				phantom.exit();
			}
			
			var nextPathRelative = directoryRelative + next + ".html";
			var nextPathAbsolute = "file:///" + fs.absolute(nextPathRelative);
			
			page.open(nextPathAbsolute, takeScreenshots(next));
			
		}, 5);
	}
}

var firstPathRelative = directoryRelative + start + ".html";
var firstPathAbsolute = "file:///" + fs.absolute(firstPathRelative);

page.open(firstPathAbsolute, takeScreenshots(start));