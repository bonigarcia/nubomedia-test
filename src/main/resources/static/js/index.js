/*
 * (C) Copyright 2016 NUBOMEDIA (http://www.nubomedia.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

var ws = new WebSocket('wss://' + location.host + '/app');
var videoInput;
var videoOutput;
var webRtcPeer1;
var webRtcPeer2;
var state = null;

var I_CAN_START = 0;
var I_CAN_STOP = 1;
var I_AM_STARTING = 2;

window.onload = function() {
	console = new Console();
	console.log("Page loaded ...");
	videoInput = document.getElementById('videoInput');
	videoOutput = document.getElementById('videoOutput');
	setState(I_CAN_START);
}

window.onbeforeunload = function() {
	ws.close();
}

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'presenterResponse':
		presenterResponse(parsedMessage);
		break;
	case 'viewerResponse':
		viewerResponse(parsedMessage);
		break;
	case 'error':
		if (state == I_AM_STARTING) {
			setState(I_CAN_START);
		}
		onError("Error message from server: " + parsedMessage.message);
		stop(false);
		break;
	case 'iceCandidate1':
		webRtcPeer1.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error) {
				console.error("Error adding candidate: " + error);
				return;
			}
		});
		break;
	case 'iceCandidate2':
		webRtcPeer2.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error) {
				console.error("Error adding candidate: " + error);
				return;
			}
		});
		break;
	case 'notEnoughResources':
		stop(false);
		$('#resourcesDialog').modal('show');
		break;
	default:
		if (state == I_AM_STARTING) {
			setState(I_CAN_START);
		}
		onError('Unrecognized message', parsedMessage);
	}
}

function start() {
	console.log("Starting video call ...")
	// Disable start button
	setState(I_AM_STARTING);
	showSpinner(videoInput, videoOutput);

	console.log("Creating WebRtcPeer and generating local sdp offer ...");

	var options1 = {
		localVideo : videoInput,
		onicecandidate : onIceCandidate1
	}
	webRtcPeer1 = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options1,
			function(error) {
				if (error) {
					return console.error(error);
				}
				webRtcPeer1.generateOffer(onOffer1);
			});
}

function onOffer1(error, offerSdp) {
	if (error) {
		return console.error("Error generating the offer " + error);
	}
	var loadPoints = document.getElementById('loadPoints').value;
	var message = {
		id : 'startPresenter',
		sdpOffer : offerSdp,
		loadPoints : loadPoints
	}
	sendMessage(message);
}

function onOffer2(error, offerSdp) {
	if (error) {
		return console.error("Error generating the offer " + error);
	}
	var loadPoints = document.getElementById('loadPoints').value;
	var message = {
		id : 'startViewer',
		sdpOffer : offerSdp,
		loadPoints : loadPoints
	}
	sendMessage(message);
}

function onError(error) {
	console.error(error);
}

function onIceCandidate1(candidate) {
	var message = {
		id : 'onIceCandidate1',
		candidate : candidate
	};
	sendMessage(message);
}

function onIceCandidate2(candidate) {
	var message = {
		id : 'onIceCandidate2',
		candidate : candidate
	};
	sendMessage(message);
}

function presenterResponse(message) {
	webRtcPeer1.processAnswer(message.sdpAnswer, function(error) {
		if (error)
			return console.error(error);
	});

	var options2 = {
		remoteVideo : videoOutput,
		onicecandidate : onIceCandidate2
	}
	webRtcPeer2 = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options2,
			function(error) {
				if (error) {
					return console.error(error);
				}
				webRtcPeer2.generateOffer(onOffer2);
			});
}

function viewerResponse(message) {
	setState(I_CAN_STOP);

	webRtcPeer2.processAnswer(message.sdpAnswer, function(error) {
		if (error)
			return console.error(error);
	});
}

function stop(stopMessage) {
	console.log("Stopping video call ...");
	setState(I_CAN_START);
	if (webRtcPeer1) {
		webRtcPeer1.dispose();
		webRtcPeer1 = null;

		if (stopMessage == undefined || stopMessage) {
			var message = {
				id : 'stop'
			}
			sendMessage(message);
		}
	}
	if (webRtcPeer2) {
		webRtcPeer2.dispose();
		webRtcPeer2 = null;
	}

	hideSpinner(videoInput, videoOutput);
}

function setState(nextState) {
	switch (nextState) {
	case I_CAN_START:
		$('#start').attr('disabled', false);
		$("#start").attr('onclick', 'start()');
		$('#stop').attr('disabled', true);
		$("#stop").removeAttr('onclick');
		break;

	case I_CAN_STOP:
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		$("#stop").attr('onclick', 'stop()');
		break;

	case I_AM_STARTING:
		$('#start').attr('disabled', true);
		$("#start").removeAttr('onclick');
		$('#stop').attr('disabled', true);
		$("#stop").removeAttr('onclick');
		break;

	default:
		onError("Unknown state " + nextState);
		return;
	}
	state = nextState;
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Senging message: ' + jsonMessage);
	ws.send(jsonMessage);
}

function showSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent.png';
		arguments[i].style.background = "center transparent url('./img/spinner.gif') no-repeat";
	}
}

function hideSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].src = '';
		arguments[i].poster = './img/webrtc.png';
		arguments[i].style.background = '';
	}
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});
