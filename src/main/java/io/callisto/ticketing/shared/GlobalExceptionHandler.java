package io.callisto.ticketing.shared;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.catalog.EventNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler({EventNotFoundException.class, BookingNotFoundException.class})
	public ProblemDetail handleNotFound(RuntimeException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(TooManySeatsRequestedException.class)
	public ProblemDetail handleTooManySeats(TooManySeatsRequestedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(BookingAlreadyCancelledException.class)
	public ProblemDetail handleAlreadyCancelled(BookingAlreadyCancelledException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This record was updated by someone else — reload and try again.");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request failed validation");
		List<String> errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.toList();
		problem.setProperty("errors", errors);
		return problem;
	}

}
