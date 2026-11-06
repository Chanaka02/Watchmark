package app.watchmark.calendar;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.WatchmarkUserDetails;
import app.watchmark.calendar.CalendarDtos.Calendar;

/** The signed-in user's calendar (R1: the user always comes from the session). */
@RestController
@RequestMapping("/api/v1/me/calendar")
class CalendarController {

	private final CalendarService calendar;

	CalendarController(CalendarService calendar) {
		this.calendar = calendar;
	}

	@GetMapping
	Calendar get(@AuthenticationPrincipal WatchmarkUserDetails principal, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		return calendar.get(principal.id(), parse(from), parse(to));
	}

	/** Parsed here so a malformed date is an {@code invalid-range} problem like any other bad range. */
	private static LocalDate parse(String date) {
		if (date == null || date.isEmpty()) {
			return null;
		}
		try {
			return LocalDate.parse(date);
		}
		catch (DateTimeParseException ex) {
			throw CalendarService.invalidRange();
		}
	}

}
