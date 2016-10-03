Loxone.websocket = (function() {

	function processValueEvents(list) {
		list.forEach(function(ve) {

      var viewModelID = `value-${ve.uuid}`
      var value = ve.value.toFixed(2);

      // Create/Update knockout view model value
      if(Loxone.controlView.viewModel[viewModelID] == null) {
        this.controlView.viewModel[viewModelID] = ko.observable(value);
      } else {
        Loxone.controlView.viewModel[viewModelID](value);
      }

		});
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
			var allValueEventsSubscription = client.subscribe(`/app/${Loxone.configID}/events/values`, function(message) {
				defaultHandleDataMessage(message, processValueEvents);
				// once we've downloaded all events, we can unsubscribe from this channel as we'll
				// pick up updates via the /topic/X/events/values subscription below
				allValueEventsSubscription.unsubscribe();
			});

			// subscribe to /topic/X/events/values to get notified of updated values
			var valueEventsUpdates = client.subscribe(`/topic/${Loxone.configID}/events/values`, function(message) {
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
})();
