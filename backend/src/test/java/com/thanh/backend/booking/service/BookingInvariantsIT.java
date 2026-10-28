package com.thanh.backend.booking.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.aircraft.entity.Aircraft;
import com.thanh.backend.aircraft.repository.AircraftRepository;
import com.thanh.backend.airline.entity.Airline;
import com.thanh.backend.airline.repository.AirlineRepository;
import com.thanh.backend.airport.entity.Airport;
import com.thanh.backend.airport.repository.AirportRepository;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.enums.UserRole;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.ContactInfoRequest;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.request.PassengerRequest;
import com.thanh.backend.booking.dto.request.SeatSelectionRequest;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.entity.BookingLeg;
import com.thanh.backend.booking.entity.Passenger;
import com.thanh.backend.booking.entity.PassengerDocument;
import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.DocumentType;
import com.thanh.backend.booking.enums.Gender;
import com.thanh.backend.booking.enums.LegDirection;
import com.thanh.backend.booking.enums.PassengerType;
import com.thanh.backend.booking.enums.TicketStatus;
import com.thanh.backend.booking.enums.TripType;
import com.thanh.backend.booking.repository.BookingLegRepository;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.booking.repository.PassengerDocumentRepository;
import com.thanh.backend.booking.repository.PassengerRepository;
import com.thanh.backend.booking.repository.TicketRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.entity.Flight;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.enums.CabinClass;
import com.thanh.backend.flight.enums.FlightSource;
import com.thanh.backend.flight.enums.FlightStatus;
import com.thanh.backend.flight.enums.SeatStatus;
import com.thanh.backend.flight.repository.FlightRepository;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingInvariantsIT extends AbstractIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private FlightSeatRepository flightSeatRepository;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private AirlineRepository airlineRepository;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private PassengerDocumentRepository passengerDocumentRepository;

    @Autowired
    private BookingLegRepository bookingLegRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @MockitoSpyBean
    private BookingRepository bookingRepository;

    private Flight flight1;
    private Flight flight2;
    private Airline airline;

    @BeforeEach
    void setUpTestData() {
        Airport origin = airportRepository.findByIataCode("SGN").orElseGet(() -> airportRepository.save(Airport.builder()
                .iataCode("SGN")
                .name("Tan Son Nhat")
                .city("Ho Chi Minh")
                .country("Vietnam")
                .timezone("Asia/Ho_Chi_Minh")
                .build()));

        Airport dest = airportRepository.findByIataCode("HAN").orElseGet(() -> airportRepository.save(Airport.builder()
                .iataCode("HAN")
                .name("Noi Bai")
                .city("Ha Noi")
                .country("Vietnam")
                .timezone("Asia/Ho_Chi_Minh")
                .build()));

        airline = airlineRepository.findByCode("VN").orElseGet(() -> airlineRepository.save(Airline.builder()
                .code("VN")
                .name("Vietnam Airlines")
                .build()));

        String regNo = "VN-A" + String.format("%04d", Math.abs((int) (System.nanoTime() % 10000)));
        Aircraft aircraft = aircraftRepository.save(Aircraft.builder()
                .airline(airline)
                .model("Airbus A321")
                .registrationNo(regNo)
                .totalSeats(180)
                .economySeats(160)
                .businessSeats(20)
                .build());

        flight1 = flightRepository.save(Flight.builder()
                .flightNo("VN101")
                .airline(airline)
                .aircraft(aircraft)
                .origin(origin)
                .dest(dest)
                .departureTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2))
                .arrivalTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).plusHours(2))
                .status(FlightStatus.SCHEDULED)
                .basePrice(new BigDecimal("1500000.00"))
                .source(FlightSource.MANUAL)
                .build());

        flight2 = flightRepository.save(Flight.builder()
                .flightNo("VN102")
                .airline(airline)
                .aircraft(aircraft)
                .origin(origin)
                .dest(dest)
                .departureTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3))
                .arrivalTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).plusHours(2))
                .status(FlightStatus.SCHEDULED)
                .basePrice(new BigDecimal("1600000.00"))
                .source(FlightSource.MANUAL)
                .build());
    }

    private User createTestUser(String emailPrefix) {
        return userRepository.save(User.builder()
                .email(emailPrefix + "_" + System.nanoTime() + "@test.com")
                .passwordHash("hashed_pwd")
                .fullName("User " + emailPrefix)
                .phone("09" + String.format("%08d", Math.abs((int) (System.nanoTime() % 100000000))))
                .role(UserRole.ROLE_USER)
                .build());
    }

    private FlightSeat createTestSeat(Flight flight, String seatNo) {
        return flightSeatRepository.save(FlightSeat.builder()
                .flight(flight)
                .seatNo(seatNo)
                .cabinClass(CabinClass.ECONOMY)
                .status(SeatStatus.AVAILABLE)
                .price(new BigDecimal("1500000.00"))
                .build());
    }

    private ContactInfoRequest createDefaultContactInfo() {
        return ContactInfoRequest.builder()
                .fullName("Nguyen Van A")
                .email("nguyenvana@test.com")
                .phone("0901234567")
                .build();
    }

    // =========================================================================
    // 1. Passenger Document Uniqueness
    // =========================================================================
    @Nested
    @DisplayName("Group 1.3.1: Passenger Document Uniqueness")
    class PassengerDocumentUniquenessTests {

        @Test
        @DisplayName("Duplicate documents in same booking are rejected and rolled back")
        void createBooking_DuplicatePassengerDocuments_ThrowsInvalidPassengerDocumentAndRollsBack() {
            User user = createTestUser("doc_dup_user");
            FlightSeat seat1 = createTestSeat(flight1, "01A");
            FlightSeat seat2 = createTestSeat(flight1, "01B");

            bookingService.holdSeats(user, HoldSeatsRequest.builder()
                    .seatIds(List.of(seat1.getId(), seat2.getId()))
                    .build());

            // 2 Adults with the exact same CITIZEN_ID document number
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Passenger One")
                                    .dateOfBirth(LocalDate.of(1990, 1, 1))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber("079199001122")
                                    .identityExpiry(LocalDate.of(2035, 1, 1))
                                    .build(),
                            PassengerRequest.builder()
                                    .fullName("Passenger Two")
                                    .dateOfBirth(LocalDate.of(1992, 2, 2))
                                    .gender(Gender.FEMALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber("079199001122") // Duplicate!
                                    .identityExpiry(LocalDate.of(2035, 1, 1))
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat1.getId()).passengerIndex(0).build(),
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat2.getId()).passengerIndex(1).build()
                    ))
                    .build();

            AppException ex = assertThrows(AppException.class, () -> bookingService.createBooking(user, request));
            assertEquals(ErrorCode.INVALID_PASSENGER_DOCUMENT, ex.getErrorCode());

            // Verify clean rollback via raw JDBC
            Integer bookingCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM booking WHERE user_id = ?", Integer.class, user.getId());
            assertEquals(0, bookingCount, "Transaction must rollback all booking records");

            Integer ticketCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM tickets WHERE flight_seat_id IN (?, ?)", Integer.class, seat1.getId(), seat2.getId());
            assertEquals(0, ticketCount, "Transaction must rollback all ticket records");
        }

        @Test
        @DisplayName("Distinct documents in same booking succeed")
        void createBooking_DistinctPassengerDocuments_Succeeds() {
            User user = createTestUser("doc_ok_user");
            FlightSeat seat1 = createTestSeat(flight1, "02A");
            FlightSeat seat2 = createTestSeat(flight1, "02B");

            bookingService.holdSeats(user, HoldSeatsRequest.builder()
                    .seatIds(List.of(seat1.getId(), seat2.getId()))
                    .build());

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Passenger One")
                                    .dateOfBirth(LocalDate.of(1990, 1, 1))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber("079199001122")
                                    .identityExpiry(LocalDate.of(2035, 1, 1))
                                    .build(),
                            PassengerRequest.builder()
                                    .fullName("Passenger Two")
                                    .dateOfBirth(LocalDate.of(1992, 2, 2))
                                    .gender(Gender.FEMALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber("079199001123") // Distinct!
                                    .identityExpiry(LocalDate.of(2035, 1, 1))
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat1.getId()).passengerIndex(0).build(),
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat2.getId()).passengerIndex(1).build()
                    ))
                    .build();

            BookingResponse response = bookingService.createBooking(user, request);
            assertNotNull(response);
            assertEquals(BookingStatus.PENDING, response.getStatus());

            Integer bookingCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM booking WHERE user_id = ?", Integer.class, user.getId());
            assertEquals(1, bookingCount);
        }
    }

    // =========================================================================
    // 2. Infant Guardian Rules
    // =========================================================================
    @Nested
    @DisplayName("Group 1.3.2: Infant Guardian Rules")
    class InfantGuardianRulesTests {

        @Test
        @DisplayName("Scenario 2A: 1 Adult cannot guardian 2 Infants (Ratio violation)")
        void createBooking_OneAdultTwoInfants_ThrowsInvalidInfantGuardian() {
            User user = createTestUser("infant_ratio_user");
            FlightSeat seat = createTestSeat(flight1, "03A");

            bookingService.holdSeats(user, HoldSeatsRequest.builder().seatIds(List.of(seat.getId())).build());

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Adult Guardian")
                                    .dateOfBirth(LocalDate.of(1990, 1, 1))
                                    .gender(Gender.FEMALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber("079190001122")
                                    .identityExpiry(LocalDate.of(2035, 1, 1))
                                    .build(),
                            PassengerRequest.builder()
                                    .fullName("Infant One")
                                    .dateOfBirth(LocalDate.now().minusMonths(6))
                                    .gender(Gender.FEMALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.INFANT)
                                    .guardianPassengerIndex(0)
                                    .build(),
                            PassengerRequest.builder()
                                    .fullName("Infant Two")
                                    .dateOfBirth(LocalDate.now().minusMonths(8))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.INFANT)
                                    .guardianPassengerIndex(0) // Second infant assigned to same adult!
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat.getId()).passengerIndex(0).build()
                    ))
                    .build();

            AppException ex = assertThrows(AppException.class, () -> bookingService.createBooking(user, request));
            assertEquals(ErrorCode.INVALID_INFANT_GUARDIAN, ex.getErrorCode());
        }

        @Test
        @DisplayName("Scenario 2B: 0 Adults with 1 Infant is rejected")
        void createBooking_ZeroAdultsWithInfant_ThrowsInvalidInfantGuardian() {
            User user = createTestUser("infant_no_adult_user");

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Solo Infant")
                                    .dateOfBirth(LocalDate.now().minusMonths(6))
                                    .gender(Gender.FEMALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.INFANT)
                                    .guardianPassengerIndex(0) // Invalid guardian
                                    .build()
                    ))
                    .seatSelections(List.of())
                    .build();

            AppException ex = assertThrows(AppException.class, () -> bookingService.createBooking(user, request));
            assertEquals(ErrorCode.INVALID_INFANT_GUARDIAN, ex.getErrorCode());
        }

        @Test
        @DisplayName("Scenario 2C: Child as guardian for Infant is rejected")
        void createBooking_ChildAsGuardianForInfant_ThrowsInvalidInfantGuardian() {
            User user = createTestUser("infant_child_guardian_user");
            FlightSeat seat1 = createTestSeat(flight1, "04A");
            FlightSeat seat2 = createTestSeat(flight1, "04B");

            bookingService.holdSeats(user, HoldSeatsRequest.builder().seatIds(List.of(seat1.getId(), seat2.getId())).build());

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Adult One")
                                    .dateOfBirth(LocalDate.of(1985, 1, 1))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber("079185001122")
                                    .identityExpiry(LocalDate.of(2035, 1, 1))
                                    .build(),
                            PassengerRequest.builder()
                                    .fullName("Child Passenger")
                                    .dateOfBirth(LocalDate.now().minusYears(6))
                                    .gender(Gender.FEMALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.CHILD)
                                    .build(),
                            PassengerRequest.builder()
                                    .fullName("Infant Passenger")
                                    .dateOfBirth(LocalDate.now().minusMonths(6))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.INFANT)
                                    .guardianPassengerIndex(1) // Points to Child (index 1)!
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat1.getId()).passengerIndex(0).build(),
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat2.getId()).passengerIndex(1).build()
                    ))
                    .build();

            AppException ex = assertThrows(AppException.class, () -> bookingService.createBooking(user, request));
            assertEquals(ErrorCode.INVALID_INFANT_GUARDIAN, ex.getErrorCode());
        }

        @Test
        @DisplayName("Scenario 2D: 1 Adult with 1 Infant succeeds")
        void createBooking_ValidAdultAndInfant_Succeeds() {
            User user = createTestUser("infant_valid_user");
            FlightSeat seat = createTestSeat(flight1, "05A");

            bookingService.holdSeats(user, HoldSeatsRequest.builder().seatIds(List.of(seat.getId())).build());

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Adult Guardian")
                                    .dateOfBirth(LocalDate.of(1990, 1, 1))
                                    .gender(Gender.FEMALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber("079190009988")
                                    .identityExpiry(LocalDate.of(2035, 1, 1))
                                    .build(),
                            PassengerRequest.builder()
                                    .fullName("Baby Passenger")
                                    .dateOfBirth(LocalDate.now().minusMonths(6))
                                    .gender(Gender.FEMALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.INFANT)
                                    .guardianPassengerIndex(0) // Valid adult guardian
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat.getId()).passengerIndex(0).build()
                    ))
                    .build();

            BookingResponse response = bookingService.createBooking(user, request);
            assertNotNull(response);
            assertEquals(BookingStatus.PENDING, response.getStatus());

            // 1 Ticket for Adult (seatable), 1 Ticket for Infant (lap child with seat null)
            List<Ticket> tickets = ticketRepository.findByBookingId(response.getId());
            assertEquals(2, tickets.size());
        }
    }

    // =========================================================================
    // 3. Duplicate Active Booking on Same Flight
    // =========================================================================
    @Nested
    @DisplayName("Group 1.3.3: Duplicate Active Booking on Same Flight")
    class DuplicateActiveBookingTests {

        private void seedActiveBooking(User user, Flight flight, String docNumber, BookingStatus status) {
            String pnr = "P" + String.format("%05d", Math.abs((int) (System.nanoTime() % 100000)));
            Booking booking = bookingRepository.save(Booking.builder()
                    .pnr(pnr)
                    .user(user)
                    .tripType(TripType.ONE_WAY)
                    .baseAmount(new BigDecimal("1500000.00"))
                    .taxAmount(new BigDecimal("150000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("1650000.00"))
                    .status(status)
                    .paymentDeadline(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15))
                    .build());

            BookingLeg leg = bookingLegRepository.save(BookingLeg.builder()
                    .booking(booking)
                    .flight(flight)
                    .legOrder(1)
                    .direction(LegDirection.OUTBOUND)
                    .build());

            Passenger passenger = passengerRepository.save(Passenger.builder()
                    .user(user)
                    .fullName("Existing Passenger")
                    .dateOfBirth(LocalDate.of(1990, 5, 10))
                    .gender(Gender.MALE)
                    .nationality("VNM")
                    .build());

            passengerDocumentRepository.save(PassengerDocument.builder()
                    .passenger(passenger)
                    .identityType(DocumentType.CITIZEN_ID)
                    .identityNumber(docNumber)
                    .identityExpiry(LocalDate.of(2035, 5, 10))
                    .build());

            ticketRepository.save(Ticket.builder()
                    .booking(booking)
                    .bookingLeg(leg)
                    .passenger(passenger)
                    .passengerType(PassengerType.ADULT)
                    .price(new BigDecimal("1500000.00"))
                    .status(status == BookingStatus.COMPLETED ? TicketStatus.ISSUED : TicketStatus.PENDING)
                    .ticketNo("VN-" + pnr + "-1-01")
                    .build());
        }

        @Test
        @DisplayName("Reject new booking when passenger already has PENDING booking on same flight")
        void createBooking_PassengerAlreadyHasPendingBooking_ThrowsException() {
            User existingUser = createTestUser("prev_pending_user");
            String docNumber = "079199887766";
            seedActiveBooking(existingUser, flight1, docNumber, BookingStatus.PENDING);

            User newUser = createTestUser("new_user_pending");
            FlightSeat seat = createTestSeat(flight1, "06A");
            bookingService.holdSeats(newUser, HoldSeatsRequest.builder().seatIds(List.of(seat.getId())).build());

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Existing Passenger")
                                    .dateOfBirth(LocalDate.of(1990, 5, 10))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber(docNumber) // Same document!
                                    .identityExpiry(LocalDate.of(2035, 5, 10))
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat.getId()).passengerIndex(0).build()
                    ))
                    .build();

            AppException ex = assertThrows(AppException.class, () -> bookingService.createBooking(newUser, request));
            assertEquals(ErrorCode.PASSENGER_ALREADY_BOOKED_ON_FLIGHT, ex.getErrorCode());
        }

        @Test
        @DisplayName("Reject new booking when passenger already has COMPLETED booking on same flight")
        void createBooking_PassengerAlreadyHasCompletedBooking_ThrowsException() {
            User existingUser = createTestUser("prev_completed_user");
            String docNumber = "079199887755";
            seedActiveBooking(existingUser, flight1, docNumber, BookingStatus.COMPLETED);

            User newUser = createTestUser("new_user_completed");
            FlightSeat seat = createTestSeat(flight1, "06B");
            bookingService.holdSeats(newUser, HoldSeatsRequest.builder().seatIds(List.of(seat.getId())).build());

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Existing Passenger")
                                    .dateOfBirth(LocalDate.of(1990, 5, 10))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber(docNumber) // Same document!
                                    .identityExpiry(LocalDate.of(2035, 5, 10))
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat.getId()).passengerIndex(0).build()
                    ))
                    .build();

            AppException ex = assertThrows(AppException.class, () -> bookingService.createBooking(newUser, request));
            assertEquals(ErrorCode.PASSENGER_ALREADY_BOOKED_ON_FLIGHT, ex.getErrorCode());
        }

        @Test
        @DisplayName("Accept new booking when passenger's previous booking on same flight was CANCELLED")
        void createBooking_PassengerPreviousBookingCancelled_Succeeds() {
            User existingUser = createTestUser("prev_cancelled_user");
            String docNumber = "079199887744";
            seedActiveBooking(existingUser, flight1, docNumber, BookingStatus.CANCELLED);

            User newUser = createTestUser("new_user_cancelled");
            FlightSeat seat = createTestSeat(flight1, "06C");
            bookingService.holdSeats(newUser, HoldSeatsRequest.builder().seatIds(List.of(seat.getId())).build());

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Rebooking Passenger")
                                    .dateOfBirth(LocalDate.of(1990, 5, 10))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber(docNumber) // Same document, but previous cancelled!
                                    .identityExpiry(LocalDate.of(2035, 5, 10))
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat.getId()).passengerIndex(0).build()
                    ))
                    .build();

            BookingResponse response = bookingService.createBooking(newUser, request);
            assertNotNull(response);
            assertEquals(BookingStatus.PENDING, response.getStatus());
        }

        @Test
        @DisplayName("Accept new booking when passenger has active booking on a DIFFERENT flight")
        void createBooking_PassengerActiveBookingOnDifferentFlight_Succeeds() {
            User existingUser = createTestUser("diff_flight_user");
            String docNumber = "079199887733";
            // Active on flight1
            seedActiveBooking(existingUser, flight1, docNumber, BookingStatus.COMPLETED);

            // New booking on flight2
            User newUser = createTestUser("new_user_diff_flight");
            FlightSeat seatOnFlight2 = createTestSeat(flight2, "07A");
            bookingService.holdSeats(newUser, HoldSeatsRequest.builder().seatIds(List.of(seatOnFlight2.getId())).build());

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight2.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("Same Passenger Other Flight")
                                    .dateOfBirth(LocalDate.of(1990, 5, 10))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber(docNumber) // Same doc, but on flight2!
                                    .identityExpiry(LocalDate.of(2035, 5, 10))
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight2.getId()).flightSeatId(seatOnFlight2.getId()).passengerIndex(0).build()
                    ))
                    .build();

            BookingResponse response = bookingService.createBooking(newUser, request);
            assertNotNull(response);
            assertEquals(BookingStatus.PENDING, response.getStatus());
        }
    }

    // =========================================================================
    // 4. PNR Generation & Constraint Enforcement
    // =========================================================================
    @Nested
    @DisplayName("Group 1.3.4: PNR Generation & Unique Constraint")
    class PnrGenerationAndConstraintTests {

        @Test
        @DisplayName("Test 4A: PNR format matches 6 chars and retry loop executes upon collision")
        void createBooking_PnrFormatAndRetryLogic_Succeeds() {
            User user = createTestUser("pnr_retry_user");
            FlightSeat seat = createTestSeat(flight1, "08A");
            bookingService.holdSeats(user, HoldSeatsRequest.builder().seatIds(List.of(seat.getId())).build());

            // Simulate collision on first generation attempt:
            // existsByPnr returns true once (forcing retry), then false on second attempt
            when(bookingRepository.existsByPnr(anyString()))
                    .thenReturn(true)
                    .thenReturn(false);

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .tripType(TripType.ONE_WAY)
                    .flightIds(List.of(flight1.getId()))
                    .contactInfo(createDefaultContactInfo())
                    .passengers(List.of(
                            PassengerRequest.builder()
                                    .fullName("PNR Test Passenger")
                                    .dateOfBirth(LocalDate.of(1993, 7, 7))
                                    .gender(Gender.MALE)
                                    .nationality("VNM")
                                    .passengerType(PassengerType.ADULT)
                                    .identityType(DocumentType.CITIZEN_ID)
                                    .identityNumber("079193007788")
                                    .identityExpiry(LocalDate.of(2035, 7, 7))
                                    .build()
                    ))
                    .seatSelections(List.of(
                            SeatSelectionRequest.builder().flightId(flight1.getId()).flightSeatId(seat.getId()).passengerIndex(0).build()
                    ))
                    .build();

            BookingResponse response = bookingService.createBooking(user, request);
            assertNotNull(response);
            assertNotNull(response.getPnr());

            // Verify PNR format: exactly 6 uppercase alphanumeric characters
            assertTrue(response.getPnr().matches("^[A-Z0-9]{6}$"), "PNR must be 6 uppercase alphanumeric characters");

            // Verify that existsByPnr was invoked at least 2 times (proving retry loop was exercised)
            verify(bookingRepository, atLeast(2)).existsByPnr(anyString());
        }

        @Test
        @DisplayName("Test 4B: PostgreSQL database unique constraint on booking(pnr) rejects duplicate PNR")
        void createBooking_PostgresUniqueConstraintOnPnr_ThrowsDataIntegrityViolation() {
            User user1 = createTestUser("pnr_db1_user");
            User user2 = createTestUser("pnr_db2_user");

            String duplicatePnr = "DUP999";

            Booking booking1 = Booking.builder()
                    .pnr(duplicatePnr)
                    .user(user1)
                    .tripType(TripType.ONE_WAY)
                    .baseAmount(new BigDecimal("1500000.00"))
                    .taxAmount(new BigDecimal("150000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("1650000.00"))
                    .status(BookingStatus.PENDING)
                    .paymentDeadline(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15))
                    .build();

            // Insert first booking and flush directly to PostgreSQL
            bookingRepository.saveAndFlush(booking1);

            Booking booking2 = Booking.builder()
                    .pnr(duplicatePnr) // Exact duplicate PNR!
                    .user(user2)
                    .tripType(TripType.ONE_WAY)
                    .baseAmount(new BigDecimal("1500000.00"))
                    .taxAmount(new BigDecimal("150000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("1650000.00"))
                    .status(BookingStatus.PENDING)
                    .paymentDeadline(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15))
                    .build();

            // Attempting to saveAndFlush the second entity with the same PNR must be rejected by PostgreSQL
            assertThrows(DataIntegrityViolationException.class, () -> bookingRepository.saveAndFlush(booking2));
        }
    }
}
