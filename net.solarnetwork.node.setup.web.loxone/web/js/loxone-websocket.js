function websocket() {
	var configId = $("meta[name='loxone-config-id']").attr("content");

  function formatUUID(uuid) {
    var segments = uuid.split('-');
    var s1 = segments[0].match(/.{1,2}/g);
    var s2 = segments[1].match(/.{1,2}/g);
    var s3 = segments[2].match(/.{1,2}/g);
    return `${s1[3] + s1[2] + s1[1] + s1[0]}-${s2[1] + s2[0]}-${s3[1] + s3[0]}-${segments[3]}`
  }

	function processValueEvents(list) {
		var container = $('#loxone-event-console'),
			group = $(`<section><h2>${new Date()}</h2></section>`);
		// console.log(`Got value events: ${JSON.stringify(list, null, 2)}`);
		list.forEach(function(ve) {
			// $('<div class="alert alert-info"/>').html(`<b>${ve.uuid}</b> = ${ve.value}`).appendTo(group);

      var valueUUID = formatUUID(ve.uuid);

      for(var c in controller.controls) {
        if(controller.controls[c].states != null) {
          for(var s in controller.controls[c].states) {
            if(controller.controls[c].states[s] == valueUUID) {
              controller.controls[c].value = parseFloat(ve.value.toFixed(2));
              var valueElement = document.getElementById(`value-${controller.controls[c].uuid}`);
              if(valueElement) {
                valueElement.innerHTML = parseFloat(ve.value.toFixed(2));
              }
            }
          }
        }
      }
		});
		container.prepend(group);
	}

	(function() {
		var csrf = SolarNode.csrfData;
		var url = 'ws://' +document.location.host +SolarNode.context.path('/ws');
		var socket = new WebSocket(url);
		var client = Stomp.over(socket);
		client.debug = null;
		var headers = {};
		headers[csrf.headerName] = csrf.token;
		client.connect(headers, function(frame) {

			function defaultHandleDataMessage(message, successHandler, errorHandler) {
				if ( message.body ) {
					var json = JSON.parse(message.body);
					if ( json.success ) {
						if ( typeof successHandler === 'function' ) {
							successHandler(json.data);
						}
					} else if ( typeof errorHandler === 'function' ) {
						errorHandler(json);
					}
				} else {
					console.log("got empty message");
				}
			}

			// subscribe to /app/X/events/values to get a complete list of all available values
			var allValueEventsSubscription = client.subscribe(`/app/${configId}/events/values`, function(message) {
				defaultHandleDataMessage(message, processValueEvents);
				// once we've downloaded all events, we can unsubscribe from this channel as we'll
				// pick up updates via the /topic/X/events/values subscription below
				allValueEventsSubscription.unsubscribe();
			});

			// subscribe to /topic/X/events/values to get notified of updated values
			var valueEventsUpdates = client.subscribe(`/topic/${configId}/events/values`, function(message) {
				defaultHandleDataMessage(message, processValueEvents);
			});

			// add a periodic call to /a/loxone/ping so the HTTP session stays alive;
			// TODO: this may be undersirable, as a logged in user will forever stay logged in
			setInterval(function() {
				$.getJSON(SolarNode.context.path('/a/loxone/ping'), function(json) {
					console.log('Ping result: %s', json.success);
				});
			},60000);

		}, function (error) {
	        console.log('STOMP protocol error %s', error);
		});
	})();
}
