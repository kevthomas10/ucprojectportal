function dialogYesNo(message, yesText, noText, yesCallback, noCallback) {
	var notificationBox = $(document.createElement("div"))
	.addClass("roundbox")
	.addClass("popane")
	.addClass("notification-popup")
	.css("backgroundColor", "white")
	.appendTo("body");

	var table = $(document.createElement("table")).appendTo(notificationBox);
	$(document.createElement("td"))
		.text(message)
		.attr("colspan", "2")
		.appendTo(
			$(document.createElement("tr"))
				.appendTo(table)
	)
	var optionTd = $(document.createElement("td"))
		.addClass("dialog-option-td")
		.appendTo(
		$(document.createElement("tr")).appendTo(table)
	);
	$(document.createElement("span"))
		.text(yesText)
		.addClass("button")
		.css("marginRight", "64px")
		.click(function() {
			yesCallback.call(this);
			notificationBox.popane({show : "false"});
		})
		.appendTo(optionTd)

	$(document.createElement("span"))
		.text(noText)
		.addClass("button")
		.click(function() {
			noCallback.call(this);
			notificationBox.popane({show : "false"});
		})
		.appendTo(optionTd)

	notificationBox.popane({show : "true"});
}

function setupLayout() {
	$(".textarea-description").autosize()

	$(".chosen").chosen({
			no_results_text: "oops, nothing found!",
			placeholder_text_multiple : "select options"
	})

	$(".edit-field").css("display", "none");

	$(".popane").popane();

	$(".dashboard-item").hover(function() {
		$(this).find(".dashboard-item-text").animate({
			"color" : "white"
		}, 300);
	}, function() { //Mouse leave
		$(this).find(".dashboard-item-text").animate({
			"color" : "black"
		}, 300)
	});

	$("#login-button").hover(function() {
		$(this).animate({
			"backgroundColor" : "#aedefc"
		}, 300)
	}, function() { //Mouse leave
		$(this).animate({
			"backgroundColor" : "#fa544f"
		}, 300)
	})
}