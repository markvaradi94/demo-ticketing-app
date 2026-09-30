package io.callisto.ticketing.shared;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.OverbookingException;
import io.callisto.ticketing.booking.PaymentDeclinedException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

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

	@ExceptionHandler(OverbookingException.class)
	public ProblemDetail handleOverbooking(OverbookingException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(PaymentDeclinedException.class)
	public ProblemDetail handlePaymentDeclined(PaymentDeclinedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, exception.getMessage());
	}

	// Both reach here only after @Retry has already exhausted its attempts —
	// CallNotPermittedException when the circuit breaker is open and refusing to
	// even try, ResourceAccessException when payment-service's read timeout was hit
	// on every attempt. Different causes, same honest answer to the client: the
	// dependency isn't available right now, try again later.
	@ExceptionHandler({CallNotPermittedException.class, ResourceAccessException.class})
	public ProblemDetail handlePaymentUnavailable(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Payment service is currently unavailable — try again shortly.");
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
